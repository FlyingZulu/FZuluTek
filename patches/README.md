# zuluTek — patches firmware ATEK303 (M-VAVE FM-1)

Série de 4 patches `git am` à appliquer sur `animatek/FM1-Animatek` (base : HEAD main de `animatek/FM1-Animatek`
(les patches 0001-0004 s'appliquent sur main HEAD — vérifié par `git apply --check` ; la release `atek303-v1.2` ne matche pas)), dossier cible `atek303/app/dsp/`. Ils produisent le **moteur zuluTek**
identique à celui embarqué dans l'APK preview (`app-zulutek/src/main/cpp/dsp/`, copiés
tels quels) — « un même moteur des deux côtés » (docs/ZULUTEK.md).

```
git clone https://github.com/animatek/FM1-Animatek && cd FM1-Animatek
git checkout atek303-v1.2          # ou le commit de base de la release 1.2
git apply patches/000*.patch        # dans l'ordre 0001 -> 0005 (format git diff, pas git am)
./herramientas/clonar-terceros.sh # fm1-base (MPL-2.0) avant de compiler
atek303/build.sh
```

| Patch | Contenu |
|---|---|
| `0001-zulutek-voice-AT_ENGINE-17-waves.patch` | voix ATEK303 : `AT_ENGINE` (sélecteur de moteur, id ajouté en fin d'enum), 17 ondes (GRIT/SUB2/BUZZ), fix coefficient AA_SOS[2] |
| `0002-zulutek-fx-MOJO-BBD-BlackHole-PoW.patch` | chaîne FX : DRIVE MOJO (soft-clip airwindows, MIT — `LICENSE-airwindows.txt` ajouté) + BIT/HOLD, delay BBD libre, reverb Black Hole, anneau PoW! 8192 éch. (15 types punch-in) + `fx_punch_off_tail` |
| `0003-zulutek-bass-engines.patch` | nouveaux `bass_engines.c/h` : PLUK/FM/SUB/8BIT (ports ZuluFx), pilotés par `AT_ENGINE` |
| `0004-zulutek-midi-looper.patch` | nouveaux `midi_looper.c/h` : looper MIDI v2 (16 slots, clock partagé, resample ring µ-law) — design dans `docs/ZULUTEK-MIDI-LOOPER.md` |
| `0005-zulutek-seq-plus.patch` (branche `vibe/fm1-patch-seqplus-c9ef18`) | **pack SEQ+** : module `ztek_seqplus.c/h` + hooks `app.c`/`app.h`/`fm1.toml` — double-tap SEQ = SEQ+ (TM LOCK/LEN, MUT, PLAY NORM/REV/PING-PONG, RAND, Turing Machine au wrap), pages LFO(8)/ENV(9)/ASSIGN(10) = modulation 8/8 (4 LFO + 4 labos, ENV1/2 bouclables + 6 tools) + matrice ASSIGN typée out→in, `EV_MUTATE` (déjà câblé ATEK303) avec burst = MUT, option **LUDIQUE** (OPTIONS, OPT3), état SEQ+ en flash (OBJ_ZSEQ @ 0xDF000), tag partie (BASSE/LEAD/DRUMS) sur les 32 presets. Hors scope FM-1 : pad XY tactile, SMP audio-in, veille écran (spécifique APK). |

Notes :
- `fastmath.h` du dépôt (shim host -> libm) est **APK uniquement** ; sur le FM-1, c'est le
  `fastmath.h` de fm1-base (approx. rapides) qui est utilisé.
- Le patch de surface FM-1 (`atek303/app/app.c` : pages LOOP/SMP/XY/KIT, PoW!, SNAP, MIDI
  CC 74..83/20..37/102..115) vit dans l'arbre firmware privé (fm1-Zulu) et arrive en PR
  séparée sur ce dépôt.
- Vérifié : `git apply --check` des 5 patches sur un clone de `animatek/FM1-Animatek` (main HEAD) : OK. `ztek_seqplus.c` compilé en C99 host (`gcc -fsyntax-only -Wall`) : OK. **Reste** : build complet avec la toolchain JieLi + test dans l'émulateur fm1-emu (`fm1-live --fwsc atek303.fwsc`, voir `docs/FM1-EMULATEUR.md`) avant tout flash.
- Licences : ATEK303 GPL-3.0-or-later (Javier/Animatek), Open303 MIT, airwindows MIT,
  bass_engines + midi_looper GPL-3.0 (FlyingZulu). Distribution => GPL-3.0 + crédits.
