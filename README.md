# EmitLoom

EmitLoom is a native Android app for controlling compatible LED lights over Bluetooth Low Energy. It uses Kotlin and Jetpack Compose, runs on Android 8.0 or newer, and operates offline.

## What you can do

- Scan for nearby controllers, reconnect to saved devices, and rename them locally.
- Set power, solid RGB color and brightness. Supported outputs also expose white brightness and color temperature.
- Browse controller effects, adjust speed, and use the hardware microphone on models that provide one. Phone microphone sync pulses RGB brightness while the app is open.
- Save and recall looks on your phone. SP601E also exposes existing controller scene slots and separate output controls.
- Set a one-shot power timer while connected. Some models expose additional output and power-restore settings.

The initial compatibility targets are SP601E, SP611E, SP617E, SP620E, SP621E, SP613E, SP614E, SP623E, SP624E, and the direct-BLE SP63xE/SP64xE family. Firmware and output configuration can affect available controls. See the [compatibility notes](docs/COMPATIBILITY.md) for verification status and current scope.

## Install and connect

1. Install the signed APK on an Android phone. A local release build exports `apk-export/emitloom-v1.0.0-release.apk`.
2. Power the controller and keep it near the phone. Disconnect any other app using the controller.
3. Open EmitLoom, tap **Find lights**, and grant the Android Bluetooth permission. Android 11 and earlier also require Location for BLE scanning.
4. Select the controller. EmitLoom reads its current state before opening the controls.

## Build

Install JDK 21 and Android SDK Platform 35 with Build Tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or open the project in Android Studio to configure the Android SDK.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On macOS or Linux use `./gradlew`. The debug APK uses Android's local debug key. For release signing and publication, see the [release guide](docs/RELEASING.md).

## Data and permissions

EmitLoom stores controller addresses, their display names, and saved looks on the phone. Bluetooth is used to discover and control devices. For phone microphone sync, audio is processed in memory to calculate volume and discarded. EmitLoom has no Internet permission, account service, or analytics. You can remove individual controllers and their looks with **Forget controller**, or remove all local data in Android app settings.

## License

EmitLoom is licensed under the [GNU Affero General Public License, version 3 only](LICENSE). [Third-party notices](app/src/main/assets/licenses/NOTICE.txt) cover bundled dependencies. Hardware and vendor names identify compatibility. EmitLoom is an independent project with no vendor affiliation.
