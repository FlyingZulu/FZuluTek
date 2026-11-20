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

Page **ASSIGN** (double-tap REV sur l'APK, page 9) : **SEL = destination (entrée), PRESET = source
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
  — pages 6 (SEQ+, double-tap SEQ), 7 (LFO, double-tap MUT), 8 (ENV, double-tap DLY), 9 (ASSIGN, double-tap REV), 10 (PRESETS, double-tap ERS), mutation live (appui long TAP/SCL, double-tap GEN), presets live (double-tap R-/R+),
  sens interdit + bulles de conseils, option LUDIQUE.
- Vérifs : `g++ -fsyntax-only` (stubs jni/oboe) OK ; test hôte gcc sur
  `acid_mutate_burst_range` + rotation TM + `acid_import` OK.

## 6. Presets

Deux niveaux de presets :

- **8 presets usine pré-patchés** façon ZuluFx (double-tap **R+** (preset suivant en live, page MAIN)) :
  ACID 303, FILTRE WOBBLE (LFO1→CUTOFF), PITCH DRIFT (LFO2 DRUNK→TUNING), GATE 303
  (ENV1→ENVMOD/DECAY), SLIDE CITY (PING-PONG + LFO3→TEMPO), LABO CHAOS (LABO1→RESO),
  TM MAX (Turing Machine ouverte), MARÉE (LABO4 MARÉE→REV MIX, ENV2→DLY TIME).
- **Presets usine PAR PARTIE** (`ztek_preset_factory(part, i)` + `ztek_preset_name_part(part, i)`) :
  chaque partie a ses **propres 8 presets usine pré-patchés**, construits sur les 8 génériques
  + moteur/son de la partie + **4 slots macro remplis** (les conteneurs « patch » prêts à appliquer) :
  **BASSE** = moteurs PLUK/FM/SUB/8BIT (AT_ENGINE 1..4), root −12, filtre sombre, macros
  CUTOFF / RESO / DECAY / type PoW! ECHO ; **LEAD** = ATEK303 bright, 128 bpm, macros
  CUTOFF 0.9 / ENVMOD 0.8 / DLY TIME ; **DRUMS** = SEQ+ (TM LOCK 0.7, PING-PONG, RAND), loop gain
  0.9, macros loop0→PoW! (aux), type PoW! ECHO, baie VOIX→FX. `ztek_bank_load(part, slot)` charge
  le preset usine **de la partie** quand le slot n'a pas de save user ; `bankName` affiche son nom.
- **Presets par partie, banc LIBRE** : page **PRESETS** (double-tap ERS, page 10) — **3 parties × 16 slots**
  (0 = BASSE, 1 = LEAD, 2 = DRUMS) ; SEL = slot, PRE = partie, K1 = LOAD, K2 = SAVE,
  K3 = effacer. Le banc est libre : les 8 premiers slots contiennent les presets usine,
  mais **n'importe quel slot peut être écrasé par un save user** (pas de séparation fixe
  8+8). `ztek_bank_load(part, slot)` charge le save user s'il existe, sinon le preset
  usine **de la partie** si le slot est dans la plage usine. Un preset = snapshot POD complet de l'état
  modulable (knobs des 16 sources, labos, LFO SYNC/TRIG, ENV loop, matrice ASSIGN, seqk,
  params voix/FX, tempo, root, scale, engine, **macros 16 slots**). Persistance : blob binaire
  `ztek_preset_export/import` → **SharedPreferences (Base64) côté APK**, **flash côté
  FM-1** (à porter dans le patch).
- **Molette PRESET en live** (comme sur le FM-1) : sur la page MAIN, l'encodeur **PST**
  fait défiler les **16 slots du banc de la partie courante pendant qu'on joue** ; slot
  vide → sens interdit + suggestion « SAVE d'abord » (si LUDIQUE on).

## 7. Agglomération avec la branche looper (PR #1 @ 7d87264) — merge `8af3450`

La session parallèle a livré sur `ml` la **page MIX + baie de patch façon AUM** ; agglomérée
avec le pack SEQ+ dans `vibe/zulutek-seq-plus-c9ef18` :

