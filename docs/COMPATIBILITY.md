# Controller compatibility

EmitLoom selects a direct Bluetooth protocol from a recognizable model name or known manufacturer advertisement. Several families use the same UART GATT service but require different command formats. The app verifies the writable characteristic, subscribes to notifications, and reads controller status before enabling controls.

| Profile | Initial target models | Protocol and controls |
| --- | --- | --- |
| SP63xE/SP64xE | SP630E, SP631E–SP639E, SP63AE, SP641E–SP649E, SP64AE | Six-byte `53` header, followed by the payload. Color, white, and effects depend on the reported output configuration. |
| A0 | SP611E, SP617E, SP620E, SP621E | `A0` command header and fragmented status notifications. Addressable effects. SP617E exposes a white channel. |
| Compact | SP613E, SP614E, SP623E, SP624E | Command and payload length followed by the payload. PWM effects. SP614E and SP624E expose white brightness. |
| Dual output | SP601E | `AA` command header with per-output fields. Independent output controls and recall of preexisting scene slots. |

Commands use GATT service `FFE0` and characteristic `FFE1`. The SP63xE/SP64xE profile also accepts service `E0FF`. EmitLoom serializes writes, coalesces pending slider updates, and rejects incomplete or malformed status frames. Notifications with encrypted payloads are outside the current decoder's scope.

## Current scope

- One connected controller at a time. Saved controllers and looks remain available between sessions.
- The power timer runs on the phone and depends on the active connection and process. It does not program a controller schedule.
- Device-specific DIY editors, multi-controller groups, Wi-Fi and mesh provisioning, network control, local music-file playback, and firmware updates are outside this release.
- The compatibility list comes from protocol evidence and packet tests. Firmware variants, Bluetooth behavior, and physical LED output require hands-on verification.

## Hardware verification

Record the Android version, controller model and firmware, and output configuration. Test scan, connect, status read, power, brightness, and color or white as applicable. Verify that rapid slider changes finish on the selected value. Run a pattern, reconnect, and verify that the displayed state matches the lights. Test the remaining controls shown for that model, including both outputs on SP601E.

## Protocol references

Public UniLED protocol observations informed the interoperability work. EmitLoom implements its own Kotlin encoding, decoding, Bluetooth transport, and UI. [UniLED Bluetooth protocol references](https://github.com/monty68/uniled/tree/main/custom_components/uniled/lib/ble)
