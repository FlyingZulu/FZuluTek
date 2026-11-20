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
