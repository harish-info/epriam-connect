# Hardware validation runbook

This gate converts source-code hypotheses into evidence for the exact stroller
firmware. Perform motor tests with an empty stroller, outdoors or on a flat clear
floor, with a second adult ready to switch the stroller off.

## Safety setup

- Lock the parking brake and front swivel wheels for rocking tests.
- Use level ground, clear of walls, stairs, slopes, obstacles, pets, and children.
- Keep one hand at the physical power control during every unknown write.
- Close the official app before using another GATT client; only one active client is supported.
- Do not test Boost with a child or on a public path.
- Stop immediately for unexpected motion, noise, error indication, or loss of control.

These precautions supplement, not replace, the current Cybex manual and app
instructions.

## Tools

- Android phone with the official Cybex app and Developer options.
- nRF Connect for Android for GATT inventory and manual read-only inspection.
- ADB on the workstation.
- Wireshark for Bluetooth HCI snoop inspection.
- This repository's protocol table for predicted values.

Do not install or connect `esPriam32` during capture; it can take the stroller's
single BLE connection and its rocking encoder is mislabeled.

## Gate A — identify the peripheral

1. Power-cycle the stroller and close the Cybex app.
2. Scan in nRF Connect.
3. Record every advertisement carrying company identifier `0x078D`:
   - displayed name;
   - address and address type if shown;
   - RSSI at approximately 0.5 m, 1 m, and 3 m;
   - full manufacturer data;
   - advertised service UUIDs.
4. Repeat one power cycle. Note whether the address changes.
5. Connect and export the complete GATT table: service UUIDs, characteristic
   UUIDs, properties, permissions, and descriptors.
6. Check Android Bluetooth settings for a bond/pairing record.

Pass condition: `0x078D` plus the expected private characteristics uniquely
identifies the stroller. If not, add a user-selection step; do not broaden name
matching.

## Gate B — capture the official app

1. In Developer options, enable **Bluetooth HCI snoop log**.
2. Reboot or toggle Bluetooth if the phone requires it.
3. Start a written timeline and perform one action at a time in the official app:
   - connect only;
   - select Eco;
   - select Tour;
   - rock Low for 1 minute, then Stop;
   - rock Medium for 5 minutes, then Stop;
   - rock High for 30 minutes, then Stop;
   - repeat the shortest rocking command with each state of the app's
     continue-on-disconnect option.
4. After each action, wait for at least two notifications and record wall-clock time.
5. Generate a bug report:

   ```bash
   adb devices -l
   adb bugreport epriam-official-capture.zip
   ```

6. Extract the Bluetooth snoop log from the bug report and open it in Wireshark.
7. Filter for ATT traffic (`btatt`) and correlate writes/notifications with the timeline.
8. Save only the minimal packet table in this repo. Do not commit the full bug
   report: it may contain personal device data.

Pass condition: exact writes for intensity, duration, stop, drive mode, and
disconnect behavior are recorded. If traffic is encrypted, the phone-side HCI
log should still expose host ATT payloads; if it does not, use a dedicated test
phone and runtime instrumentation only on software you are entitled to inspect.

## Gate C — direct protocol probes

Build the Android app's read-only diagnostics milestone first. Add motor writes
behind a compile-time `protocolLab` flag, disabled in release builds.

For each probe, require a successful write callback **and** a matching rocking
notification. Run this matrix with an empty stroller:

| Probe | Predicted write without bit `0x10` | Expected notification |
|---|---|---|
| High, 10 s | `01 0A 00` | intensity 1, set 10, countdown |
| Medium, 60 s | `02 3C 00` | intensity 2, set 60, countdown |
| Low, 300 s | `03 2C 01` | intensity 3, set 300, countdown |
| Stop | use captured official value; candidate `00 00 00` | remaining 0 / off |
| Medium, 31 min | `02 44 07` | set 1860, countdown |
| Medium, 60 min | `02 10 0E` | set 3600, countdown |

Do not jump directly to three hours. A 31-minute probe establishes whether the
firmware—not merely the transport—accepts values beyond the official limit.

### Disconnect-bit polarity

1. Start a 20-second low-intensity session with bit `0x10` clear (`03 14 00`).
2. Force-close the client or disable phone Bluetooth after five seconds.
3. Observe whether motion stops immediately or completes the cycle.
4. Repeat with the bit set (`13 14 00`).
5. Repeat each case once to exclude a transient link failure.

Whichever variant stops on link loss becomes the mandatory default. The
continue-on-disconnect variant remains an explicitly confirmed Expert option.

### Brake error

Only after positive rocking tests:

1. Keep the empty stroller controlled and release the parking brake.
2. Send the captured 10-second low-intensity command.
3. Confirm that no rocking starts.
4. Record the full notification; verify or reject candidate status byte `0x94`.
5. Re-engage the brake immediately.

## Gate D — Boost

1. Use an empty stroller on a flat private area with a spotter.
2. Write Eco (`01`) and Tour (`02`) first; confirm normal control.
3. Write candidate Boost (`03`) once.
4. Record callback, any notification, LEDs, audible indications, and behavior at
   walking speed. Do not exceed normal walking speed.
5. Power-cycle and verify that the stroller returns to a documented mode.

Pass condition: the stroller accepts `03`, remains controllable, and can always
return to Eco. Otherwise remove Boost from the app rather than retrying writes.

## Evidence file template

Create `docs/captures/YYYY-MM-DD-device.md` after testing:

```text
Stroller model/year:
Stroller firmware shown by official app:
Phone model / Android version:
Official app version:
Address type and rotation:
Pairing/bonding observed:
Service UUID:
Characteristic properties/descriptors:

Action | write UUID | write hex | notify UUID | notify hex | physical result

Disconnect bit clear:
Disconnect bit set:
Safe default polarity:
Battery raw / official percent pairs:
Unknown bytes/errors:
```

## Release gate

Motor controls may be enabled in a distributable build only when:

- captured official commands and direct probes agree;
- Stop works from UI and notification on three consecutive sessions;
- the safe disconnect-bit polarity is known;
- a reconnect never restarts motion;
- extended duration is acknowledged by a matching `set_time` notification;
- Boost has an independent kill path (Stop/power off/Eco) and stays Expert-only.
