# ePriam Connect

An unofficial, offline Android controller for the Cybex e-PRIAM stroller.

## Current status

The first native Android implementation is complete and emulator-tested. It
includes direct BLE discovery, connection state, battery estimates, Eco/Tour
assistance, experimental Boost, rocking intensity, timers from 5 minutes to 3
hours, a persistent Stop notification, diagnostics, and an offline demo mode.

The app has not yet been connected to the target stroller. Release builds keep
motor writes locked until the [hardware validation runbook](docs/hardware-validation.md)
has been completed. Debug builds expose an explicit **Protocol Lab** switch for
careful bench testing.

The key protocol finding is that the two reference projects do **not** agree on
the rocking packet. The older `python-priam` layout is internally consistent
with the five-byte rocking notifications:

```text
[intensity_and_flags, duration_seconds_low, duration_seconds_high]
```

The newer `esPriam32` layout (`[1, minutes, percent]`) appears to be a semantic
regression introduced during its 2026 rewrite. It can still make the stroller
rock because those three bytes form a valid packet under the older layout, but
the resulting duration is not the duration shown by its UI.

## Documents

- [Protocol research](docs/protocol-research.md)
- [Hardware validation runbook](docs/hardware-validation.md)
- [Android implementation plan](docs/android-implementation-plan.md)

## Build and test

Requirements: JDK 17 and Android SDK 36.

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest   # with an emulator or device attached
```

Install `app/build/outputs/apk/debug/app-debug.apk`, accept the safety screen,
then use **Open demo without a stroller** to exercise all non-Bluetooth flows.

## Safety boundary

- Never test motor commands with a child in the stroller.
- Engage the parking brake, lock the front wheels, and use level clear ground.
- Stay beside the stroller for every rocking session.
- Treat timers above 30 minutes and Boost as undocumented experiments.
- If command status is unconfirmed, physically verify that motion stopped.

## Immediate next step

Run the hardware-validation gate with an empty stroller on a flat, clear floor,
capture the official start/stop packets, and confirm both service UUID variants.
Only then change `HARDWARE_PROTOCOL_VALIDATED` in `PriamRepository` and enable
motor writes in release builds.

## Positioning

This project is not affiliated with or endorsed by Cybex GmbH. Avoid Cybex
logos and official-app visual assets. Keep the app offline: no account, cloud,
analytics, advertising, or `INTERNET` permission.
