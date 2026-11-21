# ZuluTek

Projet ZuluTEK de Flying Zulu — cible matérielle : M-VAVE FM-1.

Firmware ATEK303 personnalisé (dérivé GPL-3.0 d'Animatek — crédits : Animatek / Flying Zulu)
+ APK de preview `app-zulutek`. Dépôt privé pendant le développement ; à l'ouverture :
code publié en GPL-3.0 + crédits Animatek, nouvelle clé de signature générée (l'actuelle
reste privée), spec ZuluLink jamais publiée (voir `docs/PRISE-EN-MAIN.md`).

## État des branches (maj 2026-11-21 ~17:10Z) — `main` = `5384116`, **CI verte run 44926328701** ; mergées : #30 #31 #35 #36 #40 #41 #32 #33 #34 #37 #39 — **aucune PR ouverte**, série de patches 0001..0022 complète dans `patches/` (prochain n° : 0023)

- **Session 2026-11-21 15:19–16:45Z (charte graphique, poussée sur main)** : `LcdView` rendu fidèle au LCD du FM-1 (framebuffer 240×240 natif + RGB565 + nearest + trame physique, `09eab01`) ; typo pixel 5×9 sur tous les titres de pages + `make_icon.py` (glyphe N complet, `cd44e5e`/`5384116`) ; habillage « Zulu Cat » v2/v3 (yeux HP XXL, zébrures pop, `1aaca62`..`9cc56aa`) + titres d'intro pixel art (`b1774c5`..`2e5b789`) ; Bible à jour côté rendu (`c70c41b`, `2efaf7a`).
- **Session 2026-11-21 ~17:10Z (finition APK, équipe B)** : branche `vibe/zulutek-apk-finition-ec495a` — audit navigabilité pages 0..18 (toutes atteignables : REC+* / dbl-SEQ / dbl-GLO / SEL / PRE / ARP / tap GLO / long-HOME / HOME+Shift / HOME court), `pageNames2` complété pour les pages 0..5 (fix hint « PAGE ? » au cycle HOME+Shift FX/MIX/SNAP/KIT), tests `tools/test_ztf_codec.py` OK, `tools/make_icon.py` OK. Ouvert : PR draft finition. Questions en attente Flying Zulu (log Notion) : contrôles page MIX (Bible ligne 281 vs sous-menus post-#14), réf « DLY dans le cycle » périmée (docs/ZULUTEK.md:214), presets contextuels par partie, test e2e ZTF ZTP2 après flash FM-1.

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
  - **PR #13** `vibe/ztf-link-apk-c9ef18` (`0b07904`, CI verte) — **APK côté ZTF v1+v2+v3** : `ZtfCodec.kt`/`ZtfTransport.kt`/`Fm1Usb.kt`/`SampleLibrary.kt`/`Fm1UsbAudio.kt` + page LINK 17 (push/pull ZSMP/ZLUP/ZMAC, import WAV Koala, pont audio UAC) + v2 mode A (miroir live 0x62, télécommande, clavier = notes sur le FM-1) + v3 vue étendue (EDIT = baie plein écran) + onglet PRESETS (export/import ZTP1) + tests `tools/test_ztf_codec.py` — `docs/ZTF-LINK-APK.md`
- **ouvertes** (sessions A/B en cours) :
  - **PR #14** `vibe/zulutek-mix-ldr-c9ef18` (session B) — page MIX rework + LDR/SOLO/PAN + page SIREN (slot 5) + **pedalboard FX** (4 chaînes × 3 slots)
  - **PR #15** `vibe/zulutek-fine-0012-c9ef18` (session B) — patch **0012-zulutek-fine** (page FINE 14, 4 knobs Shruthi)
  - **PR #16** `vibe/zulutek-fine-apk-c9ef18` (session B) — page FINE (18) APK, parité 0012
  - **PR #10** `vibe/ztf-link-design-c9ef18` — doc de conception liaison APK↔FM-1 « ZTF » (`docs/FM1-APK-LINK.md`, modes A/B, pont audio hybride)
  - **PR #11** `vibe/fm1-patch-choke-eco-c9ef18` (autre session) — patch firmware **0009-choke-eco**
  - **PR #12** `vibe/fm1-patch-link-c9ef18` — patch firmware **0010-link** : liaison ZTF côté FM-1 (companion 0x60/61/62, fichiers ZSMP/ZLUP/ZMAC, SysEx `F0 7D 'Z' 'T' …`, `usb_audio_out` + hook `app_usb_in`) — CI verte run 44754438124
- ⚠️ la série de patches firmware **0001-0010 n'a jamais été buildée avec la toolchain JieLi** ni testée dans fm1-emu (`docs/FM1-EMULATEUR.md`) — à faire **avant tout flash**. Ordre des patches : 0001-0008 → 0009-choke (#11) → 0010-link (#12).
- **PR #35** `vibe/zulutek-mix-siren-spec-7f3a21` MERGÉE (`ecdb43b`) — doc spec **mixeur 4 tranches unifié APK/FM-1 (VOICE/DRUMS/LOOP/SIREN)** : `docs/ZULUTEK-MIX-SIREN.md`.
- **PR #36** `vibe/zulutek-mix-siren-apk-1f7cfe` (équipe B, MERGED `16bc6e1`) — implémentation APK de la spec #35 : strips `ZTEK_STRIP_VOICE/DRUMS/LOOP/SIREN` (`ZTEK_NSTRIP`=4, baie inchangée), VOICE = bus voix unique, tranche SIREN vol↔`siren_lvl`/mute↔siren off (aucun nouveau param ZTF), migration presets magic `ZTP1`→`ZTP2` + remap `strip_par` (sizeof POD inchangé 1284 o), `STRIP_NAMES` Kotlin, doc `docs/ZULUTEK.md`.
- **PR #40** `vibe/zulutek-apk-landscape-1f7cfe` (équipe B) — **APK en paysage comme le FM-1** (objet horizontal) : suppression du verrou `screenOrientation="portrait"` du manifeste initial (commit `7fa5491`, restauration de branche — jamais documenté, contradictoire avec « clone de la surface du FM-1 »), `sensorLandscape` + `configChanges` sur MainActivity (pas de recréation à la rotation, audio continu), `LcdView` ajusté par la hauteur en paysage, docs (`ZULUTEK.md` ligne orientation, `FM1-RESSOURCES.md` références visuelles FM-1 en ligne : manuel, Ars Technica, fm1-emu, firmware hub). Patch 0011/0019 FM-1 reste à écrire côté équipe A après le régime SRAM (PR #34/#37).
- **Session 2026-11-21 ~15:10Z (VibeCode)** :
  - **PR #37** `vibe/fm1-patch-0019-0020-mix-sram-7f3a21` — patches **0019** (page MIX 15, 4 tranches VOICE/DRUMS/LOOP/SIREN unifiée APK #36), **0020** (régime SRAM : 37 Ko libres, `.fwsc` 616 059 o buildable), **0021** (ZPRE `'ZTP2'` accepté/exporté côté FM-1, miroir APK), **0022** (fix `settings_ok` `page < NPAGES` — bug réel : presets sauvés sur page ≥ 8 perdus au reboot ; re-baseline `tests/options.txt` drift index 18). Vérifié sandbox : 8934f53 + 0001..0022 `git apply` propre, build host OK, **9/9 scénarios sim 0 FAIL**, build `.fwsc` OK.
  - **PR #32 / #33** rebasées sur main (conflits `patches/README.md` résolus). Leurs CI rouges = `tools/make_icon.py` corrompu sur main (voir #41), pas leur code.
  - **PR #41** `vibe/fix-download-marker-line1-7f3a21` — fix urgent : ligne parasite `successfully downloaded text file (SHA: …)` en tête de 4 fichiers poussés via le connecteur (`make_icon.py`, `LcdView.kt`, `MainActivity.kt`, `SurfaceView.kt`) cassait la CI de toutes les PRs. CI verte run 44911528814.
  - **PR #39** doc statut FM-1 dans `docs/ZULUTEK-MIX-SIREN.md`. **PR #38** fermée (supersédée par #36).
  - **Merges faits 2026-11-21 ~15:40Z (ordre #41 → #32 → #33 → #34 → #37 → #39, `git merge --no-ff` + push main car `gh pr merge` bloqué par le proxy)** : main = `5174962`, CI main verte run 44914471333. Reste : flash FM-1 (backup AT64 + dump JTAG) et test ZPRE ZTP2 APK↔FM-1 de bout en bout.
- 🗺️ **Carte de la flash 1 MiB (zones interdites en écriture)** : `docs/FM1-FLASHMAP.md` — boot head `0x0..0x3FFF` et `key_mac 0xFF000` jamais écrits, zone app `0x4000..0x97000` réservée au loader OTA, VM/BTIF `0xE7000..0xE9FFF` interdits, nos fichiers ZTF validés dans `0xB0000..0xD3FFF`. Voir aussi `docs/FM1-HARDWARE.md` (FM-1 rev octobre 2026 = flash UC25HQ80 JEDEC `B3 60 14`, unbrick par `WL82 UBOOT1.00` USB ou dongle Seeed XIAO RP2040).

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
- **Module `app-zulutek-drums` archivé (PR #19)** : plus buildé (retiré de `settings.gradle` et des
  artifacts CI). La référence drum est la **page DRUMS 15 intégrée à `app-zulutek`** (parité firmware
  FM-1, patch 0007 page DRUM). Le dossier reste sur disque pour archive ; spec d'origine :
  `docs/ZULUTEK-DRUMS.md`.

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
