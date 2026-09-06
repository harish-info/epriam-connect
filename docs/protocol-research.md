# e-PRIAM BLE protocol research

Research date: 2026-09-06

## Sources and reliability

1. [`python-priam` at `efc49e35`](https://github.com/vincegio/python-priam/blob/efc49e351a507204776099e8cccba79751e24e88/main.py) — original 2023 proof of concept, with command encoding and notification parsing.
2. [`esPriam32` initial ESPHome implementation at `613f6804`](https://github.com/owanvik/esPriam32/blob/613f6804b335dcd040fcd6be8eac51178bfd31e9/esphome/priam_bridge.yaml) — initially copied the Python packet layout.
3. [`esPriam32` current implementation at `6ffa751a`](https://github.com/owanvik/esPriam32/blob/6ffa751a71e66a73f30e1175f8ccd177d5e616aa/src/main.c) — useful evidence for scanning, GATT discovery, notification traffic, and real-device connection behavior; unreliable for rocking field labels.
4. [`esPriam32` PR #2](https://github.com/owanvik/esPriam32/pull/2) — reports successful empty-hardware operation and decreasing rocking notifications, but retains the mislabeled packet.
5. [Bluetooth SIG assigned numbers](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/Assigned_Numbers.pdf) — confirms company identifier `0x078D` belongs to Cybex GmbH.
6. [Cybex e-PRIAM product and safety guidance](https://www.cybex-online.com/en/row/p/10108379.html) — authoritative behavior and safety constraints.
7. [Microchip RN4871](https://www.microchip.com/en-us/product/RN4871) and its [data sheet](https://ww1.microchip.com/downloads/aemDocuments/documents/WSG/ProductDocuments/DataSheets/RN4870-71-Bluetooth-Low-Energy-Module-DS50002489.pdf) — module capabilities and approximate range.

Neither open-source project contains a captured GATT inventory or raw official-app
write trace. Anything marked `Needs capture` must not be promoted to confirmed
just because a GATT write returned success.

## Discovery and connection

| Item | Candidate value | Confidence | Evidence / caveat |
|---|---|---:|---|
| BLE role | Stroller peripheral and GATT server | High | Both clients connect as central/GATT client. |
| Radio module | Microchip RN4871 | Medium | Reported and hardware-tested by `esPriam32`; not independently inspected here. |
| Company identifier | `0x078D` (1933 decimal) | High | Both projects plus Bluetooth SIG assignment to Cybex. The old ESP comment `0x0791` is wrong. |
| Concurrent clients | One active client | High | Cybex says several phones may know a stroller, but only one can be actively connected. |
| Address | May use a random/private address | Medium | `esPriam32` found random-address connection more reliable; RN4871 has known random-address history. Do not use MAC alone as identity. |
| Pairing/bonding | Not observed | Low | Neither project performs authentication or bonding. Confirm on hardware. |
| Useful range | Roughly 1–10 m | Medium | RN4871 data sheet says up to 10 m; `esPriam32` recommends 1–2 m and RSSI above -85 dBm. |

Use a scan filter for manufacturer ID `0x078D`, then validate the private service
and characteristics after connection. Device names and MAC addresses are only
secondary hints.

## Candidate GATT profile

All UUIDs share suffix `78d3-40c2-9b6f-3c5f7b2797df`.

| Purpose | UUID | Expected access | Confidence |
|---|---|---|---:|
| Private service | `a1fc0101-78d3-40c2-9b6f-3c5f7b2797df` | discover | Medium |
| Status | `a1fc0102-78d3-40c2-9b6f-3c5f7b2797df` | read, notify | High |
| Drive support | `a1fc0103-78d3-40c2-9b6f-3c5f7b2797df` | write; notification uncertain | High for write, low for notify |
| Rocking | `a1fc0104-78d3-40c2-9b6f-3c5f7b2797df` | write, notify | High |
| Battery LEDs | `a1fc0105-78d3-40c2-9b6f-3c5f7b2797df` | read | Medium |

The initial ESPHome file declared service UUID `...0100...`, while the current
hardware-tested ESP code declares `...0101...`. Capture the actual service tree.
On Android, discover descriptors and write the actual `0x2902` CCCD; never assume
the CCCD handle equals characteristic handle + 1.

## Drive support command

Write one byte to characteristic `...0103...`:

| Byte | Meaning | Confidence |
|---:|---|---:|
| `0x01` | Eco | High; exposed by Cybex and both projects. |
| `0x02` | Tour | High; exposed by Cybex and both projects. |
| `0x03` | Boost / higher support | Medium; exercised by `python-priam`, hidden by the official app. |

Treat the UI state as `Unknown`, `Commanded(mode)`, or `Observed(mode)`. A
successful write callback only confirms transport, not that the motor controller
accepted the mode. The characteristic was found unreadable by `esPriam32`; a
notification echo is possible but unproven.

Boost is an experimental control outside Cybex's documented Eco/Tour surface.
Keep it behind Expert settings and a deliberate long-press confirmation. Test it
with an empty stroller before enabling it for normal use.

## Rocking command

### Most likely encoding

Write exactly three bytes to characteristic `...0104...`:

```text
byte 0: low nibble = intensity code; bit 4 = disconnect behavior (semantics unconfirmed)
byte 1: duration in seconds, little-endian low byte
byte 2: duration in seconds, little-endian high byte
```

Intensity codes from `python-priam` and the initial ESPHome implementation:

| Code | Meaning |
|---:|---|
| `0` | Off |
| `1` | High |
| `2` | Medium |
| `3` | Low |

The reversed numeric ordering is important. Do not map `1/2/3` to low/medium/high.

Duration examples, before applying any disconnect flag:

| Requested duration | Seconds | High intensity | Medium | Low |
|---:|---:|---|---|---|
| 5 min | 300 (`0x012C`) | `01 2C 01` | `02 2C 01` | `03 2C 01` |
| 30 min | 1800 (`0x0708`) | `01 08 07` | `02 08 07` | `03 08 07` |
| 60 min | 3600 (`0x0E10`) | `01 10 0E` | `02 10 0E` | `03 10 0E` |
| 180 min | 10800 (`0x2A30`) | `01 30 2A` | `02 30 2A` | `03 30 2A` |

The wire field can represent `0..65535` seconds (18:12:15). That is a protocol
limit, not a safe product limit. The proposed app caps the normal UI at 180
minutes and requires reconfirmation above Cybex's documented 30-minute limit.

### Why the newer ESP labels are wrong

Commit `5cbf23aa` replaced the original `[intensity, durationLow, durationHigh]`
definition with `[0x01, minutes, intensityPercent]` without protocol evidence.
Those bytes are still syntactically valid under the original format:

```text
ESP UI "30 min, 50%" -> 01 1E 32
Original decoding      -> high intensity, 0x321E seconds = 12,830 seconds (3:33:50)
```

This explains why the ESP project can report long, stable rocking even though
its field labels disagree with its own notification parser. Do not copy its
percentage slider or its one-byte minute field.

### Stop and disconnect behavior

- `python-priam` writes a three-byte packet with intensity `0`; its sample retains a nonzero duration.
- `esPriam32` writes a single `00` byte and reports success, but does not preserve raw confirmation evidence.
- Use `00 00 00` as the initial stop candidate, then replace it with the exact official-app write found in capture.
- Bit `0x10` in byte 0 is associated with continue/stop-on-disconnect behavior, but the polarity is unknown. The source variable name and Cybex's default safety behavior do not prove the same polarity.
- Until the hardware test identifies the safe polarity, do not expose “continue after disconnect.” Keep a foreground connection for active sessions and issue an explicit stop during orderly teardown.

## Rocking notification

Characteristic `...0104...` appears to notify at least five bytes:

```text
byte 0: intensity/status/flags
bytes 1-2: remaining seconds, little-endian
bytes 3-4: configured seconds, little-endian
```

Candidate parsing:

- Intensity: `byte0 & 0x0F`
- Disconnect flag: `(byte0 & 0x10) != 0`
- Remaining: `byte1 | (byte2 << 8)`
- Configured: `byte3 | (byte4 << 8)`
- `byte0 == 0x94` was labeled “brake not engaged” by `python-priam`; confirm with a controlled negative test.

The Python parser has a length bug: it accepts four bytes but reads byte index 4.
The Android parser must require at least five bytes and retain unknown trailing
bytes in diagnostics.

The notification, not the local clock or write callback, is the authoritative
rocking state. A start command is successful only after a matching notification
reports nonzero remaining time and the expected configured duration.

## Status and battery

For status notifications on `...0102...`, both projects infer:

```text
raw voltage-like value = unsigned(byte3) * 2
estimated percent = clamp((value - 315) / (380 - 315) * 100, 0, 100)
```

Confidence is medium. Cybex documents a nominal 36 V battery and 42 V maximum
charging voltage, so the 31.5–38.0 mapping is not self-evident. During validation,
record the official app percentage and the entire status packet at low, medium,
and high charge. Label the Android result “Estimated” until it matches.

Characteristic `...0105...` is reported to return a one-byte LED count from 1–3.
Keep this diagnostic-only until confirmed.

## Security implications

No application-layer authentication is visible in either project. If hardware
validation also finds no pairing or bonding, any nearby app can race to the one
available BLE connection and send motor commands. Mitigations available to this
app are limited:

- Require close proximity during first selection (for example, RSSI above -65 dBm).
- Validate company ID plus the expected service/characteristic set.
- Never auto-start motion on connection or reconnection.
- Never persist or display an unredacted MAC in exported logs by default.
- Show a visible connected/rocking notification with an immediate Stop action.
- Document that this improves accidental-selection safety, not cryptographic security.

## Unknowns that block motor-control release

1. Exact service UUID (`...0100...` versus `...0101...`) and characteristic properties.
2. Whether pairing/bonding or encryption occurs.
3. Official start and stop writes.
4. Polarity and behavior of bit `0x10` after link loss.
5. Whether firmware accepts and accurately reports a direct duration above 1800 seconds.
6. Meaning of all status bytes and exact battery mapping.
7. Whether `...0103...` notifies an accepted drive mode.
8. Exact error/status values beyond the candidate `0x94` brake error.
