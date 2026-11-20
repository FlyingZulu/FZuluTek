# zuluTek DRUMS — drum machine analogique (module `app-zulutek-drums`)

> **Historique** : la branche `vibe/zulutek-drum-analog-c9ef18` (PR #5 de l'ancien dépôt
> `fm1-Zulu`, 31 fichiers) était **perdue** avec la suppression de ce dépôt (objets introuvables
> partout : refs, packs locaux, objets unreachable). Ce module est donc une **reconstruction**
> neuve sur la branche `vibe/zulutek-drum-analog-c9ef18` (basée sur `seq-plus`), pas un
> cherry-pick de l'ancien code.

## Contenu

- **Moteur natif C99 autonome** `app-zulutek-drums/src/main/cpp/drum_engine.{h,c}` — aucune
  dépendance au moteur ztek : 8 voix « analogiques » style 808/909 :
  `KICK` (sinus à pitch env 150→45 Hz + click), `SNARE` (bruit HP + tonalité 185 Hz),
  `HAT`/`HAT-O` (bruit HP, decay 35/220 ms), `CLAP` (3 bursts de bruit), `TOM` (pitch env
  210→95 Hz), `CONGA` (sinus + bruit), `COWBELL` (2 carrés 845/560 Hz + bruit).
- **Séquenceur 16 steps** par pad, tempo BPM + swing (décalage des steps impairs),
  patterns par défaut 4-on-the-floor / backbeat / 8th hats, CLEAR + RAND (densité réglable).
- **Flux audio** : Oboe côté natif (`oboe::oboe` via prefab), mono → stéréo, blocs de 256.
- **UI Kotlin** : `DrumGridView` (rangée 16 steps + grille 4×4 pads, step courante allumée),
  `DrumsActivity` (PLAY/STOP, TEMPO, LEVEL du pad sélectionné, SWING, STEP édition, CLEAR,
  RAND, RESET). APK nommée `ZuluTekDrums-<buildType>.apk`, signature conditionnelle (status quo
  `signing/zulu.p12`, mot de passe inchangé).
- **Icônes** : tambour pixel art généré en CI par `tools/make_icon.py` (les PNG sont
  gitignorés comme ceux de `app-zulutek` — aucun binaire ne transite par le dépôt).

## Build / test

- CI : `gradle assembleDebug` build automatiquement le module (inclus dans `settings.gradle`),
  artefact `zulu-apk-<run>` inclut `app-zulutek-drums/build/outputs/apk/debug/*.apk`.
- Host : `gcc -std=c99 -Wall -Wextra -fsyntax-only drum_engine.c` + test de rendu
  (énergie non nulle, seq avance, hits, rand) — fait, RC=0. Bridge : `g++ -fsyntax-only`
  avec stubs jni/oboe — OK.

## Liens

- Presets « partie DRUMS » du moteur ztek (PR #3, page PRESETS) : partie 2 = DRUMS.
- À faire ensuite : porter cette drum machine sur le firmware FM-1 (patch 0006, 27 touches =
  8 pads + steps), et brancher les sorties drum dans la baie de patch (page 12 PATCH).
