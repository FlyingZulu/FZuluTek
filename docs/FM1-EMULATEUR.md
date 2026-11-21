# Pré-tester le firmware zuluTek sur FM-1 — sans flasher (émulateur)

Le FM-1 est un **JieLi AC791N (WL82)** : 2 cœurs pi32v2 (ISA propriétaire JieLi), flash 1 Mo,
écran 240×240, 27 touches, 14 boutons, 7 encodeurs. Il existe un **émulateur complet** qui
fait tourner les firmwares **tels quels** (packages `.fwsc` inchangés) :

## fm1-emu (joakimeriksson, GPL-3.0, Rust)
- **Navigateur** : https://joakimeriksson.github.io/fm1-emu/ — « Open .fwsc… » charge un build
  local ; émulation CPU (2 cœurs pi32v2, JIT) + SoC (timers, flash, écran, matrice de touches,
  encodeurs, audio, UART MIDI). La flash est persistée par le navigateur (settings/presets).
- **Natif** : `fm1-live --fwsc zuluTek.fwsc` (fenêtre + audio + MIDI), binaires sur
  https://github.com/joakimeriksson/fm1-emu/releases/latest .
- **Headless / tests scriptés** : `fm1-emu --fwsc zuluTek.fwsc --ms 5000 --keys "2500-4000:n7"
  --wav out.wav` (joue des touches scriptées, rend un WAV) ; `fm1-live --shot` rapporte la part
  d'écran. Parfait pour un smoke-test CI du firmware.
- **Plugin DAW** : CLAP/VST3/AU (le firmware joue comme un instrument, MIDI du host).
- Animatek embarque aussi l'émulateur en sous-module : `terceros/fm1-emu` +
  `docs/02-emulador.md` + `emulador/README.md` dans `animatek/FM1-Animatek`.

## Workflow recommandé pour zuluTek
1. `git clone animatek/FM1-Animatek && cd FM1-Animatek && git am patches/000*.patch`
   (depuis ce dépôt, branche `vibe/fm1-patch-seqplus-c9ef18`), puis `herramientas/clonar-terceros.sh`
   (fm1-base, MPL-2.0) et `atek303/build.sh` → `atek303.fwsc`.
2. **Pré-test** : `fm1-live --fwsc atek303.fwsc` (ou la page web, ou headless `--keys/--wav`).
   Vérifier : boot, pages (SELECT), SEQ+ (double-tap SEQ), mutation (ENV/LFO), pages
   LFO/ENV/ASSIGN (double-tap ENV/LFO/ARP), OPTIONS > LUDIQUE, presets.
3. Ce n'est que quand l'émulateur est satisfaisant qu'on flashe le vrai FM-1 (USB-MIDI SysEx,
   voir `docs/FM1-RESSOURCES.md`) — avec backup d'abord (pas de bouton recovery sur la carte).

## Note
Un firmware qui boot dans l'émulateur boot sur le vrai appareil (les premières frames prennent
plus de temps). Voir aussi `w0ts/fm1-emulator` / `hdavid/fm1-emulator` (Rust, desktop).

## Test zuluTek lancé en sandbox le 2026-11-21 (branche `vibe/fm1-jieli-emu-verify-c75184`)

Build : `animatek/FM1-Animatek` @ `8934f53` + `git apply` 0001..0022 (propre), `terceros/fm1-emu`
(`cargo build --release -p fm1-emu`), `.fwsc` buildé toolchain JieLi pinnée (616 059 o,
`FM-1_33000`, SRAM 37 044 o libres). 12 scénarios headless, tous **rc=0** :

```sh
fm1-emu --fwsc atek303.fwsc --ms 5000 \
  --keys "0-200:PLAY,300-500:n7,700-900:n9,1100-1300:n12,2000:midi:903e64,2300:midi:803e64" \
  --wav out.wav          # boot + notes clavier + MIDI -> WAV 44,1 kHz non muet (RMS ~4 600)
fm1-emu --fwsc atek303.fwsc --ms 6000 \
  --keys "1000-1500:REC,1100-1300:PLAY" --snap 1800 --wav out.wav   # -> LOOP PAGE 12 à l'écran
fm1-emu --fwsc atek303.fwsc --ms 6000 \
  --keys "1000-1500:REC,1100-1300:SEQ"  --snap 1800 --wav out.wav   # -> MACRO PAGE 13
fm1-emu --fwsc atek303.fwsc --ms 6000 \
  --keys "1000-1500:REC,1100-1300:EDIT" --snap 1800 --wav out.wav   # -> FINE PAGE 14
fm1-emu --fwsc atek303.fwsc --ms 6000 \
  --keys "1000-1500:REC,1100-1300:SAVE" --snap 1800 --wav out.wav   # -> MIX PAGE 15 (4 tranches)
# + tap GLO -> OPTIONS, encodeur SELECT -> cycle de pages, dbl-tap SEQ/GLO, triple-tap GLO (sirène)
```

Syntaxe `--keys` : `T-T2:nom` = appui maintenu de T à T2, `T:nom` = tap, `T:n7` = note (n0..n26),
`T:C4` = note nommée, `T:SELECT:+n` / `T:enc2:+n` = encodeur, `T:midi:HEX` = octets MIDI IN,
`T:pot:512` = potard MASTER. `--snap MS,...` écrit `work/lcd_<ms>.bmp` (créer `work/` d'abord).
Résultat : WAV non muet (97 % d'échantillons non nuls, 0 clip hors transitoires), LCD actif,
pages zuluTek rendues (vérifié visuellement sur les snapshots).
