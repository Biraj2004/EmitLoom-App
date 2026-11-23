# Build and publish an EmitLoom release

## Automatic GitHub releases

The **Android Release** workflow runs on pushes to `main` and can also be run manually from the Actions tab. It tests and lints the current commit, builds and verifies a signed APK, and publishes it under [Releases](https://github.com/Biraj2004/EmitLoom-App/releases/latest). The release tag points to the commit used to build the APK. A burst of pushes can skip intermediate commits. The next successful release covers the changes since the previous published version.

The workflow increments the patch of the highest `vMAJOR.MINOR.PATCH` tag. With no tags, the first release is `v1.0.1` because the initial local APK is `v1.0.0`. Android's version code also increases. Release notes list up to ten commit summaries, link to the full comparison, and include the APK's SHA-256. A failed build leaves the previous release available. Retry from Actions using the current `main` commit.

### One-time signing setup

Open **Settings → Secrets and variables → Actions** in the repository and add these repository secrets using the existing release key. Reusing the key lets users update existing installations.

| Secret | Value |
| --- | --- |
| `EMITLOOM_KEYSTORE_BASE64` | Base64 of the existing `release.p12` file |
| `EMITLOOM_KEY_ALIAS` | `EMITLOOM_KEY_ALIAS` from `signing.properties` |
| `EMITLOOM_STORE_PASSWORD` | `EMITLOOM_STORE_PASSWORD` from `signing.properties` |
| `EMITLOOM_KEY_PASSWORD` | `EMITLOOM_KEY_PASSWORD` from `signing.properties` |

From the repository root in PowerShell, copy the keystore value to the clipboard and paste it into `EMITLOOM_KEYSTORE_BASE64`.

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.emitloom-signing/release.p12').Path)) | Set-Clipboard
```

The workflow publishes using the repository's built-in `GITHUB_TOKEN` with `contents: write`. Once the secrets are set, push the workflow to `main` or run it from the Actions tab if it is already on `main`.

## Sign and build locally

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

Expect `com.emitloom.app`, the label `EmitLoom`, and version `1.0.0` for a local build with default settings. To verify controller behavior, install the exact signed APK and follow the [hardware verification steps](COMPATIBILITY.md#hardware-verification).
