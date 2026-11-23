# Using EmitLoom

| Screen | What you can do |
| --- | --- |
| Your lights | Find compatible nearby controllers, reconnect to saved devices, and see connection status in a list or grid. Your layout choice is saved on the phone. |
| Color | Tap or drag on the RGB wheel. The edge gives vivid colors and the center blends toward white. Use Brightness to dim the output independently. Presets and optional hex entry are available. |
| Effects | Choose a preset and adjust the controls exposed for that model and effect. |
| Sound | Use supported controller microphones or use your phone microphone to pulse RGB brightness. |
| Looks | Save and recall settings for a controller, create RGB sequences, and recall existing scene slots on the dual-output profile. |
| Controller settings | Edit the local display name, read device status, and adjust supported output settings. Power-transition controls appear when the controller reports valid settings. |
| Power timer | Schedule one on or off action for the selected output while the phone remains connected. |

The 24-LED **Strip preview** stays visible above the controls. It reflects power, color, brightness, supported white warmth, color sequences, and phone microphone levels. Effect animations respond to available settings such as speed, length, direction, and play or pause. Patterns and controller microphone activity are software approximations. The displayed count is the sample size.

## Try a demo controller

Build the opt-in debug APK for hardware-free testing. Use the intended release version and Android version code, as in this example for `v1.0.2`.

```powershell
.\gradlew.bat :app:assembleDebug '-PemitloomDemo=true' '-PemitloomVersionName=1.0.2' '-PemitloomVersionCode=3'
```

After a successful build, install `apk-export/emitloom-v1.0.2-demo.apk` and open **EmitLoom Demo**. The version in the filename comes from the APK metadata. Demo exports stay local in the Git-ignored `apk-export` directory. The demo package is `com.emitloom.app.demo`, so it can sit alongside the regular app. Tap **Try demo controller** on **Your lights** to open the simulated SP621E with one addressable RGB output. Demo setup works without Bluetooth permissions and sends no Bluetooth commands.

1. Tap and drag the wheel, dim the brightness, and switch power off and back on. Compare the resulting colors and brightness on the sample strip.
2. Try Rainbow, Rainbow Meteor, and Rainbow Stars in **Effects**. Adjust **Speed** and **Effect length**, then try **Cycle effects**. Under **Sound**, tap **Start sync**, grant microphone permission, and make a sound to see RGB brightness pulse.
3. Save and recall a look. Create and play a color sequence, and check that the strip follows its steps. Set a power timer to see a scheduled change.
4. Open **Controller settings**, try **Read controller status** and check the RGB channel-order options. Use **End demo** to discard demo looks, sequences, settings, and timers. The demo button is available in the opt-in debug APK only.

When the real SP621E is ready, end the demo, disconnect it from BanlanX, then use **Find lights**. Compare red, green, and blue, low and high brightness, and an effect at two speeds on the physical strip. Reconnect to check that the app reads the current settings.

## Create a color sequence

1. Connect to an RGB-capable controller and select the output.
2. Open **Looks** and tap **New sequence**.
3. Tap each color step to pick its color on a wheel or enter an exact hex value. Add two to twelve steps with a hold time of one to 120 seconds each.
4. Choose **Loop sequence** if needed, save, and tap **Play**.

Playback runs on the phone while EmitLoom is open. It stops when you change outputs, use another lighting control, disconnect, or leave the app. The last color stays on the light until another command changes it.

See [controller compatibility](COMPATIBILITY.md) for supported models and hardware verification.
