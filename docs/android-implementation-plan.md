# Android implementation plan

## Outcome

A native, offline Android app that discovers one e-PRIAM, reports connection and
battery state, controls documented Eco/Tour and three rocking intensities, adds
validated timers up to three hours, and exposes validated Boost behind Expert
settings.

The app must never infer motor success from a Bluetooth write callback. Motor
state comes from stroller notifications or remains explicitly `Unconfirmed`.

## Product constraints

- Unofficial and not affiliated with Cybex; no Cybex logos or copied official UI.
- Local BLE only. No login, cloud, analytics, ads, location collection, or internet permission.
- One active stroller connection.
- No motion on app launch, BLE connection, reconnection, process restoration, or device presence.
- Normal timer cap: 180 minutes. Sessions above 30 minutes require a confirmation sheet every time.
- Boost and continue-after-disconnect are Expert controls, off by default.
- Rocking always has an immediate in-app and notification Stop action.

## V1 scope

### Included

- Guided permission and Bluetooth setup.
- Manufacturer-filtered scan, explicit first-device selection, connect/reconnect.
- GATT inventory diagnostics and redacted log export.
- Battery/status notifications and estimated percentage.
- Eco and Tour.
- Low, Medium, High rocking with 5/15/30/60/90/120/180 minute presets and custom 1–180 minutes.
- Live remaining time based on `...0104...` notifications.
- Background continuity for an active rocking session.
- Expert Boost after hardware validation.
- Expert continue-on-disconnect only after bit polarity is proven.

### Excluded

- Firmware updates: no protocol is known, and interruption could brick a safety-critical controller.
- Cloud, Home Assistant, MQTT, remote control, schedules, or automatic start.
- Multiple strollers.
- Arbitrary raw writes in production builds.
- iOS.

## Technical baseline

Pin versions in `gradle/libs.versions.toml`; do not use dynamic versions.

- Kotlin, single-activity app, Jetpack Compose and Material 3.
- `minSdk 26`, `compileSdk 36`, `targetSdk 36`; test compatibility on Android 17/API 37.
- Current stable toolchain at planning time: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.3.21.
- Compose stable line 1.12 and Material 3 stable line 1.4 at planning time; prefer the matching stable Compose BOM when scaffolding.
- Coroutines/Flow, Lifecycle/ViewModel, Navigation Compose, DataStore Preferences.
- Nordic Android BLE Library 2.11.0 for its mature serialized operation queue,
  timeouts, and retry primitives; use platform `BluetoothLeScanner` for the small
  manufacturer-filtered scan. Do not adopt Nordic Kotlin BLE 2.x while it remains beta.
- Manual constructor injection. Hilt/Koin, Room, and a multi-module build add no value for V1.

Before scaffolding, rerun stable-version lookup; the numbers above are a
reproducible baseline, not permission for automatic upgrades.

## Project shape

Start with one `:app` module and enforce package boundaries:

```text
io.github.harishsrikantaiah.epriamconnect
├── app/             App container and manual dependency graph
├── ble/             scan, connection, operation queue, Android callbacks
├── protocol/        UUIDs, codecs, parsers, raw packet models
├── domain/          public state, commands, safety policy, repository interface
├── service/         active-session connectedDevice foreground service
├── diagnostics/     bounded redacted event log and export
└── ui/
    ├── onboarding/
    ├── dashboard/
    ├── rocking/
    └── settings/
```

Keep protocol code pure Kotlin without Android types. Split modules only if a
second app/tool needs the protocol or build times justify it.

## Core interfaces and state

```kotlin
interface PriamTransport {
    val events: Flow<TransportEvent>
    suspend fun scan(): Flow<PriamCandidate>
    suspend fun connect(candidate: PriamCandidate)
    suspend fun read(characteristic: Uuid): ByteArray
    suspend fun subscribe(characteristic: Uuid): Flow<ByteArray>
    suspend fun write(characteristic: Uuid, value: ByteArray): WriteReceipt
    suspend fun disconnect()
}

interface PriamController {
    val state: StateFlow<PriamState>
    suspend fun setDriveMode(mode: DriveMode)
    suspend fun startRocking(request: RockingRequest)
    suspend fun stopRocking(reason: StopReason)
}
```

