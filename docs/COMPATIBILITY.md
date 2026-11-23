# Controller compatibility

EmitLoom selects a direct Bluetooth protocol from a recognizable model name or known manufacturer advertisement. Several families use the same UART GATT service but require different command formats. The app verifies the writable characteristic, subscribes to notifications, and reads controller status before enabling controls.

| Profile | Initial target models | Protocol and controls |
| --- | --- | --- |
| A0 | **SP621E** (primary), SP611E, SP617E, SP620E | `A0` command header and fragmented status notifications. Addressable effects. SP617E exposes a white channel. |
| SP63xE/SP64xE | SP630E–SP639E, SP63AE, SP641E–SP649E, SP64AE | Six-byte `53` header, followed by the payload. Color, white, and effects depend on the reported output configuration. |
| Compact | SP613E, SP614E, SP623E, SP624E | Command and payload length followed by the payload. PWM effects. SP614E and SP624E expose white brightness. |
| Dual output | SP601E | `AA` command header with per-output fields. Independent output controls and recall of preexisting scene slots. |

Commands use GATT service `FFE0` and characteristic `FFE1`. The SP63xE/SP64xE profile also accepts service `E0FF`. EmitLoom serializes writes, coalesces pending slider updates, and rejects incomplete or malformed status frames. Notifications with encrypted payloads are outside the current decoder's scope.

## Current scope

- One connected controller at a time. Saved controllers and looks remain available between sessions.
- Up to five one-shot power timers run on the phone and depend on the active connection and process. Controller-stored schedules are outside the current scope.
- The color-sequence editor sends commands from the phone to one connected RGB output. Controller-stored DIY effects and pixel-layout editors require additional model-specific protocol evidence.
- Multi-controller groups, Wi-Fi and mesh provisioning, network control, an in-app music player, and firmware updates remain outside the implemented scope.
- The compatibility list comes from protocol evidence and packet tests. Firmware variants, Bluetooth behavior, and physical LED output require hands-on verification.
- The temporary demo uses a single RGB output from the SP621E profile. The software strip preview illustrates the active light state and representative effects. Physical effect timing, pixel count, audio response, and BLE behavior require hardware verification.

## BanlanX feature progress on SP621E

| Area | EmitLoom status |
| --- | --- |
| Core lighting | RGB wheel, brightness, power, dynamic effects, speed, effect length, effect cycling, and RGB channel order are implemented. Real-device verification is pending. |
| Sound | Both **Phone microphone** and **Now Playing** pulse RGB brightness. Now Playing uses Android 10+ playback capture for compatible apps. Mapping audio into the SP621E's firmware music presets requires protocol evidence and hardware verification. |
| Saved settings and timing | EmitLoom saves looks and color sequences on the phone and supports up to five one-shot power timers per connection. BanlanX also supports controller timing events and device groups. |
| Preview | EmitLoom provides an interactive 24-LED software sample and a temporary no-hardware demo. |

[Sperll SP621E product guide](https://document.sperll.com/homepage/upload/Doc/SP621E%20Mini%20Bluetooth%20Music%20Pixel%20LED%20Controller_CN_EN.pdf)

### Next verification

1. On a physical SP621E, check that phone microphone and Now Playing both react, that stopping restores brightness, and that Android capture revocation ends the session. Test playback with a compatible media app and with playback paused.
2. Queue separate on and off timers, cancel one, then disconnect and reconnect to confirm the queue clears. On SP601E, schedule each output separately.
3. Record verified controller behavior before adding vendor-specific audio streaming, controller-stored timing, or controller groups. The extracted BanlanX Android component confirms a foreground microphone recorder. It does not establish the SP621E packet format for playback audio or timing events.

## Hardware verification

Record the Android version, controller model and firmware, and output configuration. Test scan, connect, status read, power, brightness, and color or white as applicable. Verify that rapid slider changes finish on the selected value. Run a pattern, reconnect, and verify that the displayed state matches the lights. For RGB, play a sequence once and in a loop, then switch outputs or background the app and confirm playback stops. If the device reports a power transition, apply a setting, reconnect, and check the reported configuration and physical behavior. Test both outputs on SP601E.

## Protocol references

Public UniLED protocol observations informed the interoperability work. EmitLoom implements its own Kotlin encoding, decoding, Bluetooth transport, and UI. [UniLED Bluetooth protocol references](https://github.com/monty68/uniled/tree/main/custom_components/uniled/lib/ble)
