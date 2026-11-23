<p align="center"><img src="docs/assets/emitloom-logo.svg" alt="EmitLoom logo" width="112" height="112"></p>
<h1 align="center">EmitLoom</h1>
<p align="center">Offline Bluetooth LED control for Android</p>
<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-237A57">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-6344AB">
  <a href="LICENSE"><img alt="AGPL-3.0-only license" src="https://img.shields.io/badge/License-AGPL--3.0--only-34699B"></a>
</p>

EmitLoom is a native Android app for controlling compatible LED lights over Bluetooth Low Energy. It uses Kotlin and Jetpack Compose, runs on Android 8.0 or newer, and operates offline.

## What you can do

- Scan for nearby controllers, reconnect to saved devices, and rename them locally. Switch between list and grid views, with the choice saved on the phone.
- Drag the color wheel or tap a preset to pick RGB colors. Adjust brightness independently and see the result on a 24-LED sample strip. Supported outputs also expose white brightness and warmth.
- Browse controller effects, adjust speed, and use the hardware microphone on models that provide one. Phone microphone sync pulses RGB brightness while the app is open.
- Save and recall looks on your phone. SP601E also exposes existing controller scene slots and separate output controls.
- Create color sequences with individual hold times and optional looping. Play them in the app on a connected RGB output.
- Set a one-shot power timer while connected. Some models expose transition effects, output configuration, and power-restore settings.

[Using the controls](docs/WORKFLOWS.md) · [Build and publish](docs/RELEASING.md)

## Supported controller models

**SP621E** is the primary target and the model used by the temporary demo. EmitLoom also implements direct Bluetooth profiles for the models below.

| Profile | Models | Available controls vary by model |
| --- | --- | --- |
| A0 | **SP621E**, SP611E, SP617E, SP620E | Addressable RGB effects. SP617E also exposes white brightness. |
| Compact | SP613E, SP614E, SP623E, SP624E | PWM effects. SP614E and SP624E also expose white brightness. |
| Dual output | SP601E | Separate output controls and recall of existing controller scenes. |
| SP63xE/SP64xE | SP630E–SP639E, SP63AE, SP641E–SP649E, SP64AE | RGB, white, and effects depend on the reported output configuration. |

This list reflects protocol support in the app. Hands-on verification remains pending, and firmware and strip configuration can affect which controls are available. [Compatibility and hardware checks](docs/COMPATIBILITY.md)

## Install and connect

1. Download the signed APK from the [latest GitHub release](https://github.com/Biraj2004/EmitLoom-App/releases/latest) and install it on an Android phone.
2. Power the controller and keep it near the phone. Disconnect any other app using the controller.
3. Open EmitLoom, tap **Find lights**, and grant the Android Bluetooth permission. Android 11 and earlier also require Location for BLE scanning.
4. Select the controller. EmitLoom reads its current state before opening the controls.

## Build

Install JDK 21 and Android SDK Platform 35 with Build Tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or open the project in Android Studio to configure the Android SDK.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On macOS or Linux use `./gradlew`. The debug APK uses Android's local debug key. For release signing and publication, see the [release guide](docs/RELEASING.md).

To try the SP621E controls without nearby hardware, see the [temporary demo build](docs/WORKFLOWS.md#try-a-demo-controller).

## Data and permissions

EmitLoom stores controller addresses, display names, looks, and color sequences on the phone. Bluetooth is used to discover and control devices. Phone microphone sync processes audio in memory to calculate volume and discards the samples. EmitLoom has no Internet permission, account service, or analytics. Use **Forget controller** to remove a controller and its saved looks, or clear the app's storage in Android settings to remove all local data.

[Privacy notice](PRIVACY.md)

## Project participation

[Contributing](CONTRIBUTING.md) · [Code of conduct](CODE_OF_CONDUCT.md) · [Security policy](SECURITY.md)

## License

EmitLoom is licensed under the [GNU Affero General Public License, version 3 only](LICENSE). [Third-party notices](app/src/main/assets/licenses/NOTICE.txt) cover bundled dependencies. Hardware and vendor names identify compatibility. EmitLoom is an independent project with no vendor affiliation.

[Legal and licensing notes](LEGAL.md)
