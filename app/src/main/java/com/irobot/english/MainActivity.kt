package com.irobot.english

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.irobot.english.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), RobotLink.Listener {

    private lateinit var b: ActivityMainBinding
    private lateinit var link: RobotLink
    private lateinit var speaker: Speaker
    private lateinit var voice: VoiceInput
    private val main = Handler(Looper.getMainLooper())
    private var model = RobotModel.ROBOT_DOG
    private var handsFree = false
    private var waitingToListen = false

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val btPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        else emptyArray()

    private val askBt = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) connectNow()
        else toast("Bluetooth permission is needed to talk to the robot.")
    }

    private val enableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (link.bluetoothOn) link.connect() else toast("Bluetooth is still off.")
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening() else toast("Microphone permission is needed for voice commands.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        link = RobotLink(this, this)
        speaker = Speaker(this) { onSpeechFinished() }
        voice = VoiceInput(
            this,
            onListening = { on -> setMicState(on) },
            onPartial = { b.heardText.text = "“$it”" },
            onResult = { handleHeard(it) },
            onError = { msg ->
                b.heardText.text = msg
                if (handsFree) scheduleListen(800)
            },
        )

        model = runCatching { RobotModel.valueOf(prefs.getString("model", null) ?: "") }
            .getOrDefault(RobotModel.ROBOT_DOG)
        b.modelGroup.check(if (model == RobotModel.ROBOT_DOG) R.id.modelDog else R.id.modelWolf)
        b.modelGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            model = if (id == R.id.modelWolf) RobotModel.WOLF_WARRIOR else RobotModel.ROBOT_DOG
            prefs.edit().putString("model", model.name).apply()
            buildActions()
        }

        b.connectButton.setOnClickListener {
            if (link.state == RobotLink.State.DISCONNECTED) startConnect() else link.disconnect()
        }

        b.micButton.setOnClickListener {
            if (voice.listening) { voice.stop(); return@setOnClickListener }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) startListening()
            else askMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        b.handsFreeSwitch.setOnCheckedChangeListener { _, on ->
            handsFree = on
            if (!on) { waitingToListen = false; main.removeCallbacksAndMessages(null) }
        }
        b.repliesSwitch.isChecked = prefs.getBoolean("replies", true)
        speaker.enabled = b.repliesSwitch.isChecked
        b.repliesSwitch.setOnCheckedChangeListener { _, on ->
            speaker.enabled = on
            prefs.edit().putBoolean("replies", on).apply()
        }
        b.programSwitch.setOnCheckedChangeListener { _, on ->
            link.setProgramMode(on)
            if (on) say("Programming mode on.") else say("Programming mode off.")
        }

        holdButton(b.btnForward, Commands.FORWARD)
        holdButton(b.btnBack, Commands.BACKWARD)
        holdButton(b.btnLeft, Commands.LEFT)
        holdButton(b.btnRight, Commands.RIGHT)
        b.btnStop.setOnClickListener { run(Commands.STOP, speak = true) }

        buildActions()
        onStateChanged(link.state)
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        voice.destroy()
        speaker.shutdown()
        link.disconnect()
    }

    // ---------- Bluetooth ----------

    private fun startConnect() {
        val missing = btPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) askBt.launch(missing.toTypedArray()) else connectNow()
    }

    @SuppressLint("MissingPermission")
    private fun connectNow() {
        if (!link.bluetoothOn) {
            try {
                enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: SecurityException) {
                toast("Turn Bluetooth on in settings.")
            }
            return
        }
        link.connect()
    }

    override fun onStateChanged(state: RobotLink.State) {
        when (state) {
            RobotLink.State.DISCONNECTED -> {
                b.statusText.text = "Not connected — turn the robot on, then tap Connect"
                b.statusText.setTextColor(ContextCompat.getColor(this, R.color.text_soft))
                b.connectButton.text = "Connect"
                b.programSwitch.isChecked = false
            }
            RobotLink.State.CONNECTING -> {
                b.statusText.text = "Pairing… keep the phone near the robot"
                b.statusText.setTextColor(ContextCompat.getColor(this, R.color.warn))
                b.connectButton.text = "Cancel"
            }
            RobotLink.State.CONNECTED -> {
                b.statusText.text = "● Connected to ${model.label}"
                b.statusText.setTextColor(ContextCompat.getColor(this, R.color.ok))
                b.connectButton.text = "Disconnect"
                say("Connected. Ready for your commands.")
            }
        }
    }

    override fun onError(message: String) = toast(message)

    // ---------- Commands ----------

    private fun run(cmd: RobotCommand, speak: Boolean, heard: String? = null) {
        if (link.state != RobotLink.State.CONNECTED) {
            b.lastCommandText.text = "Not connected — tap Connect first."
            if (speak) say("I'm not connected to the robot yet.")
            return
        }
        link.tap(cmd)
        b.lastCommandText.text = "Sent: ${cmd.label}" + (heard?.let { "  (heard “$it”)" } ?: "")
        if (speak) say(cmd.reply)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun holdButton(button: View, cmd: RobotCommand) {
        button.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (link.state == RobotLink.State.CONNECTED) {
                        link.press(cmd)
                        b.lastCommandText.text = "Holding: ${cmd.label}"
                    } else {
                        b.lastCommandText.text = "Not connected — tap Connect first."
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    link.release()
                    if (link.state == RobotLink.State.CONNECTED) b.lastCommandText.text = "Released: ${cmd.label}"
                }
            }
            true
        }
    }

    private fun buildActions() {
        val grid = b.actionGrid
        grid.removeAllViews()
        val pad = (4 * resources.displayMetrics.density).toInt()
        for (cmd in Commands.actionsFor(model)) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btn.text = cmd.label
            btn.isAllCaps = false
            btn.minHeight = (56 * resources.displayMetrics.density).toInt()
            if (cmd.id == "mute") {
                btn.setTextColor(ContextCompat.getColor(this, R.color.danger))
                btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger))
            }
            btn.setOnClickListener { run(cmd, speak = true) }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = 0
                setMargins(pad, pad, pad, pad)
            }
            grid.addView(btn, lp)
        }
        b.commandList.text = buildString {
            append("Things you can say:\n")
            Commands.forModel(model).forEach { c ->
                append("• ").append(c.label).append(" — “").append(c.phrases.take(3).joinToString("”, “")).append("”\n")
            }
            append("\nThe robot's own voice is built into the robot. Use “Mute robot” to silence it — this app speaks English for it.")
        }
    }

    // ---------- Voice ----------

    private fun startListening() {
        waitingToListen = false
        if (!voice.available) {
            toast("No speech recognizer on this phone. Install the Google app.")
            return
        }
        b.heardText.text = "Listening…"
        voice.start()
    }

    private fun handleHeard(candidates: List<String>) {
        if (candidates.isEmpty()) {
            b.heardText.text = "I didn't catch that."
            if (handsFree) scheduleListen(800)
            return
        }
        val hit = Commands.matchAny(candidates, model)
        if (hit == null) {
            b.heardText.text = "“${candidates.first()}”"
            b.lastCommandText.text = "Not a command I know."
            say("Sorry, I don't know that command.")
            return
        }
        val (cmd, text) = hit
        b.heardText.text = "“$text”"
        run(cmd, speak = true, heard = text)
    }

    private fun setMicState(on: Boolean) {
        b.micButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (on) R.color.danger else R.color.brand)
        )
    }

    /** Replies go through the phone speaker; only re-open the mic after they finish so we don't hear ourselves. */
    private fun say(text: String) {
        if (voice.listening) voice.stop()
        speaker.say(text)
    }

    private fun onSpeechFinished() {
        if (handsFree && !voice.listening) scheduleListen(400)
    }

    private fun scheduleListen(delay: Long) {
        if (waitingToListen) return
        waitingToListen = true
        main.postDelayed({ if (handsFree && waitingToListen) startListening() }, delay)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
