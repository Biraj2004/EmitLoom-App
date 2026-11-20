# Build and publish a EmitLoom release

## Sign and build

Use a private Android signing key that you control. Keep a secure backup of the key and its credentials. Android uses the signing identity for app updates.

For local builds, put these settings in the Git-ignored file `signing.properties` at the project root. The same settings can be supplied as environment variables, which take precedence. On Windows, use forward slashes in the file path.

```properties
EMITLOOM_KEYSTORE=.emitloom-signing/release.p12
EMITLOOM_KEY_ALIAS=emitloom-release
EMITLOOM_STORE_PASSWORD=<store password>
EMITLOOM_KEY_PASSWORD=<key password>
```

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

After successful assembly Gradle exports one signed APK to `apk-export/emitloom-v1.0.0-release.apk`. The export uses the current build metadata and atomically replaces the previous file. Failed assembly leaves the existing export intact.

Verify that the APK signature is valid and inspect its identity.

```powershell
$apk = 'apk-export/emitloom-v1.0.0-release.apk'
& "$env:ANDROID_HOME/build-tools/35.0.0/apksigner.bat" verify --verbose --print-certs $apk
& "$env:ANDROID_HOME/build-tools/35.0.0/aapt2.exe" dump badging $apk
Get-FileHash -Algorithm SHA256 $apk
```

Expect `com.emitloom.app`, the label `EmitLoom`, and version `1.0.0`. Install the exact signed artifact and complete the [hardware verification](COMPATIBILITY.md#hardware-verification) before publishing a stable release.

## Publish on GitHub

1. Confirm the right to publish the included source and assets, and clear the release name in the intended markets.
2. Review the branch and its tracked paths. Include the Gradle wrapper and the source, docs, and notice files. Keep reference binaries, extracted resources, research tools, signing files, and APKs outside Git history.
3. Create an empty GitHub repository and push the reviewed source. Check the Android CI workflow after the push.
4. Tag the tested source `v1.0.0` and create a GitHub Release for that tag. Attach the verified APK and list the SHA-256 and hardware tested. Keep the corresponding source accessible beside the binary under the AGPL.

```powershell
git status --short --branch
git log --oneline
git ls-files
```

After reviewing the staged paths and creating the empty remote, replace the example URL with the repository URL from GitHub.

```powershell
git remote add origin 'https://github.com/YOUR_ACCOUNT/EmitLoom.git'
git push -u origin main
```

Use the same tested commit for the source tag and the bundled release APK.

Suggested GitHub topics are `android`, `kotlin`, `jetpack-compose`, `bluetooth-low-energy`, and `led-controller`.
