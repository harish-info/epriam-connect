# ePriam Connect

An unofficial, open-source Android controller for the CYBEX e-Priam stroller.

<p align="center">
  <img src="docs/screenshots/connection.png" width="30%" alt="Stroller connection screen" />
  <img src="docs/screenshots/controls.png" width="30%" alt="Rocking controls" />
  <img src="docs/screenshots/drive-assistance.png" width="30%" alt="Drive assistance modes" />
</p>

## Features

- Direct Bluetooth LE discovery and connection
- Rocking intensity and timers up to three hours
- Eco, Tour, and experimental Boost drive assistance
- Battery status, light/dark themes, and offline demo mode

## Build

Requires JDK 17 and Android SDK 36.

```bash
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Safety

This is an experimental enthusiast project. Test with an empty stroller, stay nearby, and physically verify that motion has stopped after any connection or command failure. Boost and sessions longer than 30 minutes are not exposed by the official app and may carry additional risk.

This project is not affiliated with or endorsed by CYBEX GmbH.

## Documentation

- [Protocol research](docs/protocol-research.md)
- [Hardware validation](docs/hardware-validation.md)
- [Implementation plan](docs/android-implementation-plan.md)

## License

[MIT](LICENSE)
