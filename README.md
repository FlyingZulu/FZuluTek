# ZuluTek

Projet ZuluTEK de Flying Zulu — cible matérielle : M-VAVE FM-1.

Firmware ATEK303 personnalisé (dérivé GPL-3.0 d'Animatek — crédits : Animatek / Flying Zulu)
+ APK de preview `app-zulutek`. Dépôt privé pendant le développement ; à l'ouverture :
code publié en GPL-3.0 + crédits Animatek, nouvelle clé de signature générée (l'actuelle
reste privée), spec ZuluLink jamais publiée (voir `docs/PRISE-EN-MAIN.md`).

## État des branches (maj 2026-11-20 ~02:05Z) — 4 PR draft ouvertes

- **PR #3** `vibe/zulutek-seq-plus-c9ef18` (`8af3450`) — **branche principale APK** : moteur complet
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
