# Robot English

An English controller app for the robot dog / "Wolf Warrior" robot that ships
with the Chinese **757 R/C** app (`com.frank.dog`).

- **English voice commands.** Tap the mic and say "sit down", "dance", "turn left", "stop"…
  It uses the phone's own speech recognizer, so English works well. Turn on *Keep listening* for hands-free use.
- **English replies.** The phone answers in English ("Sitting down.") through text-to-speech.
- **Mute robot.** Sends the robot's own mute command, so its built-in Chinese voice stays quiet.
- **Hold-to-move pad** (forward / back / left / right / stop) and a tile for every action:
  sit, lie down, act cute, hello, handstand, push-ups, dance, kung fu, swim, patrol, shoot, song, study, AI, light…
- **Programming mode**, the same as the original app's "Programming" toggle.

## Install

1. Download `app-release.apk`. It's in the **Build APK** workflow artifacts on GitHub, or ask for a build.
2. On the Android phone, open it and allow "install unknown apps" when asked.
3. Turn the robot on, open **Robot English**, pick your model (Robot Dog or Wolf Warrior) and tap **Connect**.
   Keep the phone close to the robot while it pairs (about 3 s).

Needs Android 8.0+ with Bluetooth LE. Voice needs the Google app (or another speech service) and English installed.

## How it talks to the robot

The robot does not use a normal Bluetooth connection. The phone **broadcasts** a BLE advertisement
(manufacturer ID `0x0006`, legacy and non-connectable, every 100 ms) with a 23-byte payload.
The robot only listens; it never replies.

The payload is `0x00` followed by a 22-byte frame built by `RobotPacket.create`:

| bytes | meaning |
|---|---|
| 0–1 | `AA 70` preamble |
| 2–5 | address `C3 C2 C1 C0` |
| 8 | `D0` pair, `E0` command |
| 9 | model id `54` |
| 10–13 | random app id per install (the robot pairs to it) |
| 14–18 | d0…d4 |
| 19 | checksum |
| 20–21 | CRC-16 |

The frame is then whitened, bit-reversed and XORed with the channel-39 table.
`RobotPacketTest` checks it byte for byte against the original app's encoder.

Commands are `(d3, d4)`. After each command the app sends the idle packet `(0, 0)`,
or `(2, 0)` in programming mode, because the radio keeps repeating the last packet.

| Command | d3, d4 |
|---|---|
| Stop / Forward / Back / Left / Right | 0,1 / 0,2 / 0,3 / 0,4 / 0,5 |
| Sit / Lie down / Act cute / Pee (dog) / March (wolf) | 0,6 / 0,7 / 0,8 / 0,9 / 0,9 |
| Hello (dog) / Shoot (wolf) | 0,10 |
| Handstand / Push-ups / Dance / Kung fu / Swim | 0,11 / 0,12 / 0,13 / 0,14 / 0,15 |
| Blessing (dog) / Charge (wolf) / Patrol | 0,16 / 0,16 / 0,17 |
| Song / Study / Shoot (dog) or Salute (wolf) / AI / Mute | 1,0 / 16,0 / 8,0 / 32,0 / 64,0 |
| Light (dog) or Hello (wolf) | 128,0 |

## The robot's own voice

The Chinese voice clips are stored **on the robot**, not in the app, so no app can change them.
This app mutes the robot and speaks English from the phone instead. Replacing the clips
inside the robot would need a hardware change: either reflash its sound memory chip, or fit a
small English sound module (ESP32 + DFPlayer) that listens for these same broadcasts.

## Build

```
./gradlew :app:testDebugUnitTest :app:assembleRelease
```
APKs are signed with `keystore/sideload.jks`, so new builds install over old ones.
That key is for personal sideloading only, not for the Play Store.