- **page 11 = MIX** : niveaux des 16 slots du looper, aux (send) vers PoW!, gain du bus LOOP
  (`ztek_mix_set_vol/aux/gain`, `ztek_mix_get_gain`). SEL=slot, K1=vol, PRE=aux, ALG=gain bus.
- **page 12 = PATCH** : baie de patch **façon AUM/VCV** — 4 sources (VOIX, SIREN, SMP/AIN,
  bus LOOP) × 3 destinations (chaîne FX, MASTER direct, entrée anneau PoW!) ;
  `ztek_patch_compat(src,dst)` = 0 → connexion impossible (estompée dans la baie, comme le
  typage out→in de la matrice ASSIGN page 9). SEL=rangée(source), ALG=colonne(dest),
  tap cellule ou PLAY = toggle.
- Navigation : bouton **DLY** cycle 0→6→7→…→12→0 ; **REC** quitte les pages 1 et 6..12.
- Pages finales APK : 0 MAIN, 1 LOOP, 2 SNAP, 3 KIT, 4 XY, 5 POW!, 6 SEQ+, 7 LFO, 8 ENV,
  9 ASSIGN, 10 PRESETS, 11 MIX, 12 PATCH.

## 8. À faire ensuite

- Porter le pack sur le FM-1 : fait dans PR #4 (`patches/0005-zulutek-seq-plus.patch`) —
  reste à **builder avec la toolchain JieLi et pré-tester dans fm1-emu** (voir
  `docs/FM1-EMULATEUR.md`) avant tout flash.
- Porter aussi MIX/PATCH sur le firmware FM-1 (pages 11/12 côté ATEK303, baie de patch
  out→in comme page ASSIGN).
- Mix des slots PoW! comme destinations de la matrice (`labApply` au-delà de `ZT_NDST`).

## 9. Pack SEQ+ porté sur les MIDI loops (en cours, non poussé — en attente d'aval)

Même sémantique ENV/LFO/SEQ que le seq acid (`EV_MUTATE`) et le drum, appliquée aux
16 slots du looper (`midi_looper.c`) :

- **`ml_mutate(m, slot, layer)`** : layer 1 = TIME (dur/timing ±, comme ENV seul),
  2 = PITCH (transpose -2..+2 demi-tons, met à jour `sp.pitch`, comme LFO seul),
  3 = ARTIC (vélocités 40..127, comme ENV+LFO). Exposé en JNI :
  `ZtekAudio.loopMutate(slot, layer)` → `ztek_loop_mutate`.
- **`ml_set_randp(m, p)`** : probabilité de re-générer un slot à son wrap (RAND global
  des loops). Exposé : `ZtekAudio.loopRandp(p)` → `ztek_loop_randp`.
- **Mode de lecture 3 = RANDOM** (`ML_SP_MODE`, cycle REV dans la page LOOP : NORM →
  REV → PING-PONG → RANDOM) : au wrap, la position saute à un offset aléatoire.
- UI APK : sur la **page 1 (LOOP)**, appui long **TAP** = mutation TIME du slot,
  appui long **SCL** = PITCH, double-tap **GEN** = ARTICULATION (hors page LOOP :
  comportement seq acid inchangé).
- Tests hôte (`gcc -DFX_HOST`, 15 checks) : mutate 3 layers, clamp randp, mode RANDOM,
  RAND au wrap (randp=1 re-génère ~len/8 notes pitch 36..72 ; randp=0 inchangé ;
  slot en rec protégé) → **tous OK**. `g++ -fsyntax-only` moteur + JNI → OK.
- À faire : porter la même chose dans le patch FM-1 (patch 0006, avec la drum machine),
  et brancher `loopRandp` sur un potard (ex. SEL RAND de la page SEQ+).

## 10. Arpégiateur (bouton ARP, page 13) — ajouté après le transfert

Nouveau module `ztek_arp_*` dans `ztek_engine.{h,cpp}` (APK ; à porter sur FM-1 avec le patch 0006) :

