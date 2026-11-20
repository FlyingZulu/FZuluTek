# ZuluTek

Projet ZuluTEK de Flying Zulu — cible matérielle : M-VAVE FM-1.

Firmware ATEK303 personnalisé (dérivé GPL-3.0 d'Animatek — crédits : Animatek / Flying Zulu)
+ APK de preview `app-zulutek`. Dépôt privé pendant le développement ; à l'ouverture :
code publié en GPL-3.0 + crédits Animatek, nouvelle clé de signature générée (l'actuelle
reste privée), spec ZuluLink jamais publiée (voir `docs/PRISE-EN-MAIN.md`).

## État des branches (maj 2026-11-20 ~15:30Z) — PR #1..#9 MERGÉES dans `main` (CI verte), PR #10/#11/#12 ouvertes

- **Mergées dans main** (ordre : #3 → #9 → #5 → #2 → #4 → #6 → #7 → #8 puis **#1** `3ca9446` par la session B — baie v3 hiérarchique + DRUMS + choke MONO/POLY + OPTIONS(ECO/LDR) + mixeur 4 tranches + page DRUMS, CI verte run 44722534300 ; `gh pr merge` bloqué par le proxy → merges via `git merge --no-ff` + `git push origin main`, branches conservées) :
  - **PR #3** `vibe/zulutek-seq-plus-c9ef18` — pack SEQ+ APK (modulation 8/8, ASSIGN typé LUDIQUE, presets, ARP page 13, MACRO page 14)
  - **PR #9** `vibe/zulutek-presets-parts-c9ef18` — presets usine pré-patchés **par partie** (BASSE/LEAD/DRUMS, 4 slots macro remplis chacun)
  - **PR #5** `vibe/zulutek-drum-analog-c9ef18` — drum machine analog v2 (8 pads PERKONS-like, comp bus Fac Smash)
  - **PR #2** `vibe/atek303-mojo-patch-c9ef18` — patches firmware 0001-0004
  - **PR #4** `vibe/fm1-patch-seqplus-c9ef18` — patch 0005 SEQ+ FM-1 + `docs/FM1-EMULATEUR.md`
  - **PR #6** `vibe/fm1-patch-arp-c9ef18` — patch 0006 arpégiateur FM-1
  - **PR #7** `vibe/fm1-patch-drums-c9ef18` — patch 0007 drum machine FM-1 (page DRUM 11)
  - **PR #8** `vibe/fm1-patch-looper-macro-c9ef18` — patch 0008 : page LOOP 12 + MACRO 13 FM-1, bus drum dans ASSIGN (dst 35..42), presets pré-patchés par partie (flash ZSP2)
  - **PR #1** `vibe/zulutek-midi-looper-c9ef18` (`3ca9446`) — session B : moteur de base + baie v3 + DRUMS + mixeur + OPTIONS
- **ouvertes** :
  - **PR #10** `vibe/ztf-link-design-c9ef18` — doc de conception liaison APK↔FM-1 « ZTF » (`docs/FM1-APK-LINK.md`, modes A/B, pont audio hybride)
  - **PR #11** `vibe/fm1-patch-choke-eco-c9ef18` (autre session) — patch firmware **0009-choke-eco**
  - **PR #12** `vibe/fm1-patch-link-c9ef18` — patch firmware **0010-link** : liaison ZTF côté FM-1 (companion 0x60/61/62, fichiers ZSMP/ZLUP/ZMAC, SysEx `F0 7D 'Z' 'T' …`, `usb_audio_out` + hook `app_usb_in`) — CI verte run 44754438124
  - **PR #13** `vibe/ztf-link-apk-c9ef18` (`bdedfdd`, CI verte run 44773909627) — **APK côté ZTF v1+v2** : `ZtfCodec.kt`/`ZtfTransport.kt`/`Fm1Usb.kt`/`SampleLibrary.kt`/`Fm1UsbAudio.kt` + page LINK 17 (push/pull ZSMP/ZLUP/ZMAC, import WAV Koala, pont audio UAC) + **v2 mode A** (miroir live params 0x62, télécommande PAGE/TEMPO/CUTOFF/LOOP-GAIN, clavier = notes sur le FM-1) + tests `tools/test_ztf_codec.py` — voir `docs/ZTF-LINK-APK.md`
- ⚠️ la série de patches firmware **0001-0010 n'a jamais été buildée avec la toolchain JieLi** ni testée dans fm1-emu (`docs/FM1-EMULATEUR.md`) — à faire **avant tout flash**. Ordre des patches : 0001-0008 → 0009-choke (#11) → 0010-link (#12).

<details><summary>Détail des PR mergées (archive)</summary>

- **PR #8** `vibe/fm1-patch-looper-macro-c9ef18` (`0748389`) — **firmware FM-1** : patch
  `patches/0008-zulutek-looper-macro.patch` : page **LOOP 12** (REC maintenu+PLAY : MIDI looper UI,
  16 blanches = slots, resample ring µ-law 32 kHz `ML_RS_MAX 4096`, mixé post-FX) + page
  **MACRO 13** (REC maintenu+SEQ : 16 slots, mêmes cibles que ASSIGN + loop_gain) + **bus drum
  dans la matrice ASSIGN** (dst 35..42) + **presets pré-patchés par partie** (save/load de l'état
  de modulation selon le tag BASSE/LEAD/DRUMS, flash ZSP2). apply-check OK sur 0001-0007, tests
  host OK, syntax-check app.c sans erreur nouvelle. ⚠️ pas encore buildé en toolchain JieLi.
- **PR #9** `vibe/zulutek-presets-parts-c9ef18` (`526aad7`, base PR #3) — **APK** : presets usine
  pré-patchés **par partie** (BASSE/LEAD/DRUMS : `ztek_preset_factory`, 8 presets/partie avec
  **4 slots macro remplis** chacun, noms propres, `bank_load`/`bankName` suivent la partie).
  Test host OK.
- **PR #3** `vibe/zulutek-seq-plus-c9ef18` (`192c635`) — **branche principale APK** : moteur complet
  (PR #1) + pack « SEQ+ » + modulation 8/8 (voir `docs/ZULUTEK-SEQ-PLUS.md`) : double-tap SEQ =
  sous-mode avancé (Turing Machine LOCK/LEN, MUT, PLAY NORM/REV/PING-PONG, RAND), mutation live
  ENV/LFO, pages 6..10 = SEQ+/LFO/ENV/ASSIGN/PRESETS (ENV = ENV1/ENV2 bouclables + 6 tools,
  LFO = 4 LFO + 4 labos, matrice ASSIGN typée façon VCV Rack out→in avec sens interdit 🚫 rond
  rouge + barre blanche + suggestions, option LUDIQUE, presets pré-patchés 8 usine + banc libre
  3 parties × 16 slots, molette PRESET en live), **pages 11 MIX + 12 PATCH** (baie de patch façon
  AUM agglomérée depuis PR #1 @ 7d87264), **page 13 ARP** (arpégiateur : bouton ARP on/off, modes
  UP/DOWN/UP-DOWN/RANDOM/AS-PLAYED, 1..4 octaves, divisions 1/4..1/16 triolets, gate), **page 14 MACRO**
  (16 slots assignables : voix/FX/seq/matrice + aux->PoW! + baie de patch + type PoW!, embarqués dans les
  presets; double-tap GLO, touches blanches = applique). CI verte.
- **PR #1** `vibe/zulutek-midi-looper-c9ef18` (`7d87264`) — session B : moteur de base restauré
  (veille Nyan, SMP, bend Lot D, ZuluLink, SNAP/KIT/undo, XY, POW!, sirène) + page MIX/baie de
  patch. CI verte.
- **PR #4** `vibe/fm1-patch-seqplus-c9ef18` (`2c11cc7`) — **firmware FM-1** : patch
  `patches/0005-zulutek-seq-plus.patch` (pack SEQ+ porté sur ATEK303, `git apply --check` OK,
  compile host C99 OK) + `docs/FM1-EMULATEUR.md` (pré-test via fm1-emu avant flash). CI verte.
  ⚠️ pas encore buildé avec la toolchain JieLi ni testé dans l'émulateur.
- **PR #2** `vibe/atek303-mojo-patch-c9ef18` (`24d6d32`) — patches 0001-0004 (`git am`) : voix/FX/
  bass engines/looper portés sur `animatek/FM1-Animatek` (base main HEAD 8934f53).
- **Branche drum** `vibe/zulutek-drum-analog-c9ef18` (basée sur seq-plus) — **reconstruction** du
  module `app-zulutek-drums` (l'ancienne branche/PR #5 de `fm1-Zulu` était perdue avec le dépôt
  supprimé) : drum machine analogique 8 pads + seq 16 steps, moteur C99 autonome
  (`drum_engine.c`), APK `ZuluTekDrums-*.apk`. Voir `docs/ZULUTEK-DRUMS.md`.

</details>

## Docs

- `docs/ZULUTEK.md` — la bible ; `docs/ZULUTEK-MIDI-LOOPER.md` — le looper ;
  `docs/ZULUTEK-SEQ-PLUS.md` — le pack SEQ+ ; `docs/ZULUTEK-UI-STYLE.md` — charte graphique ;
  `docs/PRISE-EN-MAIN.md` — consignes (confidentialité ZuluLink, clé de signature, GPL).

## Règles de session (anti-perte + travail en parallèle)

Le bac à sable local est effacé entre les sessions : **push tôt, push souvent**
(`git commit && git push origin <branche>` dès qu'une unité est testée), commit local
fréquent, et à chaque session : README + mode d'emploi à jour + sauvegarde Notion
(BIBLE ZULU). Voir les pages « ⚠️ RÈGLES ANTI-PERTE » et « 🧭 Sessions parallèles — qui fait
quoi » dans Notion : une branche = un périmètre, PR draft ouverte tôt, log Notion de la
tâche en cours, revue croisée des PR entre sessions.
