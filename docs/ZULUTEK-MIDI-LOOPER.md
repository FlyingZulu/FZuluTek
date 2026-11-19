# ZULUTEK — MIDI LOOPER (design)

`app-zulutek/src/main/cpp/dsp/midi_looper.c/h` — le looper MIDI v2, porté des boucles MIDI de
l'app ZuluFx (`FlyingZulu/fm1-Zulu`, GPL-3.0) et adapté au FM-1. Implémentation : voir le
header, chaque fonction y est documentée.

## Pourquoi du MIDI et pas de l'audio

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