- **Bouton ARP** (page MAIN) : toggle on/off. Quand ARP est on, les notes du clavier alimentent
  une liste de notes tenues (16 max, triées + ordre de jeu) — elles sont quand même enregistrées
  dans le looper — et l'arp les rejoue en boucle. Coupure = silence propre.
- **Page 13 ARP** : SEL=MODE, PRE=OCT, ALG=DIV, K1=GATE.
  - MODE : UP / DOWN / UP-DOWN (ping-pong sans répéter les extrémités) / RANDOM / AS-PLAYED ;
  - OCT : 1..4 octaves (la boucle s'étend sur held×oct, +12 st par octave) ;
  - DIV : 1/4, 1/8, 1/16, 1/32, 1/8 triolet, 1/16 triolet (pas/temps = 1,2,4,8,3,6, synchro BPM) ;
  - GATE : 0.05..1.0 de la durée du pas (note-off avant le pas suivant si < 1).
- Les notes générées par l'arp ne sont PAS ré-enregistrées dans le looper (record=0) ; le seq acid
  et les callbacks looper passent par `ztek_note_on_int` et ne sont pas interceptés.
- JNI : `ZtekAudio.arpOn/arpKnob/arpKnobGet/arpHeld` → `nativeArpOn/nativeArpKnob/...`.
- Navigation : REC quitte la page 13 ; DLY cycle …12→13→0.
- **Tests hôte** (20 checks, `#include "ztek_engine.cpp"` + objets DSP C) : capture/dédup/tri,
  UP 60-64-67 en boucle, DOWN 67-64-60, UP-DOWN ping-pong 60-64-67-64-60, OCT 2 (+12 st),
  RANDOM dans held, AS-PLAYED ordre de jeu 67-60-64, retrait note_off, silence à vide,
  arp off = reset, clamps knobs → **tous OK**. `g++ -fsyntax-only` moteur+JNI → OK.
- FM-1 : le bouton ARP existe physiquement ; à câbler dans le patch (arp sur la voix courante,
  SAVE+K1..K4 = bend déjà pris — utiliser ARP seul + K1..K4 pour MODE/OCT/DIV/GATE).

## 11. Page MACRO (14) — 16 slots assignables (SPEC Notion « SPEC MACRO zuluTek »)

Un macro = **conteneur** : cible (dst) + sous-cible + niveau 0..1 (`ztek_macro_*` dans le moteur) :

- **Cibles** : 0..7 voix, 8..25 FX, 26..34 seq (mêmes ids que la matrice ASSIGN) ; **35..50 = les 16 mix
  des macros** (la matrice ASSIGN page 9 peut piloter un macro, ex. LFO1 -> MACRO 3) ; **100+slot =
  aux du slot loop -> PoW!** (départs auxiliaires du mixeur, page 11) ; **120+c = connexion de la baie
  de patch** (page 12, compat VCV respectée) ; **140 = type PoW! actif** (utilisé au prochain POW!).
- **UI APK** : double-tap **GLO** = page 14 (SEL=slot, PRE=cible, ALG=sous, K1=niveau) ; **REC** =
  enregistre le slot, **PLAY** = applique, **ERS** = efface ; **touche blanche = applique la macro du
  slot** directement. DLY cycle …13→14→0.
- Les macros sont embarquées dans les **presets par partie** (page 10) — save/load testé.
- POW! utilise maintenant le type courant du moteur (`ztek_punch_type_get`), donc un macro POWT
  change le type joué par le bouton POW (défaut ECHO).
- **Tests hôte** (19 checks) : apply param voix/FX, aux->PoW!, baie (compat SIREN->POW! bloquée),
  POWT sans déclencher le son, erase/no-op, matrice->mix de macro (LFO pilote et réapplique),
  level resync dst_base, preset save/load des macros → **tous OK**. `g++ -fsyntax-only` OK.
- FM-1 : à porter dans le patch 0007 (REC+SEQ = page MACRO, touches blanches = slots, K1 = niveau,
  matrice peut viser les mix des macros).
