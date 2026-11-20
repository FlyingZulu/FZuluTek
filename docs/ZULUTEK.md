# zuluTek — la bible

zuluTek (clin d'œil au créateur original) est une **version alternative et personnalisée du
firmware ATEK303** d'Animatek (Javier) pour le M-VAVE FM-1. ZuluFx n'est ici qu'une
bibliothèque de référence : on y pioche des effets, des moteurs et des idées d'interface.

Deux livrables, un même moteur :

1. **Le firmware FM-1** — 4 patches `git am` sur `animatek/FM1-Animatek` (PR #4 de ce dépôt,
   branche `vibe/atek303-mojo-patch-c9ef18`, dossier `patches/`) : build puis flash sur le FM-1.
2. **L'appli de prévisualisation smartphone** (`app-zulutek`, branche `vibe/zulutek-apk-c9ef18`)
   : un clone de la surface du FM-1 (27 touches F3..G5, 14 boutons, 7 encodeurs SELECT/PRESET/ALGO/K1..K4,
   écran LCD noir et blanc avec flash/icône caméra et léger effet VHS) qui embarque **les mêmes
   fichiers DSP** (`app-zulutek/src/main/cpp/dsp/`, copiés tels quels de l'arbre patché), pour
   tester le rendu avant de porter/flâsher sur le FM-1.

## La chaîne audio (identique des deux côtés)

```
notes/clavier ──► voix (AT_ENGINE: 0 ATEK303 · 1 PLUK · 2 FM · 3 SUB · 4 8BIT)
                ──► FX: DRIVE (OFF/OVERDRIVE/RAT/FUZZ/MOJO[soft-clip airwindows + BIT/HOLD])
                      → MOD (CHORUS/FLANGER) → DELAY BBD libre 10..600 ms
                      → REVERB Black Hole (decay 0.995, mix en smoothstep)
                      → anneau PoW! 8192 éch. (15 types punch-in, en fin de chaîne)
                ──► sortie (limiteur doux)
```

- **Séquenceur acid** (acid_pattern) : PLAY lance, SEQ génère un nouveau seed, page PATT
  (DENSITY/RANGE/ACCENTS/SLIDES) regénère depuis le seed, page SEQ (STEPS/SCALE/TEMPO/ROOT).
- **Undo/redo** : REC double tap = undo (3 niveaux), REC appui long 1.2 s = redo, puis chaque
  tap remonte d'un niveau (fenêtre 3 s).
- **PoW!** (REC+FX) : les 16 touches blanches = 16 slots punch-in momentanés (15 types :
  REPEAT, 1/16 REP, 1/8 DOWN/UP/SHRINK, BUILD, STRETCH, ECHO, REVERSE, STOP, SCRATCH, LPF,
  CRUSH, GATE — ZuluFx + rolls Amenity). REC+touche = page PUNCH (K1 type, K2/K3 réglages —
  un potard libre devient DRY/WET —, K4 mix, SELECT le slot). Pop noir et blanc en header.
- **SNAP** (REC + double-tap FX) : 16 snapshots (voix + FX + knobs seq + PoW! enclenché + FLY),
  jamais les notes. Touche blanche = charger ; REC+touche = stocker. REC+PLAY = capture à tout
  moment (prioritaire : fonctionne même depuis SAVE/OPTIONS, et la capture gagne sur le tap de
  REC — pas d'undo) : flash blanc 120 ms + icône caméra, clavier inactif 1.5 s en attendant le
  pad (les slots pleins s'allument), au choix du pad l'icône rétrécit vers le fond en 0.8 s.
  Page SNAP : K1 fader ZU↔LU (8 gauches = ZU (K1 fermé), 8 droites = LU (K1 ouvert), un snap par côté, effet immédiat),
  K2/K3 libres (guardado solo con REC+PLAY → pad destino), K4 FLY (whammy ±12 st).
- **MIDI (FM-1)** : CC 74..79 voix, CC 80 engine, CC 81 fly, CC 20..37 knobs FX, CC 102..115
  REMOTE (14 boutons), OPTIONS > FX CH (notes F3..G5 = slots PoW!/SNAP), OPTIONS > LEARN
  (MIDI learn, en OBJ_OPTIONS OPT6).

## Différences plateforme (attendues)

| | FM-1 (firmware) | smartphone (APK) |
|---|---|---|
| sample rate | 44100 | 48000 (oboe) |
| maths | fastmath (approx.) | libm via `dsp/fastmath.h` |
| gros buffers | section `.pool` (SRAM) | tas (heap), `FX_HOST` |
| surface | vraie (app.c en C) | clone tactile Kotlin/C++ autour du même DSP |
| persistance | flash (OBJ_OPTIONS OPT6, presets 480×8) | en mémoire (preview) |

## Développement

On développe/test d'abord sur l'APK (`app-zulutek`), puis on porte sur le FM-1 via les patches
(PR #4). Build APK : CI « Build APK » de la branche `vibe/zulutek-apk-c9ef18` (artefact
`zulu-apk-<run>`), ou en local `gradle :app-zulutek:assembleDebug`.

## Fichiers

- `app-zulutek/` — l'appli preview (module Android à part ; `main` et ZuluFx inchangés).
- `patches/atek303-*.patch` (branche PR #4) — le firmware FM-1.
- `docs/ZULUTEK-MODE-D-EMPLOI.md` — le mode d'emploi.

## Le sampleur / Loop (branche looper)

Voir `docs/ZULUTEK-LOOP.md` pour le détail. En bref : **REC+EDIT** = page LOOP
(16 slots = les 16 blanches), **REC+GLO** = procédure REC (caméra cinéma, armement
1,2 s, REC+GLO clignotent + logo REC rouge, départ immédiat, stop = longueur prédéfinie
ou quantizee au tempo), appui long = édition du slot (ALGO = source, SELECT = longueur,
PRESET = niveau, K1 PITCH / K2 POS / K3 MODE rev-pingpong / K4 MUTATION), touche 25 + pad
= grille 16 pas (SELECT = vélocité), GLO(15) + pad = RESAMPLE (ring auto-RAM), page MIX
(SEL) = niveaux + aux -> LOOP / POW!, source PoW! par slot (ALGO en page PUNCH),
micro barré si aux à 0, mode SIREN (triple-tap GLO), option SIREN>POW!.

## Charte graphique

Voir `docs/ZULUTEK-UI-STYLE.md` (palette fluo rose/jaune/turquoise, pops 400 ms, témoins ZU/LU) — identique APK et firmware.

## ZuluTek (FM-1) ↔ APK preview — connexion et pipeline (philosophie ZuluLink, docs/ZULULINK.md)

- **Rôles** : le FM-1 (firmware zuluTek) est un **MODULE Zulu** (il rend le son, sort au jack) ;
  l'APK peut être **CONSOLE** (surface de pilotage + horloge) ou les deux. Découverte HELLO en
  broadcast UDP (port 53333), messages directs port 53334, paquets binaires < 200 o.
- **Contrôle/sync par le réseau local ou BLE MIDI, jamais d'audio par le réseau** (règle ZuluLink
  §0.2) : CLOCK (tempo + beat, console = chef), CC avec **déjà le vocabulaire du FM-1**
  (74..83 voix, 20..37 FX, 102..115 boutons, + LEARN/LEARN OUT des deux côtés), SNAP (état
  sérialisé `zt_settings_t` + slots de loops → l'APK sert de **librairie** de banques nommées),
  START/STOP ciblés et filtrés (SUIVRE START/STOP = OFF par défaut : le FM-1 ne s'arrête que
  localement), TAP, PING/PONG. Le looper de l'APK suit le tempo reçu (`ml_set_bpm`) et peut
  prendre un **ratio polyrythmique n:d** vs le master (ZuluLink §2b). Le Monkey en relais
  (ZuluLink M5) : contrôleur BT → APK (canaux 1-6/10) → CC vers le FM-1.
- **Audio : par le jack, pas par le Wi-Fi** — FM-1 line-out → entrée micro du téléphone (câble
  TRRS) = partie **AIN** du MIX de l'APK. L'APK peut alors **resampler / looper / passer en PoW!**
  la sortie du FM-1 (ring resample auto-RAM, loops MIDI src ALL+IN), et renvoyer en MIDI-out
  vers le FM-1 ce que ses loops rejouent.
- **Mode « satellite DSP »** : le FM-1 garde les voix + filtres + seq (léger, dans le budget du
  JieLi AC791N : pas de RAM externe, 2e cœur peu fiable) et **délègue à l'APK les traitements
  lourds** (reverb longue, resample 32K, looper multi-slots, mandalas LCD) via jack audio +
  clock ZuluLink. Chaîne : FM-1 (voix) → jack → APK AIN → [PoW!/resample/loops] → monitoring
  (casque/tel) ; le tempo vient du maître choisi (APK ou FM-1), les deux continuent au dernier
  tempo si le master se tait (règle v0.2).
- **Live d'abord** (PHILOSOPHIE.md) : ces liaisons ne bloquent jamais le jeu local du FM-1 ;
  chaque réglage de synchro est modifiable en live, en douceur, à la frontière de temps.

## Pages SMP (17) et XY (18) — APK (et patch FM-1)

- **Page SMP (17)** — sampleur de l'audio-in (APK ; sur FM-1 le buffer 4 s/48 kHz doit être taillé dans `.pool`) :
  PRESET = REC on/off de l'AIN, SELECT = mode de stretch (**BEAT** = snappé au tempo, **CLEAN** = resample,
  **OLD** = décimation x2 façon vieux sampler), K1..K4 = START / END / VOL / PITCH (−24..+24 st), ALGO = PLAY,
  une touche blanche = PLAY. LCD : waveform turquoise, marqueurs START/END jaunes, point REC rouge clignotant.
- **Page XY (18) — REC+ARP** : un seul pad XY (ZuluFx en avait deux axes séparés). SELECT = cible de X,
  PRESET = cible de Y, K1 = X, K2 = Y, K3/K4 = cibles, ALGO = swap X/Y, touche noire 25 (ou ARP seul sur la page)
  = **HOLD/MOM** : en HOLD le curseur reste (affaibli hors page), en MOM il est invisible sauf au toucher et
  revient au repos au relâché. **Une fois activé, le pad XY reste actif sur toutes les pages** (petit curseur
  estompé en haut au centre hors page 18). Sur l'écran de la page XY : pas de grille ni de bord, juste l'animation
  du curseur façon pad ZuluFx (halo concentrique turquoise/jaune + cœur blanc). Cibles : voix 0..7, FX 8..25,
  LFO 26..33, appliquées en absolu (0..1).
- **Circuit bend du pad XY** (inspiré du bend ZuluFx, SHIFT remplacé par REC) :
  - APK : **REC maintenu + toucher l'écran** = bend à la position du doigt (X = densité, Y = niveau).
  - FM-1 (pas d'écran tactile) : **SAVE maintenu + K1/K4** = bend (K1 = X, K4 = niveau). SAVE était le seul
    bouton libre (REC+ARP = page XY, REC+SEQ = MACRO).
  - Modes (OPTIONS > **BEND** on/off, **BEND M** = GLITCH/LOW BAT — mêmes options à prévoir sur le FM-1) :
    GLITCH = sample-hold aléatoire + bit-crush + trémolo ; LOW BAT = décimation + soft-clip. Le bend est appliqué
    **en post-master** (après filtre SNAP) avec une dérive aléatoire de la coupure du filtre master : il touche
    toutes les voix, les FX, les loops et l'audio-in, « comme une interférence dans les câbles ».
  - Affichage : bandes de glitch vidéo vintage (split RGB rose/jaune/turquoise/rouge) + roue de bend tournante
    (SweepGradient, façon `drawBendWheel` de ZuluFx) au curseur sur la page XY. BEND = OFF désactive tout
    (utile si un conflit apparaissait avec la liaison APK↔FM-1).

## Veille auto (Nyan Cat ghetto blaster) — APK + à porter sur FM-1

Si **rien ne joue** (seq/loop/PoW!/sirène/rec off) et qu'**aucun appui** (bouton, knob, touche, écran, MIDI) n'a lieu :

- **2 min 30** d'inactivité : le Nyan Cat ghetto blaster **traverse l'écran au milieu de l'intérieur du FM-1**
  (décor PCB pixel art `pcb_scene.png` : pistes cuivre, chip AC791N broché, câbles rose/jaune/turquoise/rouge,
  LED verte — il joue avec les câbles), traînée arc-en-ciel, ~7 s.
- **3 min** : spectacle de **30 s** (allers-retours + glitch), même décor.
- **3 min 30** : **écran éteint** (APK : noir ; FM-1 : rétroéclairage off) pour la batterie. En veille, toutes
  les **30 s** sa tête passe dans le coin bas-droite avec un **effet lampe torche** (le décor n'apparaît que
  dans le halo de lumière) : l'appareil est toujours allumé.
- **Tout appui** réveille : le Nyan **fait coucou** (pop au centre, même décor machine que la 1re phase) puis
  disparaît, et le compteur d'inactivité repart à zéro.
- **OPTIONS > VEILLE** ON/OFF (à prévoir aussi sur le FM-1) pour désactiver toute la veille.
- Le splash d'intro utilise le **même décor PCB** + le ghetto blaster + « ZULUTEK » cyberpunk.

## Lot D — circuit bending (style ZuluFx, SHIFT remplacé par REC/SAVE)

- **FX maintenu + K1..K4** : chaque knob engage son effet circuit bending (K1 CRUSH, K2 GATE, K3 STRETCH,
  K4 SCRATCH — engine PoW!); le **dernier knob touché reste actif** tant que FX est maintenu; tourner le
  knob actif règle son mix. Au **relâché de FX**, l'effet s'éteint avec un **tail propre à l'effet**
  (CRUSH 0,12 s, GATE 0,06 s, STRETCH 1,2 s, SCRATCH 0,6 s — `fx_punch_off_tail` dans `dsp/fx.c`).
  Sur le LCD : **cadre ambré autour du knob actif + mandala animé par effet** (ZuluFx allume la cellule
  du pad en ambré avec bordure; ici cadre ambré + pétales tournants, couleur par knob).
- **PoW! + bend** (ZuluFx : SHIFT + pad) : **SAVE maintenu + pad blanc en mode PoW!** = PoW! + circuit
  bend global (mode BEND M); au relâché, tail proportionnel au coût de l'effet. SAVE = modificateur
  bend partout (FM-1 sans écran tactile : SAVE+K1/K4).
- **XY bend** : APK REC+toucher, FM-1 SAVE+K1/K4 — voir section page XY ci-dessus.
- Lot E outillé : `tools/lcd_image.py` (PNG → RGB565 240×240 `.bin`/`.h` pour le LCD ST7789) +
  `docs/PROMPT-BOOT-IA.md` (prompt IA pour l'anim de boot ~2 s).

## Page KIT (19) — REC+SAVE — kits façon Octatrack

Un **kit = sauvegarde totale de l'état global** : réglages courants + **tous les snaps** (layers,
captures MIDI-in, masques) + PoW! engagé — rappelable à tout moment. Icône 🚧 (petit panneau de chantier).

- **REC+SAVE** ouvre/ferme la page KIT (16 slots sur les touches blanches ; SELECT = slot).
- **Charger** : en page KIT, tap d'un des 16 slots. OPTIONS > **KIT LD** = **DIRECT** (instant) ou
  **BARRE** (chargé à la prochaine mesure — quantisé sur l'horloge du looper, `kit_pend_` côté moteur;
  même option à prévoir sur le FM-1).
- **Sauvegarder** : **SAVE** (arme, « KIT SAVE: PAD? ») → tap du **slot de destination** (n'importe
  lequel : copier un kit ouvert dans un autre slot = sauvegarde/copie) → **SAVE** encore = enregistré
  (icône 🚧 pop+shrink). SAVE pendant l'arm sans pad = annule.
- **Effacer** : maintenir un slot **1,5 s** → « ERASE KIT n? » → **SAVE** confirme.
- **Double-tap** sur un slot = copie vers le slot miroir (xor 8), comme les snaps ZU/LU.
- Stockage : 16 kits × (settings + 16 snaps × 4 layers + MIDI) — heap côté APK, flash côté FM-1 (patch).
- REC+PLAY sur la page KIT est inerte (la sauvegarde passe par SAVE).

## Modulation (LFO/ENV) — socle commun, façon ZuluFx — rééquilibré 8/8 (voir docs/ZULUTEK-SEQ-PLUS.md)

zuluTek partage **une seule section de modulation** entre basse/synth/drum (pas de LFO/ENV par branche,
trop coûteux sur l'AC791N et contraire à « tout linkable »). Depuis le pack SEQ+ :

- **page LFO (REC+LFO) = 8 modules : 4 LFO + 4 labos** — LFO1..4 (SIN/TRI/SQ/SAW/S&H/DRUNK,
  options LFO SYNC divisions d'horloge + LFO TRIG retrigger par note) et LABO1..4 (les anciennes
  boîtes « réseau », 8 types : LINK, MYCELIUM, HUMUS, NET VINTAGE, CHAOS, OISEAUX, INSECTES, MARÉE).
- **page ENV (REC+ENV) = 8 tools, l'enveloppe en premier : ENV1, ENV2 (bouclables) + EF, LOG,
  SLEW, CMP, QUANT, ATT.**
- Routage en **ASSIGN typé façon VCV Rack** (page ASSIGN) : 16 sources (sorties) vers 35
  destinations (entrées : voix 0..7, FX 8..25, seq 26..34) — un câble ne va que out → in,
  anti-boucle (un labo ne source que des sources d'index inférieur), panneau « sens interdit »
  + suggestions à l'écran, **option LUDIQUE** désactivable (mode expert). Toute la section tourne
  **par blocs de 32 échantillons** → coût DSP négligeable sur l'AC791N.
- **Mutation live** : ENV seul = couche TIME, LFO seul = couche PITCH, ENV+LFO = ARTICULATION
  (comme l'ATEK303 d'origine, `acid_mutate_burst_range` sur la page de 16 pas courante).
- **SEQ+ (double-tap SEQ)** : TM LOCK / TM LEN / MUT / PLAY (NORM/REV/PING-PONG) / RAND —
  détail dans `docs/ZULUTEK-SEQ-PLUS.md`.

À prévoir dans le patch FM-1 : ajouter les **16 mix des slots PoW!**
comme destinations de la matrice (`labApply` au-delà de `ZT_NDST`) pour qu'un LFO puisse aussi piloter
les macros.

## Page FINE (18 APK, page 14 FM-1 — patch 0012) — 4 knobs façon Shruthi sur les réglages fins voix

Même contrat des deux côtés : **SEL = banc** (OSC / FILTRE / AMP), **K1..K4 = 4 réglages du banc**.
- OSC : PW / SQ_DROOP / SAW_RESET / SAW_DROOP · FILTRE : MODEL ORG/NEW (`f_model`) / MORPH LP→HP (`f_morph`) / DRIVE / OTA · AMP : SLIDE / LEGATO / SAT / DRIFT
- APK : appui **PRE** (ou DLY dans le cycle) = page 18 ; persistance `ztek_opts` (`fine_$idx`) + presets/snapshots (champ `opt[]` du POD). Moteur : `ztek_set_option/get_option` (opt 0..10 = `AT_OPT_*`, 11 = model, 12 = morph 0..255).
- FM-1 (patch 0012, PR #15 — **pas encore mergé côté firmware au 2026-11-20 ~20:30Z → page FINE = « APK en avance »**) : REC maintenu + EDIT = page 14, GLO quitte ; CC MIDI 84..96 ; persistance OBJ_OPTIONS (OPT5). Numérotation : page 17 APK prise par PR #13 (LINK) → FINE = 18 côté APK, 14 côté FM-1.

## OPTIONS (page 9 APK, long-SCL ; OBJ_OPTIONS OPT6 côté FM-1)

- **OPTIONS > ECO = OFF/ON** (« MODE ECO (BATTERIE) », comme Zulu v0.33) — mode éco DSP/RAM :
  runtime (flag `ztek_set_eco`, lu dans le render) : entrée crush 96 + loudness `tanh` (style Zulu),
  BBD **mono**, swirl de la Black Hole off, pre-delay max /2, decay réverb ≤ **0.70** (0.89 sinon),
  polyphonie drums **8 → 5**, resample loop forcé **µ-law + paliers lo-fi (fmt 2), ring 32 kHz, capa /2**,
  LCD throttlé (~1 s) + animations figées côté APK. Build éco firmware (defines, `patches/README.md`) :
  `-DFX_RV_Q` (lignes plaque en 16 bits, −73 Ko), `DLY_N`/2 (BBD 300 ms max, −51 Ko), `PN_N`/2, `ML_RS_MAX`/2
  → ≈ **−220 Ko** de pool sur l'AC791N (~430 Ko RAM). Persistance : SharedPreferences APK / OBJ_OPTIONS FM-1.
- **OPTIONS > CHOKE LDR = ON/OFF** — mode choke des séquenceurs : ON = **LEADER** (hybride, seul le leader
  coupe), OFF = **NORMAL** (Koala, choke mutuel). Manip par touches noires MONO (9e) / POLY (10e), voir
  `docs/ZULUTEK-LOOP.md`.

## Table de mixage principale (page MIX, 4 tranches)

4 tranches **BASSE / LEAD / DRUMS / LOOP** (BASSE et LEAD partagent le bus voix physique :
engine 0 = LEAD, engines 1..4 = BASSE). Par tranche, en tête **pré-fader** : filtre traversant
**LP — neutre — HP** + cutoff (40 Hz..5 kHz), puis **VOL**, **MUT** ; départs auxiliaires
**post-fader** : PoW! (P), Loop (L — la tranche LOOP n'en a pas), 2 slots libres AUX1/AUX2
(assignables dans la baie, module AUX). Master : **BUS** (gain, SEL=tranche 4) + départs aux
libres master AUX1/AUX2 (vides par défaut, baie) + **VU**. Chaîne FX partagée (1×) sur le bus
master, Black Hole bridée. Pas d'inserts delay/reverb dans le mixeur (pages instruments).
Contrôles page MIX : SEL=tranche/master, K1=vol (bus si master), K2=mut, K3=filtre LP/·/HP,
K4=cutoff, PRE=aux PoW!, ALG=aux Loop. Moteur : `ztek_mix_set/get_strip` (ZTEK_MIX_*), tranches
préservées par snapshots/kits/undo.
