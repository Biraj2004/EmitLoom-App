# Privacy notice

This notice covers the EmitLoom Android app.

## Data and permissions

| Data or permission | How EmitLoom uses it |
| --- | --- |
| Bluetooth and Nearby devices | Discover compatible controllers and exchange lighting commands and status with the selected controller over Bluetooth Low Energy. |
| Location on Android 11 and earlier | Enable Bluetooth scanning under Android's older permission model. EmitLoom does not request coordinates or retain a location history. |
| Microphone | When you start phone microphone sync and grant permission, EmitLoom estimates volume from microphone samples in memory. It sends lighting commands over Bluetooth and discards the samples. |
| Playback capture | On Android 10 or newer, **Now Playing** uses Android's capture approval and audio permission to estimate volume from compatible apps' media and game playback. EmitLoom processes audio in memory, discards the samples, and sends brightness commands over Bluetooth. The system consent dialog may mention sharing the screen. EmitLoom captures audio only. |
| Saved settings | Controller addresses and display names, saved looks, color sequences, and the list/grid choice are stored in the app's local preferences. |

The app does not request Internet access or use analytics or an account service. App preferences are excluded from the configured Android backup and device-transfer rules.

Use **Stop sync** to end a capture session. Sync also ends when you leave the app or disconnect the controller. Android can revoke playback capture at any time.

## Manage your data

- Use **Forget controller** to remove the saved controller and its associated looks.
- Delete individual looks and sequences from **Looks**.
- Clear EmitLoom's app storage in Android settings or uninstall the app to remove its local data.
- Change Bluetooth, location, and microphone access in Android app permissions.

Public GitHub issues and pull requests are separate from the Android app. Redact controller addresses, personal information, credentials, and unrelated log entries when sharing a report.
