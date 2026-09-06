# ePriam Connect

Planning workspace for an unofficial, offline Android controller for the Cybex
e-PRIAM stroller.

## Current status

Research and implementation planning are complete. No Android application has
been implemented yet. Motor-control writes are deliberately gated on a short
hardware-validation session with the target stroller.

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

## Immediate next step

Run the hardware-validation gate with an empty stroller on a flat, clear floor.
Do not implement extended timers or Boost from the newer ESP packet labels.

## Positioning

This project is not affiliated with or endorsed by Cybex GmbH. Avoid Cybex
logos and official-app visual assets. Keep the app offline: no account, cloud,
analytics, advertising, or `INTERNET` permission.
