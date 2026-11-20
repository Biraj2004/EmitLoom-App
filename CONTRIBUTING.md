# Contributing to EmitLoom

Bug reports, controller compatibility reports, documentation, tests, and code are welcome. Follow the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities using the [security policy](SECURITY.md).

## Report a bug or test a controller

Search [existing issues](https://github.com/Biraj2004/EmitLoom-App/issues) first. Include the app version or commit, Android version, controller model and firmware if visible, selected output, steps, and expected and observed behavior. Share only the relevant, redacted portion of any logs or screenshots.

For compatibility findings, use the [hardware verification guide](docs/COMPATIBILITY.md#hardware-verification). State which behavior you observed on a real controller and which checks used unit tests or an emulator.

## Open a pull request

1. Fork the repository and create a branch from `main`.
2. Keep the change focused. Update the relevant docs or tests when behavior changes.
3. Run unit tests and lint using JDK 21 and Android SDK Platform 35 with Build Tools 35.0.0.
4. In the pull request, describe the change and the verification you performed. Include controller and firmware details for changes to Bluetooth behavior.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

On Windows use `.\gradlew.bat` in place of `./gradlew`. These checks run without release signing credentials.

## Contribution licensing

Submit original work or material you have permission to contribute under compatible terms. By submitting original work for inclusion, you make it available under the project's [AGPL-3.0-only license](LICENSE). Identify any third-party material and retain its applicable notices.

Keep vendor APKs, extracted assets, private traces, credentials, signing files, and generated APKs outside Git. Use public protocol references and small, independently written test fixtures. See the [legal notes](LEGAL.md) for licensing and release details.