Suggested state is explicit about certainty:

```text
Connection = BluetoothOff | PermissionMissing | Idle | Scanning | Connecting |
             Discovering | Subscribing | Ready | Reconnecting(attempt) | Failed(reason)

Drive = Unknown | Commanded(mode) | Observed(mode)

Rocking = Off | Starting(request) | Active(intensity, remaining, configured, flags) |
          Stopping | Rejected(error, raw) | Unconfirmed(lastCommand)
```

`PriamState` also carries estimated battery, raw battery data, RSSI, protocol
capabilities, last packet time, and a bounded non-sensitive diagnostic trail.

## Protocol codec

Implement only typed commands:

```text
DriveCodec.encode(Eco)   = 01
DriveCodec.encode(Tour)  = 02
DriveCodec.encode(Boost) = 03

RockingCodec.encode(intensity, seconds, disconnectPolicy)
  byte0 = intensity.code OR validatedDisconnectFlag
  byte1 = seconds AND 0xFF
  byte2 = (seconds SHR 8) AND 0xFF
```

- Accept seconds `1..10800` in normal builds; protocol-lab tests may reach `65535`.
- Reject zero/overflow/negative durations rather than truncate.
- Stop uses the captured official packet, not a guessed overload.
- Require five bytes before parsing a rocking notification.
- Preserve raw bytes beside parsed values for diagnostics.
- Convert bytes with `toInt() and 0xFF`; never rely on signed Kotlin `Byte` behavior.

## BLE lifecycle

All GATT operations are serialized and complete through their callbacks.

1. Check BLE feature, adapter state, and runtime permission.
2. Scan for company ID `0x078D` for at most 12 seconds.
3. Show candidates; require a close-range explicit tap on first setup.
4. Connect with LE transport and `autoConnect = false`.
5. Discover services and validate the candidate GATT profile. Never use fixed handles.
6. Subscribe to status and rocking by locating their actual `0x2902` descriptors.
7. Read status and optional battery-LED value.
8. Enter `Ready`; enable only capabilities actually discovered.
9. For a command: enqueue one write, await callback, then await a matching
   notification with a 5-second timeout.
10. Deduplicate Start. Stop clears queued Start and takes priority over all other operations.
11. On GATT error/link loss: close the old `BluetoothGatt`, back off
   `1s, 2s, 4s` with jitter, rescan by company/profile, and stop after three attempts.
12. Reconnection restores observation only; it never replays a motor command.

Avoid hidden `refresh()` APIs and fixed sleeps. Record callback status codes and
state transitions, but redact addresses in export.

## Android permissions

For Android 12+:

- `BLUETOOTH_SCAN` with `neverForLocation` because results are not used to derive location.
- `BLUETOOTH_CONNECT`.
- No `BLUETOOTH_ADVERTISE`.

For Android 8–11:

- legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion=30`;
- `ACCESS_FINE_LOCATION` with `maxSdkVersion=30`, requested only when scanning.

Declare `android.hardware.bluetooth_le` required. Explain “Nearby devices” in
plain language and handle denial without loops. If `neverForLocation` filters the
stroller on a tested OEM, document that device and use a narrowly scoped fallback.

Do not choose Companion Device Manager for V1: current Android guidance notes
that it does not support random MAC addresses, while the stroller may advertise
with one. Reassess only after Gate A establishes stable addressing.

## Background session design

When a confirmed user action enters `Starting`, start a foreground service while
the activity is visible, before sending the write. Stop it if the command is
rejected or times out:

- service type `connectedDevice`;
- manifest permission `FOREGROUND_SERVICE_CONNECTED_DEVICE`;
- ongoing notification: intensity, stroller-reported remaining time, connection
  state, and a direct Stop action;
- notification opens the live controller;
- service stops after confirmed rocking Off and clean disconnect.

Android can still kill the process or lose the radio. The safe disconnect bit is
therefore more important than the service. Never promise indefinite connection.
Do not use periodic WorkManager scans or auto-start from device presence.

## Screens and interaction

### Onboarding

- “Unofficial local controller” disclosure.
- Current Cybex safety checklist: monitor the child; brake and front swivel
  wheels locked; level clear surface; harness secured; no stairs/slopes/obstacles;
  lower intensity until the child can sit unassisted; documented limit 22 kg.
- Nearby-device permission and Bluetooth enablement.
- Scan/select screen showing name, signal strength, and verified-profile badge.

### Dashboard

- Connection status and retry/disconnect.
- Estimated battery with “Estimated” label until mapping is verified.
- Eco/Tour segmented control; displayed as Commanded if no mode notification confirms it.
- Boost absent until Expert mode is enabled and the hardware gate passed.
- Rocking card with intensity, duration presets/custom input, and one prominent Start/Stop control.

### Start confirmation

Every start shows brake/wheels/level/monitoring confirmation. Above 30 minutes,
also state that Cybex's official app documents a 30-minute maximum and require a
press-and-hold start. Continue-after-disconnect is never silently inherited from
a previous session.

### Active session

- Large remaining time from notifications, not a free-running local timer.
- Intensity, disconnect policy, last packet age, and connection state.
- Stop stays available during reconnect attempts; if a stop cannot be delivered,
  instruct the user to switch the stroller off physically.

### Diagnostics

- App/toolchain version, phone/Android version, permission and adapter state.
- GATT profile with properties/descriptors.
- Timestamped state transitions and UUID/hex packets.
- Copy/export with MAC and device identifiers redacted by default.
- Protocol-lab writes exist only in internal debug builds.

## Safety invariants in code

- No automatic motor command after connect, reconnect, rotation, process restore, or service restart.
- One in-flight motor command; Stop cancels pending Start.
- Start is enabled only in `Ready`, with a recently received status packet.
- A write callback does not change state to Active.
- Active requires a matching notification with nonzero remaining time.
- Duration above 30 minutes requires per-session confirmation.
- The safe disconnect policy is hard-coded as default only after hardware validation.
- Unknown/error notifications fail closed and keep Stop visible.
- If notifications are stale for 10 seconds, show `Unconfirmed`; do not resend Start.
- Never auto-renew a timer. Prefer one validated 16-bit duration command.

## Delivery milestones

### M0 — protocol evidence (0.5–1 day plus physical observation)

- Run the hardware-validation document.
- Commit a sanitized capture table for the exact stroller/app/firmware.
- Resolve service UUID, properties, stop packet, disconnect bit, >30-minute
  acceptance, mode notification, and error bytes.

Exit: every motor command in the codec has captured evidence. This milestone is
the blocker for distributable motor control.

### M1 — scaffold and pure protocol (1 day)

- Create the Compose app and pinned version catalog.
- Add pure codecs/parsers, models, fixtures, and unit tests.
- Add fake transport and manual dependency graph.

Exit: boundary/malformed packet tests pass without Android or hardware.

### M2 — read-only BLE diagnostics (1–2 days)

- Permission flow, filtered scan, explicit selection, connect, discovery, subscriptions.
- Connection state machine, redacted log, GATT inventory, battery raw/estimate.
- No motor writes in release variant.

Exit: 20 connect/disconnect cycles on the target phone without leaked GATT
objects, crashes, or wrong-device selection.

### M3 — documented controls (1–2 days)

- Eco, Tour, and rocking up to 30 minutes.
- Notification-confirmed state, Stop priority, errors, safety confirmation.

Exit: three consecutive sessions per intensity; Stop succeeds from UI and
notification; brake-negative test fails closed.

### M4 — extended timer and background continuity (1–2 days)

- Direct validated durations up to 180 minutes.
- `connectedDevice` foreground service and active notification.
- Link-loss behavior, reconnect observation, physical-stop guidance.

Exit: 31-, 60-, and 180-minute configured values are reported correctly by the
stroller; app background/screen-off/rotation do not duplicate commands. The
three-hour test may be time-compressed for automation but needs one real soak run.

### M5 — Expert Boost and disconnect option (1 day)

- Gate Expert settings behind explicit disclosure.
- Add Boost and the non-default continue-after-disconnect option only if M0 evidence passed.
- Preserve Eco recovery and physical-off guidance.

Exit: empty-stroller matrix passes twice and Expert state never leaks into normal defaults.

### M6 — release hardening (1–2 days)

- Accessibility, localization-ready strings, dark mode, adaptive layout.
- Baseline profile if startup warrants it; R8 release build; signing/documentation.
- Android 8/11/12/16/17 compatibility matrix and target-phone OEM test.
- License notices and unofficial-product disclaimer.

Exit: Definition of Done below is met. Expected focused effort after hardware
access: roughly 7–11 development days plus the three-hour physical soak.

## Test strategy

### Pure unit tests

- Drive encoding `1/2/3`.
- Rocking intensity reversal (`1=High`, `2=Medium`, `3=Low`).
- Little-endian boundaries: 1, 255, 256, 1800, 1860, 3600, 10800, 65535 seconds.
- Reject 0, negative, >UI cap, and >65535.
- Disconnect flag clear/set after polarity is captured.
- Notification length 0–4 rejected; five and extra bytes parsed safely.
- Remaining/configured endianness and `0x94` candidate error.
- Battery unsigned conversion, clamping, malformed status.
- State reducer: no write-only success can produce `Active`.
- Stop preemption and duplicate-start suppression.

### JVM integration tests with fake transport

- Connect/discover/subscribe sequencing.
- One operation in flight.
- Write success + matching notify, mismatch, timeout, rejection.
- Link loss at each state, bounded reconnect, no command replay.
- Stale notification transition to `Unconfirmed`.
- Process-state restoration contains preferences but no pending motor command.

### Android instrumentation/UI tests

- Permission matrices for API 26/30/31/36/37.
- Bluetooth off, denial, permanent denial, scan timeout, unsupported GATT profile.
- Rotation/background/service/notification Stop behavior.
- Accessibility labels, 200% font scale, TalkBack order, touch targets.
- Screenshot tests for onboarding, ready, active, reconnecting, and error states.

### Hardware regression matrix

- At least one physical phone on Android 12+ and the owner's primary phone.
- 20 connection cycles and 10 forced link losses.
- All three intensities and all normal duration boundaries.
- Stop from UI, notification, disconnect-safe behavior, and physical power.
- Eco/Tour plus Expert Boost twice with empty stroller.
- One screen-off/background 60-minute run and one supervised, empty-stroller
  180-minute soak.

## Risks and decisions

| Risk | Consequence | Control |
|---|---|---|
| Reference projects disagree | Wrong duration/intensity and unexpected long motion | M0 capture gate; pure codecs; no ESP field labels. |
| Unauthenticated nearby control | Another phone can occupy/control the link | Close-range selection, profile validation, no auto-motion; document lack of crypto. |
| Random address | Persisted MAC fails or selects stale device | Rescan by company/profile; store user alias, not MAC as sole identity. |
| Android process/radio loss | Cannot send Stop or receive countdown | Safe disconnect bit, foreground service, physical-off instruction. |
| Firmware differences | Commands behave differently across stroller generations | Record model/firmware with captures; capability gate; fail closed. |
| Hidden Boost | Higher power may be unsupported or unsafe | Empty-stroller gate, Expert-only, long press, Eco recovery. |
| Extended rocking | Exceeds manufacturer-tested official app limit | 180-minute cap, per-session confirmation, monitoring reminder, no automation. |
| Battery estimate wrong | Misleading range | “Estimated” label and official-app calibration pairs. |

## Definition of Done

- Sanitized hardware capture proves every enabled write and parser field.
- Release build contains no raw-write console, auto-start, analytics, or internet permission.
- Connection state and all errors are visible and recoverable.
- Stop is prioritized, available from UI and notification, and validated under link loss.
- Reconnect never replays a motor command.
- Extended duration is stroller-confirmed, not app-clock-only.
- Boost and continue-after-disconnect are Expert-only and default off.
- Unit, fake-transport, UI, permission, hardware-cycle, and soak tests pass.
- README states exact supported stroller model/firmware/phone matrix and known unknowns.
- Signed APK/AAB and reproducible build instructions are produced; publishing remains a separate explicit decision.

## Primary Android references

- [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Connect to a BLE GATT server](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server)
- [Communicate with BLE in the background](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Connected-device foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
- [Companion device pairing limitations](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Android BLE platform sample](https://github.com/android/platform-samples/tree/main/samples/connectivity/bluetooth/ble)
- [Nordic Android BLE Library 2.11.0](https://github.com/NordicSemiconductor/Android-BLE-Library/releases/tag/2.11.0)
