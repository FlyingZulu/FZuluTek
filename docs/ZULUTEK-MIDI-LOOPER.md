# zuluTek — le sampleur / looper MIDI (FM-1 + APK)

Branche `vibe/zulutek-midi-looper-c9ef18` (PR #6, draft). Inspiré du looper MIDI de
ZuluFx (`fm1-Zulu`, `MainActivity.kt:713-766, 2827-2849, 4252-4330, 6325+`, grille
`MidiSeqView` 6×16 `ZuluControls.kt:3791`) et du looper du **Kaoss Pad 3** (longueur en
beats quantizee a l'arret, tap pad = play/stop, overdub). Adapte a la surface FM-1
(27 touches, 14 boutons, 7 encodeurs SELECT/PRESET/ALGO/K1-K4).

## Structure

- **1 banque, 16 slots** = les 16 touches blanches. Horloge unique synchrone sur
  PLAY + tempo SEQ (`ml_play`/`ml_set_bpm`), evenements en 1/64 de mesure.
- `sizeof(ml_t)` ≈ 48 Ko (dont 32 Ko de ring resample `ML_RS_MAX 16384`, auto-dimensionne
  selon la RAM libre : 16K / 8K / 4K). Sur le FM-1 (`.pool`, pas de RAM externe) le patch
  firmware taillera le ring dans la `.pool` restante.

## Manipulations

### Page LOOP — `REC+EDIT`
Ouvre la page LOOP, qui reste ouverte jusqu'a une autre page ou `REC+EDIT` a nouveau.
Grille 4×4 des 16 slots sur le LCD (plein = joue, clignotant = REC, point = rempli,
cadre = selectionne) + barre de playhead.

- **tap blanche** = play/stop du slot
- **appui long** (0,8 s) = page d'edition du slot (SLOT)
- **touche 25 + blanche 1..8** = longueur predefinie : 1, 2, 4, 8, 16, 32, 64, 128
  temps (1 beat … 32 bars) — `ml_resize` : retrécir = tronqué, allonger = la boucle
  initiale est clonée
- **touche 25 (maintenue) + SELECT** = longueur fine 0..128 pas (1 pas = 1 temps)
- **GLO (touche noire 15) + pad** = procédure RESAMPLE (voir plus bas)

### Procédure REC — `REC+GLO`, a tout moment
1. `REC+GLO` → flash 120 ms + **icône caméra de cinéma** (qui rétrécit ensuite, comme
   l'appareil photo des snaps) + « LOOP REC: PAD? »
2. tap blanche = slot de destination (sélectionné)
3. longueur optionnelle : touche 25 + blanche (presets) ou 25 + SELECT (0..128 pas)
4. si aucun réglage de longueur dans les **1,2 s** après le pad → **armé** : retour à la
   page précédente, navigation libre ; **REC+GLO clignotent** + **logo REC rouge**
   clignotant en haut à droite du LCD (règle générale : les boutons d'une procédure armée
   clignotent jusqu'a sa fermeture — idem PoW! REC+FX, SNAP REC+SEL, capture REC+PLAY,
   ASSIGN ENV+LFO / REC+LFO, SIREN triple-tap GLO)
5. `REC+GLO` quand prêt → **départ immédiat** (aucun délai) ; sur un slot en lecture =
   **overdub** (superposition, longueur conservée) ; sinon le slot est vidé
6. re-tap `REC+GLO` ou de la blanche → stop : longueur prédéfinie conservée (événements
   au-delà tronqués), sinon **auto-quantizée au tempo** (1,2,4…128 beats) → boucle
   parfaite (événements rebasés sur l'horloge maître au début du REC)

Enregistrement : notes (clavier/MIDI in, vélocité + durée via note-off) + mouvements
K1–K4/WAVE/ENGINE en automation (clés 0..7), + sirène (clés 32+wave, 33..36 params).
Filtre de source : une note n'est enregistrée que si la **source du slot** (ALGO)
correspond (BASS/SYNTH/DRUMS) ou ALL.

### Page SLOT (édition globale du slot, appui long sur la blanche)
| Encodeur | Fonction |
|---|---|
| **ALGO** | **SRC** : BASS / SYNTH / DRUMS / ALL / RESAMPLE ; **touche 25 maintenue + ALGO** = flag **+FX** (les effets PoW! de la destination sont inclus dans la boucle ; sinon flow post-FX) |
| **SELECT** | **LEN** 0..128 pas : rétrécir = tronqué, allonger = cloné |
| **PRESET** | **NIVEAU** (volume du slot) |
| K1 | **PITCH** ±12 st |
| K2 | **POS** : point de départ, avant/arrière |
| K3 | **MODE** : NORMAL / REVERSE / PING-PONG |
| K4 | **MUTATION** : proba de mutation (vel/params ±) à chaque passage |
| touche 25 long (1,2 s) | efface le slot |
| touche 25 + pad | page STEP (édition profonde de ce slot) |

Icône **micro barré** (rouge, clignotant) si la source choisie a son **auxiliaire à 0**
(page MIX) — information seule : la piste est quand même enregistrée.

### Page STEP (édition profonde, touche 25 + pad)
Les **16 blanches = les 16 pas** de la grille du slot (équivalent des 16 colonnes de la
`MidiSeqView` de ZuluFx) ; LCD = grille 16×4 (64 pas) avec playhead inversé.
**tap** = sélectionne/active le pas (`ml_step_toggle`), **SELECT = vélocité du pas**
(`ml_step_vel`). EDIT = retour à la page SLOT.

### RESAMPLE (reloop) — GLO (touche noire 15) + pad
Fenêtre de 2 s (optionnelle) : tap les **pads sources** à monitorer (ils clignotent) ; **REC+GLO pendant la
fenêtre = lancement immédiat** avec le masque retenu → `REC+GLO`
lance l'enregistrement de la somme des slots monitorés (bus post-FX) dans le slot de
destination, en **ring audio auto-dimensionné à la RAM libre** (16384/8192/4096 éch.,
~372/186/93 ms @ 44,1 kHz). Header « RESAMPLE » + logo REC. re-tap `REC+GLO` = stop,
longueur quantizée. `REC + double-tap GLO` reste libre.

## Page MIX — `SEL`
SELECT = sous-page : **LV** (K1..K4 = niveaux BASS/SYNTH/DRUMS/LOOP) · **>LOOP**
(PRESET = aux BASS, ALGO = aux SYNTH, K1 = aux DRUMS → looper) · **>POW!** (idem vers le
bus PoW!). Les aux ne filtrent PAS l'enregistrement (info micro barré seule) ; ils
règlent le niveau envoyé vers chaque bus.

## PoW! — source par slot
En page PUNCH, **ALGO = source du slot PoW!** (BASS/SYNTH/DRUMS/ALL), affiché dans le
header ; micro barré si son aux → POW! est à 0.

## Mode SIREN — triple-tap GLO
À tout moment : **triple-tap GLO** = mode SIREN (GLO clignote, header « SIREN », quitte
par tout changement de page). **GLO maintenu + touche noire** (PIT/GLO/MONO/POLY) =
sélection, **tap GLO** = confirme ; le clavier joue normalement avec la sirène active ;
**K1..K4 = RATE/DEPTH/DELAY/MIX**, **ALGO = presets de sirène** (4). Les sirènes restent
accessibles en momentané sur les 4 noires hors mode SIREN, et dans PoW!/SNAP/LOOP.
La sirène est **capturée dans les SNAP** (`zt_settings_t.siren_wave/siren_p`) et
enregistrée dans les **loops MIDI** (clés d'automation 32..36). **OPTIONS ▸ SIREN>POW!**
= la sirène passe (ON, défaut, pre-FX) ou non (OFF, post-FX) dans la chaîne PoW!.

## Entrée MIDI externe (Monkey by Wavy) — sources, canaux, modes

- **Sources de loop** : ALGO en page SLOT/STEP = BASS/SYNTH/DRUMS/ALL/RS/IN/ALL+IN
  (`sp.src` 0..6 ; 5 = entrée MIDI externe, 6 = tout + MIDI-in). **REC+GLO+OCT±** choisit la source :
  **OCT+ = externe** (src IN), **OCT- = interne** (src ALL, parties audio), **les deux = ALL+IN**
  (les OCT concernés clignotent avec REC+GLO pendant la procédure).
- **REC+PLAY+OCT±** arme une capture de SNAP avec contenu choisi : **OCT+ = externe** (notes MIDI
  tenues + derniers CC reçus, rejoués au rappel : gate + MIDI-out), **OCT- = interne** (niveau
  audio-in AIN de l'APK), **les deux = les deux**. La capture reste **REC+PLAY + pad** comme
  d'habitude (REC/PLAY et les OCT concernés clignotent).
  **Le choix OCT est mémorisé, et séparément pour les deux procédures** : celui des SNAP
  (REC+PLAY+OCT±) et celui des MIDI loops (REC+GLO+OCT±) sont deux réglages distincts — si la
  procédure suivante se fait sans OCT, c'est le dernier choix de CETTE procédure qui s'applique.
  Même principe « dernier choix retenu » pour le **RESAMPLE** (GLO+pad) : si on ne retape pas de
  pads source dans les 2 s, c'est le **dernier masque de monitoring** qui est réutilisé (tap sur
  le pad destination pendant la fenêtre = clear du masque).
- **Canaux dédiés** (le Monkey envoie sur les canaux 1..10) : **1 = BASS**, **2 = LEAD**,
  **3 et 10 = DRUMS** (10 = le canal drum du Monkey), **4 = PoW!** (note = slot PoW!),
  **5 = SNAP** (note = pad snap), **6 = LOOP** (note = pad loop). Tout autre canal = mode
  **global** (voir ci-dessous). **CC1 (la lune du Monkey) = CUTOFF du filtre** (modes synth,
  basse et drum ; côté firmware drum : stutter PoW! « 1/16 REP »).
- **DRUM KB** (OPTIONS) : sur le canal drum, **NOTE** = chromatique (rentrer des séquences à la
  volée), **FINGER** = les 8 touches noires du Monkey (2 octaves depuis F) = les 8 pads drum
  (C2..G2).
- **MIDI-out** : la **lecture des loops** et le **replay MIDI des snaps** partent en MIDI-out
  (pas de réecho de l'entrée). CC reçus renvoyés en out (thru CC). OPTIONS ▸ MIDI OUT = on/off.
- **Modes Monkey** : deux modes, **FREE** (clavier actif partout) et **LOCK** (verrouillé sur une
  page). Sur l'APK : bouton **KB** dans la barre de statut (tap = lock page en cours, re-tap =
  recapture, double tap = free ; une couleur par état : gris déconnecté, turquoise free, ambre
  lock + n° de page, magenta si loop détecté). Sur le **FM-1 : EDIT+GLO** (tap = lock page en
  cours, double tap = free). Si le Monkey est en mode loop (plusieurs canaux simultanés) : logo
  🔁 sur le LCD et passage auto en FREE. Si LOCK : **cadenas + n° de page** en haut à gauche
  du LCD, et **HOME clignote toutes les 20 s**. L'animation **singe au piano sous la lune**
  apparaît à la connexion d'un Monkey (détection par nom) — OPTIONS ▸ **MONKEY** = on/off.
  OPTIONS ▸ **CLAV** (APK) : PLEIN / MINI / AUTO — en MINI ou AUTO+Monkey connecté, le clavier
  écran perd ~1/4 de sa hauteur et l'écran grandit en proportion.
- **Audio-in (APK)** : entrée micro = partie **AIN** du MIX (sous-page « AIN », K1 = niveau ;
  aux AIN → LOOP/POW! en sous-pages 1/2), mixée post-FX donc capturée par le resample ; son
  niveau est inclus dans les snaps MIDI-in.

### Ring resample : formats (FM-1 allégé)
`ml_rs_start(dst, monmask, cap, fmt, khz)` : **fmt 0** = int16 ; **fmt 1** = µ-law 8 bits (companding
style SP-1200, moitié moins de RAM → cap 2×ML_RS_MAX) ; **fmt 2** = µ-law + **paliers lo-fi par durée
(style ZuluFx) : 0–7 s = 14 bits (léger), 7–14 s = 11 bits, 14 s+ = 8 bits + soft-clip (fort)**.
**khz** = fréquence du ring (0 = natif ; **32 = ring 32 kHz**, ~1,38× moins de charge et de RAM, la
boucle tient ~1 s en 32 Ko). Le « réglage qualité » type AirWindows = le soft-clip MOJO de DRIVE
(`fx.c`, déjà embarqué). APK : fmt 0 natif ; patch FM-1 : fmt 2 + 32 kHz. Longueur max du loop audio :
cap du ring + quantize au tempo à l'arrêt (1/64 de mesure).

## Fichiers

- `app-zulutek/src/main/cpp/dsp/midi_looper.{c,h}` — cœur C99 portable (pas de libm) :
  record/quantize/rebase, resize clone/tronqué, per-slot sp, src filter, modes
  normal/reverse/ping-pong, overdub, resample ring, callbacks note/param avec slot.
- `ztek_engine.{h,cpp}` + `jni_bridge.cpp` + `AudioEngine.kt` — intégration (EV_ML_*,
  EV_MIX_*, EV_PUNCH_SRC, EV_SIREN_*, routing siren pre/post-FX, gains par partie).
- `MainActivity.kt` / `LcdView.kt` / `KeyboardView.kt` — pages LOOP/SLOT/STEP/MIX,
  procédures REC+GLO / GLO+pad, mode SIREN, grilles LCD, icônes (caméra cinéma, logo REC
  rouge, micro barré), points ZU/LU fluo, clignotement des procédures armées.
- `patches/atek303-midi-looper.patch` pour `animatek/FM1-Animatek` (à faire).

## Vérification

- Test hôte `gcc -DFX_HOST … midi_looper.c` : rec/quantize, rebase, filtre source,
  resize clone/tronqué, modes reverse/ping-pong, resample → RESULT: OK.
- `g++ -fsyntax-only` (stubs oboe/jni) sur `ztek_engine.cpp jni_bridge.cpp` → OK.
- CI `assembleDebug` vert sur chaque commit de la branche.

- **Page MACRO** (REC+SEQ, ou EDIT depuis MIX) — concept simplifié : **K1..K4 = 4 knobs**, chacun mappé à
  un des **16 slots d'effets** (les 16 touches blanches = les 16 types PoW! de ZuluFx, légers et portables FM-1).
  **OP1..OP4** = sélectionner le knob K1..K4, puis une **blanche** = affecter ce slot au knob.
  **OP5** = dictateur principal : une blanche = un des **16 comportements** (BEH_NAMES, 1ère = LIN, mode
  linéaire façon macro ZuluFx). **Double-tap OPi** = comportement du knob i (16 blanches, 1ère = LIN).
  **SELECT = type d'effet du slot sélectionné**, **ALGO** = slot affecté au knob, **PRESET** = on/off.
  **Maintenir OPi + maintenir une blanche** = ouvre le menu de l'effet (page PUNCH, K1..K4 affinent).
  K_i pilote le mix de son slot via la courbe de comportement. Tap blanche seule = PoW! momentané.
- **Warning « cocktail »** (page MACRO) : si la somme des coûts DSP des 4 effets assignés aux knobs
  seuil dynamique : dspPct (charge reelle en cours) + 8 x cout des 4 effets > 90, un **verre à cocktail barré** clignote en haut à droite du LCD.
