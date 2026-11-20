# ZuluTek

Projet ZuluTEK de Flying Zulu — cible matérielle : M-VAVE FM-1.

Firmware ATEK303 personnalisé (dérivé GPL-3.0 d'Animatek — crédits : Animatek / Flying Zulu)
+ APK de preview `app-zulutek`. Dépôt privé pendant le développement ; à l'ouverture :
code publié en GPL-3.0 + crédits Animatek, nouvelle clé de signature générée (l'actuelle
reste privée), spec ZuluLink jamais publiée (voir `docs/PRISE-EN-MAIN.md`).

## En cours — branche `vibe/zulutek-seq-plus-c9ef18`

Pack « SEQ+ » + modulation 8/8 (voir `docs/ZULUTEK-SEQ-PLUS.md`) : double-tap SEQ = sous-mode
avancé (Turing Machine LOCK/LEN, MUT, PLAY NORM/REV/PING-PONG, RAND), mutation live via ENV/LFO,
page ENV = ENV1/ENV2 bouclables + 6 tools, page LFO = 4 LFO + 4 labos, matrice ASSIGN typée
façon VCV Rack (out→in, sens interdit + conseils, option HINTS).

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
