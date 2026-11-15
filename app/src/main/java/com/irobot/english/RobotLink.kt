package com.irobot.english

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Talks to the robot the same way the original app does: the phone broadcasts
 * a Bluetooth LE advertisement (manufacturer id 6) and keeps repeating the most
 * recent packet about every 100 ms. The robot never answers, so "connected"
 * means "pairing packet broadcast for 3 seconds".
 */
@SuppressLint("MissingPermission")
class RobotLink(context: Context, private val listener: Listener) {

    interface Listener {
        fun onStateChanged(state: State)
        fun onError(message: String)
    }

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var set: AdvertisingSet? = null
    private val queue = ArrayDeque<ByteArray>()
    private var updating = false
    private var firstData = true
    private var releaseTask: Runnable? = null

    var state = State.DISCONNECTED
        private set(value) {
            field = value
            listener.onStateChanged(value)
        }

    /** Program ("teach") mode adds 2 to d3 on every remote-control packet. */
    var programMode = false
        private set

    val bluetoothOn: Boolean get() = adapter?.isEnabled == true

    /** Same per-install random id the original app uses; the robot pairs to it. */
    private val appId: ByteArray by lazy {
        val prefs = appContext.getSharedPreferences("robot", Context.MODE_PRIVATE)
        if (!prefs.contains("uid1")) {
            prefs.edit().apply {
                for (i in 1..4) putInt("uid$i", Random.nextInt(255) + 1)
            }.apply()
        }
        ByteArray(4) { prefs.getInt("uid${it + 1}", 1).toByte() }
    }

    fun connect() {
        val a = adapter
        if (a == null) { listener.onError("This phone has no Bluetooth."); return }
        if (!a.isEnabled) { listener.onError("Turn Bluetooth on first."); return }
        if (!a.isLeExtendedAdvertisingSupported && !a.isMultipleAdvertisementSupported) {
            listener.onError("This phone can't broadcast Bluetooth LE; it can't drive the robot.")
            return
        }
        stopSet()
        val adv = a.bluetoothLeAdvertiser
        if (adv == null) { listener.onError("Bluetooth broadcast isn't available right now."); return }
        advertiser = adv
        state = State.CONNECTING
        firstData = true
        queue.clear()
        updating = false
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(true)
            .setConnectable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_LOW)        // 160 = 100 ms
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)   // +1 dBm
            .build()
        val initial = AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_ID, "Initial Data".toByteArray())
            .build()
        try {
            adv.startAdvertisingSet(params, initial, null, null, null, callback)
        } catch (e: SecurityException) {
            state = State.DISCONNECTED
            listener.onError("Bluetooth permission is needed.")
            return
        }
        enqueue(RobotPacket.connect(appId))
    }

    fun disconnect() {
        cancelRelease()
        stopSet()
        programMode = false
        state = State.DISCONNECTED
    }

    /** Sends d3/d4 and keeps it on air until something else is sent. */
    fun hold(d3: Int, d4: Int) {
        if (state != State.CONNECTED) return
        cancelRelease()
        send(d3, d4)
    }

    /** Sends a command for [ms] milliseconds, then the idle packet (like the original voice screen). */
    fun pulse(d3: Int, d4: Int, ms: Long = 500) {
        if (state != State.CONNECTED) return
        cancelRelease()
        send(d3, d4)
        val r = Runnable { release() }
        releaseTask = r
        main.postDelayed(r, ms)
    }

    /** Remote-control press: same as [hold] but with the program-mode offset. */
    fun press(cmd: RobotCommand) {
        if (programMode && cmd.id == "mute") return
        hold(cmd.d3 + if (programMode) 2 else 0, cmd.d4)
    }

    /** Remote-control tap: the command for half a second, then idle. */
    fun tap(cmd: RobotCommand) {
        if (programMode && cmd.id == "mute") return
        pulse(cmd.d3 + if (programMode) 2 else 0, cmd.d4)
    }

    /** Back to idle: (0,0), or (2,0) while program mode is on. */
    fun release() {
        cancelRelease()
        if (state != State.CONNECTED) return
        send(if (programMode) 2 else 0, 0)
    }

    fun setProgramMode(on: Boolean) {
        programMode = on
        if (state == State.CONNECTED) send(if (on) 2 else 0, 0)
    }

    private fun send(d3: Int, d4: Int) = enqueue(RobotPacket.data(appId, 0, 0, 0, d3, d4))

    private fun cancelRelease() {
        releaseTask?.let { main.removeCallbacks(it) }
        releaseTask = null
    }

    private fun enqueue(payload: ByteArray) {
        // Only the newest packet matters for the robot; drop stale ones so taps feel instant.
        if (state == State.CONNECTED) queue.clear()
        queue.addLast(payload)
        next()
    }

    private fun next() {
        val s = set ?: return
        if (updating || queue.isEmpty()) return
        updating = true
        val data = AdvertiseData.Builder().addManufacturerData(MANUFACTURER_ID, queue.removeFirst()).build()
        try {
            s.setAdvertisingData(data)
        } catch (e: SecurityException) {
            updating = false
            listener.onError("Bluetooth permission is needed.")
        }
    }

    private fun stopSet() {
        try {
            advertiser?.stopAdvertisingSet(callback)
        } catch (_: SecurityException) {
        }
        set = null
        queue.clear()
        updating = false
    }

    private val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            main.post {
                if (status == ADVERTISE_SUCCESS && advertisingSet != null) {
                    set = advertisingSet
                    next()
                } else {
                    Log.w(TAG, "start failed $status")
                    state = State.DISCONNECTED
                    listener.onError("Couldn't start Bluetooth broadcast (code $status).")
                }
            }
        }

        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            main.post {
                updating = false
                if (firstData) {
                    if (status == ADVERTISE_SUCCESS) {
                        firstData = false
                        // The original app waits 3 s of pairing broadcast, then sends idle.
                        main.postDelayed({
                            if (state == State.CONNECTING) {
                                state = State.CONNECTED
                                send(0, 0)
                            }
                        }, 3000)
                    } else {
                        state = State.DISCONNECTED
                        listener.onError("Pairing broadcast failed (code $status).")
                    }
                } else if (status != ADVERTISE_SUCCESS) {
                    listener.onError("Send failed (code $status).")
                }
                next()
            }
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            main.post {
                set = null
                queue.clear()
                if (state != State.DISCONNECTED) state = State.DISCONNECTED
            }
        }
    }

    companion object {
        private const val TAG = "RobotLink"
        const val MANUFACTURER_ID = 6
    }
}
