# ZULUTEK — LOOP (design)

On l'appelle **Loop** (plus « MIDI loop »). `app-zulutek/src/main/cpp/dsp/midi_looper.c/h` —
le Loop v2 (le fichier vendored garde son nom upstream), porté des boucles de l'app ZuluFx
(`FlyingZulu/fm1-Zulu`, GPL-3.0) et adapté au FM-1. Implémentation : voir le header, chaque
fonction y est documentée.

## Pourquoi des événements (notes) et pas de l'audio

Les drums et synthés s'enregistrent en **événements** (note, vélocité, durée, automation de
params), rejoués en boucle : les paramètres restent retouchables derrière, et 96 événements
tiennent dans ~1 Ko contre des mégaoctets d'audio. Seul le **resampling** (reloop) passe en
audio, dans un ring de taille fixe.

## Le modèle

- **16 slots** = les 16 touches blanches du FM-1. Un **clock partagé** (PLAY/bpm, position en
  1/64 de mesure de 4/4) ; chaque slot boucle sur ce clock, donc les boucles restent en phase.
- **Longueur en 1/64 de mesure** (1 mesure = 64, max 32 mesures = 2048). Prédéfinie via SELECT,
  sinon **quantisée à l'arrêt** de l'enregistrement sur les puissances de 2 en temps (1, 2, 4…
  128 temps). Rétrécir tronque, agrandir clone la boucle originale (`ml_resize`).
- **REC** : `ml_rec_start` (od=0 efface le slot, od=1 overdub) → les événements entrants
  (`ml_note_in` / `ml_par_in`, filtrés par la source du slot : basse / synthé / drums / ALL /
  resample / MIDI in) sont estampillés relativement au début ; `ml_rec_stop` rebased sur le
  clock maître pour une boucle parfaite. Tap sur le slot = stop en gardant.
- **Lecture** : `ml_toggle` ; modes normal / reverse / ping-pong, offset de départ (K2),
  pitch ±12 demi-tons (K1), mutation (K4 : jitter de vélocité/params), niveau (PRESET).
  Les événements sont tirés par `ml_render` via des callbacks (`note_on`/`note_off`/`param`)
  qui pilotent le synthé — dans l'APK, `ztek_engine` branche ces callbacks sur la voix.
- **Resample** (`ml_rs_*`) : enregistre le bus monitoré dans un ring audio, au choix en
  int16, µ-law 8 bits (style SP-1200, moitié moins de RAM) ou µ-law + quantification par paliers
  selon la durée (lo-fi croissant). Le ring peut tourner à 32 kHz (décimation) pour alléger
  le FM-1. À l'arrêt, la longueur est quantisée en 1/64 de mesure comme les boucles d'événements.
- **Édition profonde** : `ml_step_toggle` / `ml_step_vel` découpent la boucle en 16 pas.

## Contraintes FM-1 respectées

État de taille fixe (~16 Ko + ring), **pas d'allocation dans le chemin audio**, pas de libm
(`fastmath.h`), callbacks depuis le thread audio uniquement. Côté hôte (APK), `FX_HOST` garde
les gros buffers hors de la section `.pool` du device — voir `dsp/README.md`.

## Choke des séquenceurs (DRUMS + LOOP) — process façade validé

Groupes de choke **1..6** (+0 = off) par pad (DRUMS) et par slot (LOOP), style Koala.
Deux modes globaux (flag `leader`) : **LDR ON = mode leader/hybride** (seul le leader coupe
les autres membres du groupe au déclenchement), **LDR OFF = normal Koala** (choke mutuel).

Manip (touches noires du clavier : **MONO = 9e, POLY = 10e**, adjacentes) :
1. Page DRUMS (long-ERS depuis MAIN) ou sous-vue CHK de la page LOOP (SCL) : SELECT = pad/slot.
2. **MONO** bascule le pad sélectionné en mode choke : fenêtre choke ouverte (groupe 1 par
   défaut) ; re-MONO = quitte le groupe (C0) et ferme la fenêtre.
3. Fenêtre ouverte : **SELECT = groupe C0..C6** ; **tap d'une touche blanche** = assigne ce
   pad/slot au groupe courant (multi-pads d'un coup).
4. **POLY + pad** = ce pad devient **leader** du groupe (il clignote rapidement à l'écran ;
   les pads hors groupe restent éteints). **POLY + re-tap sur le leader** = il n'est plus leader ;
   **POLY + autre pad** = ce pad est le nouveau leader. **POLY sans sélection de pad** (fenêtre
   qui se ferme) = **mode leader désactivé** pour le groupe (retour à l'auto : 1er assigné).
   Le leader désigné reste leader tant qu'on ne le désassigne pas, ou que les pads ne sont pas
   désassignés tout court (C0).
5. La fenêtre choke se quitte en ouvrant un autre menu / en faisant autre chose, ou
   **toute seule après 1,5 s sans action**.

Moteur : `drum_set_choke/...`, `ml_set_choke/...`, `drum_set_leader_pad(pad,on)`,
`ml_set_leader_slot(slot,on)`, `*_choke_master(grp)` (leader explicite `lead[]`, sinon 1er
assigné via `choke_order[]`). Appliqué au déclenchement : `drum_hit` (donc aussi séquenceur
16 steps) et `ml_toggle` (+ note-offs). Choke hors `ml_sp_t` : la baie garde ses 112 ports.
Snapshots/kits/undo préservent choke + ordre + lead + leader. **Défaut : choke à 0 partout** (désactivé). APK : `ZtekAudio.drum/loopSetChoke/GetChoke/ChokeMaster/SetLeader/SetLeaderPad/Slot`.
