# Provenance of the vendored DSP

These files are copied **as-is** from the zuluTek firmware tree (the ATEK303 app of
`animatek/FM1-Animatek` with the zuluTek patches applied — same source the FM-1 firmware
runs), so the smartphone preview sounds the same as the FM-1:

- `atek_voice.c/h` — the ATEK303 voice: GPL-3.0-or-later, Copyright (C) 2026 Javier (Animatek);
  Open303 core (Robin Schmidt) MIT — see the headers and `LICENSE-open303.txt`. On this host
  branch the file is upstream ATEK303 v1.2 (`AT_NOSC` 14 waves); the zuluTek-patched variant
  (`AT_ENGINE`, 17 waves GRIT/SUB2/BUZZ) lives in the private firmware tree and is what the
  FM-1 runs — the host shim absorbs the difference via `AT_ENGINE` (see `bass_engines.h`).
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
- `midi_looper.c/h` — the zuluTek MIDI looper (16 slots, notes + knob automation,
  bar-quantized), ported from the ZuluFx app's MIDI loops (FlyingZulu/fm1-Zulu,
  GPL-3.0). Fixed-size state ~16 KB, no libm. Design: `docs/ZULUTEK-MIDI-LOOPER.md`.
