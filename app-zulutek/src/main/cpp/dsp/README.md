# Provenance of the vendored DSP

These files are copied **as-is** from the zuluTek firmware tree (the ATEK303 app of
`animatek/FM1-Animatek` with the zuluTek patches applied — same source the FM-1 firmware
runs), so the smartphone preview sounds the same as the FM-1:

- `atek_voice.c/h` — the ATEK303 voice: GPL-3.0-or-later, Copyright (C) 2026 Javier (Animatek);
  Open303 core (Robin Schmidt) MIT — see the headers. With the zuluTek patch: `AT_ENGINE`,
  17 waves (GRIT/SUB2/BUZZ).
- `bass_engines.c/h` — the zuluTek bass engines (PLUK/FM/SUB/8BIT), ported from the ZuluFx app
  (GPL-3.0, FlyingZulu): `AT_NENGINES`, `AT_ENGINE_NAME`, `at_engine_index()`.
- `fx.c/h` — the zuluTek FX chain: GPL-3.0-or-later Javier (Animatek). DRIVE MOJO's soft-clip
  curve is Chris Johnson's airwindows (MIT, `LICENSE-airwindows.txt`); with the zuluTek patch:
  MOJO + `FX_CRUSH`/`FX_HOLD`, the free BBD delay, the Black Hole reverb, and the punch-in ring
  (`fx_punch`, `PN_NTYPES`, `PUNCH_TYPE_NAME`, `PUNCH_USES`).
- `acid_pattern.c/h` — the acid pattern generator/renderer (AcidGen), GPL-3.0-or-later.
- `fastmath.h` — **not** the FM-1's fastmath: a shim mapping the `fm_*` API to libm (Android has
  libm), same file the ZuluFx port uses. The FM-1 uses its own approximations, so tiny numeric
  differences vs the device are expected.

Compile note: `FX_HOST` is defined so `fx.c`'s big buffers stay out of the FM-1's `.pool`
section (plain heap on Android).
- `drum_engine.c/h` — the drum machine (8 pads 808/909-style + 16-step seq), vendored from
  PR #5 (`app-zulutek-drums`, branche `vibe/zulutek-drum-analog-c9ef18`), C99, GPL-3.0 zuluTek.
- `midi_looper.c/h` — the zuluTek **Loop** (16 slots, notes + knob automation,
  bar-quantized; vendored file keeps its upstream name), ported from the ZuluFx app
  (FlyingZulu/fm1-Zulu, GPL-3.0). Fixed-size state ~16 KB, no libm.
  Design: `docs/ZULUTEK-LOOP.md`.
