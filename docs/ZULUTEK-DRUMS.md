# zuluTek DRUMS — SPEC v2 (module `app-zulutek-drums`, branche `vibe/zulutek-drum-analog-c9ef18`)

> **Archivé (PR #19, 2026-11-20)** : module plus buildé, dédoublonné avec la **page DRUMS 15 intégrée
> à `app-zulutek`** (parité firmware FM-1, patch 0007). Ce fichier reste la spec d'origine du module
> standalone.

> ⚠️ **Anti-perte** : l'ancien code de la branche drum (PR #5 de `fm1-Zulu`) et `tr80x.hpp`
> de ZuluFx sont **définitivement perdus** (dépôt supprimé 404, aucun objet git retrouvé).
> Ce document + la page Notion « 🛠️ SPEC Drum Engine zuluTek » sont la spec écrite : si le code
> saute encore, on reconstruit from here. **À mettre à jour à chaque changement.**
>
> APK : `ZuluTekDrums-*.apk`. Moteur C99 autonome + moteurs basse/lead réutilisés (vendored).

## 0. Référence design : Erica Synths PĒRKONS HD-01 (fiche AudioFanzine, lien utilisateur)

Notre cible : une mini-PĒRKONS logicielle.
- **4 voix hybrides** chez Erica (moteur numérique + filtre multimode analogique + overdrive)
  → chez nous : **8 pads, chaque pad = moteur custom + moteur basse/lead, crossfade HYB**.
- 8 commandes par voix → chez nous : **4 params shapables (P0..P3) + HYB + LEVEL + MUT + MODE**.
- Séquenceur 4 pistes, 16 pas : sens NORM/REV/PING-PONG/RANDOM, groove/shuffle (= SWING),
  **probabilités par pas (10/25/50/90%) et ODDS1 (1/2,1/3,1/4,1/8) / ODDS2** → chez nous :
  **COND = 1:1, 1:2, 1:3, 1:4, 1:8, FILL** (ODDS1/FILL) + proba via MUT, slide micro-timing.
- **P-LOCKS** (valeur de potard fixe par step) → snapshot des 4 params dans la voix au hit
  (`dr_voice_t.par`), façon Elektron.
- 64 kits / 64 patterns → chez nous : **kits ZULU / 808 / 909** (params par défaut des pads) +
  patterns par pad éditables, RAND.
- Delay BBD, LFO morphable, MIDI → plus tard (FX page PATCH côté app-zulutek, ZuluLink).

## 1. Philosophie (demandes Flying Zulu, 2026-11-20)

- Drums **analogiques inspirés Elektron/PĒRKONS** (punch, drive, hybridation), pas de samples.
- **Kick/snare style Mutable Instruments** (Braids/Elements, GPL-3.0, crédits Émilie Gillet) :
  kick = sinus + pitch env exp + drive tanh + click ; snare = bruit + corps sinus + drive.
- **Hybridation entre tous les modèles** : chaque pad somme le moteur custom ET un moteur
  basse/lead existant (`bass_engines.c` vendored depuis app-zulutek : KICK→SUB, SNARE/HAT/CLAP/
  COWBELL→8BIT, TOM/PERC→PLUK Karplus-Strong, FM→FM), mix réglable **HYB 0..1** par pad.
- Piocher dans ZuluFx : `audio_engine.cpp` case 5 (kick glide+tanh+click), case 6 (snare
  bruit/corps), case 7 (hat 6 carrés 263/400/421/474/587/845 Hz + HP + bright).
- **Kits ZULU/808/909** (sélecteur façon ZuluFx) : 808 = kick grave long decay drop profond,
  snare sec ; 909 = kick aigu click+drive, snare dense, hat métal. Table `KITPAR[3][8][4]`.
- Séquenceur **inspiré du séquenceur MIDI loop** (`dsp/midi_looper.c`) : horloge `clk64` en
  1/64 de mesure (1 mesure 4/4 = 64, 1 step = 4), steps `{on, vel 0..127, cond, slide int8}`,
  modes NORM/REV/PING-PONG par pad, swing (steps impairs), REC live overdub, quantize.
- Licences : jamais de décompilation de binaires proprio ; GPL/MIT OK avec crédits.

## 2. Les 8 pads × 4 params (+ HYB, LEVEL, MUT, MODE)

| pad | custom | bass engine (HYB) | P0 | P1 | P2 | P3 |
|---|---|---|---|---|---|---|
| 0 KICK | Mutable sinus+pitch env+drive+click | SUB | TUNE 40–200 Hz | DECAY | DROP | DRIVE |
| 1 SNARE | Mutable bruit+corps+drive | 8BIT | TONE 150–400 Hz | DECAY | NOISE mix | DRIVE |
| 2 HAT | ZuluFx 6-carrés+HP | 8BIT | BRIGHT | DECAY 0.02–0.4 | METAL | — |
| 3 CLAP | 3 bursts bruit HP | 8BIT | — | DECAY | BURST 1..3 | SPC |
| 4 TOM | sinus pitch env | PLUK | TUNE | DECAY | DROP | — |
| 5 PERC | modal (sinus + harmonique ~3.9×) | PLUK | TUNE | DECAY | INHARM | NOISE |
| 6 COWBELL | 2 carrés 845/560 + bruit | 8BIT | DETUNE | DECAY | — | — |
| 7 FM | Braids FM drum (porteuse+modulateur+fb) | FM | RATIO | DECAY | INDEX | FEEDBK |

## 3. Séquenceur (clone du MIDI loop)

- `clk64` master en 1/64, avance au BPM ; 16 steps/mesure ; **step 0 joue dès le départ**
  (`step` init à -1).
- Steps `dr_step_t {on, vel, cond, slide}` ; **COND** = ALWAYS/1:2/1:3/1:4/1:8/FILL (FILL =
  joue seulement si un autre pad est actif sur ce step) ; **slide** -8..+8 en 1/64.
- Modes par pad NORM/REV/PING-PONG ; **MUT** 0..255 (re-roll vel au passage) ; **swing** 0..50 %.
- **REC live** : hit manuel enregistré à la position courante (overdub).
- Patterns défaut : kick 0,4,8,12 ; snare 4,12 ; hat pairs ; clap 12 ; perc 6,14.
- `trig_count[8]` : compteur de triggers (tests/UI).

## 4. Fichiers

- `app-zulutek-drums/src/main/cpp/drum_engine.{h,c}` — moteur C99 (inclut `bass_engines.h`).
- `jni_bridge.cpp` — Oboe prefab, mono→stéréo 256 ; natives DrumsAudio (hit, setStep/getStep
  packé en jlong, setParam/getParam, setHyb, setKit, setPadMode/Mut, setLevel/Bpm/Swing/Master,
  play/rec, getCurStep/getClk64, clear/rand/reset).
- `CMakeLists.txt` — compile aussi `bass_engines.c` + `atek_voice.c` vendored depuis
  `app-zulutek/src/main/cpp/dsp` (`-DFX_HOST`).
- Kotlin : `DrumsAudio.kt`, `DrumGridView.kt` (steps vélocité/cond/slide + pads 4×4),
  `DrumsActivity.kt` (PLAY/REC/STEP, TEMPO, SWING, KIT, P0..P3, HYB, LVL, COND, MODE, MUT).
- `settings.gradle` inclut le module ; CI build + artefact ; icône tambour en CI
  (`tools/make_icon.py`), res gitignorées ; signature status quo.

## 5. Vérifs host (faits 2026-11-20, RC=0)

- `gcc -std=c99 -Wall -Wextra -fsyntax-only -I. -Idsp -DFX_HOST drum_engine.c` + test :
  seq avance (clk64), step 0 au départ, **COND 1:2 = 31 triggers/4 mesures (attendu 32)**,
  1:1 = 63 (64), **FILL = 2 (step avec kick oui, step seul non)**, REC live enregistre,
  kits changent les params (kick TUNE 0.45/0.25/0.58), **hybridation hyb0≠hyb1 sur les 8 pads**.
- `g++ -std=c++17 -fsyntax-only` bridge (stubs jni/oboe) → RC=0.
- CI `gradle assembleDebug` → `ZuluTekDrums-debug.apk`.

## 5b. Mises à jour v2.1 (2026-11-20 ~03:20Z, demandes Flying Zulu)

- **P0 = PITCH sur les 8 voies** (règle 808/909/PĒRKONS) : KICK 40–200 Hz, SNARE corps 150–400 Hz,
  HAT multiplieur des 6 carrés, CLAP fc du BP 400–3600 Hz (one-pole LP+HP), TOM 80–300 Hz,
  PERC 200–1000 Hz, COWBELL base 845 Hz ×2^((P0−.5)·2), FM porteuse 60–400 Hz (RATIO en P2,
  INDEX en P3). Test host : KICK 140→700 Hz, TOM 155→590 Hz entre P0=0 et P0=1 ✓.
- **8 knobs par voie, 2 pages de 4** (façon 8 commandes/voix PĒRKONS) : page 1 = P0..P3 (custom),
  page 2 = P4 HYB, P5 B.WAVE, P6 B.RES (moteur basse/lead), P7 LEVEL. UI APK : bouton PAGE 1/2.
  P-locks : les 8 knobs snapshotés dans la voix au hit (`dr_voice_t.par[8]`).
- **Compresseur de bus inspiré AUV3 Fac Smash** (et section compression PĒRKONS) : follower de
  crête linéaire (atk ~3 éch, rel ~8 ms, cheap FM-1 sans powf), soft-knee, THR/RATIO/MAKEUP ;
  **ratio 20 = SMASH radical** (limiteur dur au seuil + tanh de saturation). Mesuré host :
  OFF peak 1.43/RMS .391 → ratio 4 peak .51 → ratio 20 peak .21 → SMASH thr .15 mk 2 peak .18/RMS .077.
- **Layout FM-1 (proposé, en discussion)** : 16 blanches = 16 steps, 8 noires = 8 pads,
  K1–K4 = 4 knobs page courante, PRESET = page 1/2, SELECT = pad/step, ALG = swing,
  PLAY = transport, REC = REC live, ENV = COND, LFO = MODE, EDIT = kit ZULU/808/909,
  SAVE = comp ON/OFF (page COMP : THR/RATIO/MAKEUP), GLO = HYB, ARP = CLEAR, SEQ = RAND/RESET.
- **Budget DSP FM-1** : mesuré host 0,29 µs/éch pour 8 voix custom (pire cas) ; astuces ECO :
  hyb=0 par défaut, bass engine à demi-rate, -Os, comp linéaire ; bass_engines.c/atek_voice.c
  déjà en flash (basse/lead) → +~7 Ko seulement ; RAM 8 bass_t = 73 Ko → 2 bass_t partagés en ECO.

## 5c. Mises à jour v2.2 (2026-11-20 ~03:35Z)

- **12 knobs par voie = 3 pages de 4** (demande Flying Zulu) : page 3 = **P8 PAN, P9 SEND DELAY,
  P10 SEND REVERB, P11 SEND COMP** (contribution au détecteur du compresseur, ducking ciblé).
  Le render passe en **stéréo** (`drum_render(d, outL, outR, n)`, pan law cos/sin par voie).
- **Delay bus partagé** (ring `DR_DLY_MAX` = 16384 éch ≈ 0,34 s ; 8192 en `DR_FM1_ECO`), params
  globaux time/fb/wet ; send par voie en P9. Test host : écho OK à 0,24–0,34 s.
- **Reverb courte et économe** : 2 allpass Schroeder en série (211 + 331 éch ≈ 12 ms, g=0,5),
  ~2 Ko de RAM, wet global ; send par voie en P10. Test host : queue diffuse OK.
- **Pad ELIXIR** (ex-PERC) : 5 sous-sons légers/aériens via **P2 = TYPE** : BELL (partiel 2,76×),
  CHIME (1×+3,01×), KALIMBA (1×+4× + attaque), GLASS (bruit HP cristallin + sinus 8×),
  PING (FM aigu mod 3,5×). Test host : les 5 rendent, P0=PITCH 200–1000 Hz.
- Tests host v2.2 RC=0 : PAN gauche/droite OK (eL 51.7/eR 0 et inverse), DELAY OK, REVERB OK,
  ELIXIR 5/5 OK, COND 1:2 régression 31/32, compile C99 -Wall OK, bridge stubs OK.

## 5d. Mises à jour v2.4 (2026-11-22, patch 0023, demandes Flying Zulu)

- **FM-1 passe à 6 pads : CLAP et COWBELL sautent** (choix Flying Zulu). Pads FM-1 :
  KICK, SNARE, HAT, TOM, ELIXIR, FM. **L'APK garde ses 8 pads inchangés**
  (KICK SNARE HAT HAT-O CLAP TOM CONGA COWBELL).
- **Divergence de listes documentée** : les 8 lignes FM-1 d'origine étaient
  KICK SNARE HAT CLAP TOM ELIXIR COWBELL FM, l'APK a sa propre lignée
  (deux drum_engine jamais synchronisés depuis le portage). C'est préexistant ;
  le remap ci-dessous neutralise, APK non touché.
- **Garde DR_NPADS** : `drum_engine.h` définit `DR_NPADS 8` sous
  `#ifndef DR_NPADS` ; les unités FM-1 (`ztek_drum.c`, `ztek_drum_fm1.c` posent
  `#define DR_NPADS 6` avant l'inclusion, les DEUX unités, sinon .bss doublé et
  offsets drum_t divergents). Enum adaptatif : KICK=0, SNARE=1, HAT=2, TOM=3,
  ELIXIR=4, FM=5 en 6 pads ; code CLAP/COWBELL sous `#if DR_NPADS == 8`.
- **Tables canoniques inchangées** (flash) : KITPAR/names restent à 8 lignes
  (`DR_TABPADS 8`) ; remap `DR_TAB()` = {0,1,2,4,5,7} (lignes CLAP/COWBELL
  ignorées sur FM-1).
- **Blob ZKT1 à taille fixe 8** (`DR_ZKT1_NPADS`) : export = kits usine
  canoniques 8 lignes, import clampé aux pads montés. Contrat APK intact
  (`zlk_ztp1_fits`, ZKIT00..02), `dr_zkt1_t` (par[8] fixe) taille constante.
- **ONE-SHOT séquenceur drums** : `drum_set_oneshot`/`drum_get_oneshot`.
  Fin = **dernier step relevé** : à la fin du pattern les notes/coupes sont
  relevées (pas de bouclage), position figée, DRUM FIN relayé ; PLAY repart du
  step 0.
- **ONE-SHOT looper MIDI** : `ml_set_oneshot`/`ml_get_oneshot`. Les 5 sens
  (NORM/REV/PING-PONG x2/RANDOM) arment un rebond vers l'arrêt à leur wrap
  final ; `ml_oneshot_stop` relève les notes actives via note_off.
- **UI FM-1** : SHIFT = dernière touche noire maintenue (mode d'emploi, hors
  mode clavier, 11 noires, index bi==10). **SHIFT+SEQ sur pages 11/12** =
  toggle one-shot (drums / looper).
- **APK** : toggle SHIFT+SEQ dans les pages drum et looper (JNI/Kotlin).
- **Routage looper (bug APK seulement)** : le looper MIDI ne doit enregistrer
  QUE les notes jouées en amont du séquenceur (clavier), pas celles générées
  par le séquenceur acid → `ztek_engine.c` `ztek_acid_step` passe record=1 à 0.
  Vérifié : ce bug n'existe PAS sur FM-1 (l'acid passe par at_gate, jamais par
  la file EV_NOTE_ON qui alimente zml_note_in).
- Validation : rejeu du patch 0023 sur l'arbre reconstruit post-0022 = arbres
  identiques octet à octet ; garde #if/#endif équilibrée ; aucune réf
  CLAP/COWBELL active en 6 pads ; build réel CI/toolchain JieLi.

## 6. À faire ensuite (PAS pushé sans aval utilisateur)

- Push branche `vibe/zulutek-drum-analog-c9ef18` v2 + maj PR #5 (après aval).
- Porter sur FM-1 (patch 0006 : 27 touches = pads + steps) ; sortie drum dans baie PATCH (page 12).
- RATCHETS par pas (x2..x5 façon PĒRKONS), polyrythmie (longueur de pattern par pad 1..64),
  pattern chain, LFO morphable, delay BBD (fx.c de app-zulutek).
