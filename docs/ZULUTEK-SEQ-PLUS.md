# zuluTek — pack « SEQ+ » & modulation 8/8 (branche `vibe/zulutek-seq-plus-c9ef18`)

Pack de fonctionnalités approuvé en session, développé d'abord sur l'APK (`app-zulutek`),
à porter ensuite sur le FM-1 via les patches. Tout est documenté ici (consigne PRISE-EN-MAIN :
chaque décision dans `docs/`).

## 1. Séquenceur avancé — double-tap SEQ (sous-mode de la page SEQ)

Double-tap sur **SEQ** = génère un nouveau pattern (seed aléatoire, comme le SEQ de
l'ATEK303) et ouvre le **sous-mode avancé** de la page SEQ. Les 5 réglages :

| encodeur | nom | rôle |
|---|---|---|
| K1 | **TM LOCK** | 0 = mode VCV d'origine pur (générateur acid v5, bit-à-bit) ; > 0 = **Turing Machine** active, la valeur = probabilité de « lock » |
| K2 | **TM LEN** | longueur de la fenêtre TM, 4..64 pas |
| K3 | **MUT** | taux de mutation (bursts live 1..8 opérations + probabilité de mutation TM) |
| K4 | **PLAY** | mode de lecture par tiers : **NORM / REV / PING-PONG** |
| SELECT | **RAND** | probabilité de re-génération complète du pattern à chaque boucle |

**Turing Machine** (inspirée des modules Turing Machine des BattleStation, ex. Befaco) :
à chaque wrap de boucle, la fenêtre `[0, TM LEN)` du pattern tourne d'un pas vers la
droite ; le pas entrant (index 0, le pas sortant n'est pas réinjecté tel quel) est :
gardé tel quel si `r < LOCK`, muté (degré/octave/gate/accent) si `LOCK ≤ r < LOCK+MUT`,
sinon re-tiré aléatoirement. `LOCK = 1` → boucle parfaite qui tourne ; `LOCK = 0` → TM off.
Le grid est réinjecté dans le pattern via `acid_import()` puis re-rendu (`acid_render`).

**PLAY** : NORM = avant, REV = arrière, PING-PONG = aller-retour (rebond aux extrémités).

## 2. Mutation live — boutons ENV / LFO seuls (comme l'ATEK303 d'origine)

Dans l'APK preview, l'API existait mais n'était pas câblée ; c'est fait :
- **ENV seul** = mutation de la couche **TIME** de la page courante (16 pas)
- **LFO seul** = mutation de la couche **PITCH**
- **ENV+LFO** = mutation de la couche **ARTICULATION**

Implémentation : `ztek_seq_mutate(layer)` → `acid_mutate_burst_range(&pat, layer, rng,
1 + MUT*7 ops, scale, &last, page, page+16)` puis `acid_render`. Sur le FM-1, c'est le
`EV_MUTATE` de l'ATEK303 (`app.c` : ENV → `ACID_MUT_TIME`, LFO → `ACID_MUT_PITCH`).

## 3. Rééquilibrage des pages de modulation — 8 modules partout

- **Page ENV (REC+ENV) = 8 tools**, le premier est l'enveloppe :
  **ENV1, ENV2** (deux enveloppes **bouclables** — ADSR en boucle, retrigger par note si
  LFO TRIG), puis **EF** (envelope follower du bus synthé), **LOG** (courbe log),
  **SLEW** (lag montante/descendante), **CMP** (comparateur à hystérésis),
  **QUANT** (quantizeur), **ATT** (gain+offset).
- **Page LFO (REC+LFO) = 8 modules : 4 LFO + 4 « labos »** (les anciennes boîtes
  « réseau/N1 » de ZuluFx, renommées **labos** : labo glitch/CHAOS, labo oiseaux, labo
  insectes, labo marée…). 8 types : LINK, MYCELIUM, HUMUS, NET VINTAGE, CHAOS, OISEAUX,
  INSECTES, MARÉE. Chaque labo : TYPE, SRC1, SRC2, DEPTH.
- **LFO** : 4 LFO, ondes **SIN/TRI/SQ/SAW + S&H + DRUNK**, options **LFO SYNC** (snap sur
  divisions d'horloge : 1/16..2 mesures) et **LFO TRIG** (retrigger des LFO/ENV par note).

**Coût DSP sur le FM-1 : négligeable.** Toute la section modulation (LFO, labos, envs,
tools, matrice) tourne **par blocs de 32 échantillons**, pas par échantillon audio
(quelques one-poles, un LCG, un anneau 16 cases par labo) → < 0,1 % du CPU de l'AC791N.
La charge lourde reste les voix/FX par échantillon.

## 4. Matrice ASSIGN typée, façon panneau VCV Rack / miRack

Page **ASSIGN** (REC+HOME sur l'APK) : **SEL = destination (entrée), PRESET = source
(sortie), K1 = depth, ALG = option LUDIQUE**. Règles de typage (un câble ne va que
**out → in**) :

- le menu **SRC ne propose que les 16 sources** (LFO1..4, LABO1..4, ENV1/2, EF, LOG,
  SLEW, CMP, QUANT, ATT) + OFF — jamais un paramètre ;
- le menu **DST ne propose que les 35 paramètres** (voix 0..7, FX 8..25, seq 26..34 :
  STEPS/SCALE/TEMPO/ROOT + TM LOCK/TM LEN/MUT/PLAY/RAND) — jamais une source ;
- **anti-boucle** : un labo (source 4..7) ne peut sourcer que des sources d'index
  strictement inférieur ; idem pour les tools (10..15). Le moteur clamp aussi côté DSP.

À l'écran : si on tente un branchement invalide, un **panneau « sens interdit »**
(rond rouge plein + barre blanche horizontale, animé ~1,2 s) s'affiche, avec en dessous
les **suggestions de destinations valides**. Sur un branchement valide, une petite bulle
turquoise éphémère propose des idées cohérentes selon la catégorie de la destination
(ex. « LFO1 → CUTOFF », « ENV1 → DECAY », « LFO → TEMPO »).

**Option LUDIQUE** (mode ludique/éducatif, dans **OPTIONS > LUDIQUE** sur le FM-1, encodeur
ALG sur la page ASSIGN de l'APK) : désactivable pour les utilisateurs avancés — plus de
conseils, plus de sens interdit animé (le typage out→in reste appliqué par le moteur).
Portée : faciliter la découverte du fonctionnement d'une machine qui peut être complexe
par endroits — **flow fluide au quotidien, labo expérimental toujours sous la main** pour
mener des expériences plus poussées (les 4 labos + la matrice ASSIGN typée sont ce labo).

## 5. Fichiers touchés

- `app-zulutek/src/main/cpp/ztek_engine.h/.cpp` — section modulation (16 sources,
  4 LFO, 4 labos, 2 ENV bouclables, 6 tools), matrice ASSIGN (35 dst), seq avancé
  (modes PLAY, TM au wrap, RAND, `ztek_seq_mutate`), tout en blocs de 32 échantillons.
- `app-zulutek/src/main/cpp/jni_bridge.cpp` — bindings JNI des nouvelles API.
- `app-zulutek/src/main/java/com/zulutek/preview/{ZtekAudio,MainActivity,LcdView,SurfaceView}.kt`
  — pages 3 (SEQ+), 10 (LFO), 11 (ENV), 12 (ASSIGN), double-tap SEQ, ENV/LFO = mutation,
  sens interdit + bulles de conseils, option LUDIQUE.
- Vérifs : `g++ -fsyntax-only` (stubs jni/oboe) OK ; test hôte gcc sur
  `acid_mutate_burst_range` + rotation TM + `acid_import` OK.

## 6. Presets

Deux niveaux de presets :

- **8 presets usine pré-patchés** façon ZuluFx (bouton **EDIT** sur la page MAIN de l'APK) :
  ACID 303, FILTRE WOBBLE (LFO1→CUTOFF), PITCH DRIFT (LFO2 DRUNK→TUNING), GATE 303
  (ENV1→ENVMOD/DECAY), SLIDE CITY (PING-PONG + LFO3→TEMPO), LABO CHAOS (LABO1→RESO),
  TM MAX (Turing Machine ouverte), MARÉE (LABO4 MARÉE→REV MIX, ENV2→DLY TIME).
- **Presets user par partie** : page **PRESETS** (REC+EDIT) — **3 parties × 8 slots**
  (0 = BASSE, 1 = LEAD, 2 = DRUMS) ; SEL = slot, PRE = partie, K1 = LOAD, K2 = SAVE,
  K3 = effacer. Un preset = snapshot POD complet de l'état modulable (knobs des 16
  sources, labos, LFO SYNC/TRIG, ENV loop, matrice ASSIGN, seqk, params voix/FX, tempo,
  root, scale, engine). Persistance : blob binaire `ztek_preset_export/import`
  → **SharedPreferences (Base64) côté APK**, **flash côté FM-1** (à porter dans le patch).
- **Molette PRESET en live** (comme sur le FM-1) : sur la page MAIN, l'encodeur **PST**
  fait défiler les presets **pendant qu'on joue** — 8 presets usine puis les 8 slots user
  de la partie courante (partie choisie sur la page PRESETS) ; slot vide → sens interdit
  + suggestion « SAVE d'abord » (si LUDIQUE on).

## 7. À faire ensuite

- Porter le pack sur le FM-1 (patches `patches/atek303-*.patch`) : `EV_MUTATE`, pages,
  option LUDIQUE dans OPTIONS, presets user en flash.
- Mix des slots PoW! comme destinations de la matrice (`labApply` au-delà de `ZT_NDST`).
