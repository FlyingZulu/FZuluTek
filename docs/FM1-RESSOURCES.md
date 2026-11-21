# Ressources FM-1 (M-VAVE) — skins, firmwares, docs

Recherche faite pour rapprocher l'UI de l'APK du look FM-1 et trouver des skins récupérables :

- **fm1-editor.com** — liste des firmwares FM-1 + émulateur navigateur (teste un .fwsc sans flasher) + installateurs. Plusieurs firmwares proposent des **schémas de couleurs LCD sélectionnables** (ex. 6 thèmes), le stock V15/V16 reste sombre avec accents couleur.
- **github.com/aroum/fm1-custom-fw** et **github.com/genkigenki/fm1-custom-fw** — hubs de ressources : firmwares communautaires, outils SysEx, flasher.
- **stanleygurvich.github.io/FMDB_FM1_FirmwareDatabase** — base de données des firmwares FM-1/SMK-37 (releases, éditeurs, capacités).
- **forum.m-vave.com** — forum officiel (section FM1 Open : SDK, workbench navigateur, sources GPL de la couche application).
- **m-wave.net** — page produit FM-1 (hardware : JieLi AC791N, écran 240×240 couleur, 8 encodeurs + potard master, 14 boutons, 27 touches).
- **Reddit r/MVaveFM1** — hub communautaire (40+ firmwares classés).
- Notre base : **github.com/animatek/FM1-Animatek** (ATEK303, sur fm1-base MPL-2.0).

## Représentations de la face avant du FM-1 (références visuelles pour l'UI de l'APK)

Le FM-1 est un **objet horizontal** (boîtier de table paysage) : panneau gauche = contrôles master, **écran TFT 240×240 au centre**, panneau droit = 4 encodeurs de paramètres KNOB1–K4 + boutons de fonction, **clavier 27 touches silicone en bas sur toute la largeur**, 8 molettes au total (7 encodeurs + MASTER), 14 boutons avec LED.

- **manuals.plus / device.report (manuel FM-1)** : schéma du panneau — « left panel with master controls, center TFT display, right panel with parameter knobs and function buttons », 27-key silicone keyboard, photos.
- **Ars Technica, octobre 2026** (« From impulse purchase to pocket studio: the M-Vave FM-1 ») : photo du produit en main + photos du panneau.
- **joakimeriksson/fm1-emu** : le panneau de l'émulateur navigateur/native est **dessiné d'après une photo de l'appareil** (knobrs qui tournent, touches/boutons qui s'enfoncent, LED et écran pilotés par le firmware) ; sur téléphone l'émulateur passe en paysage « pour un synth plus grand ».
- **rackears.io (M-VAVE FM-1)** : « The panel is drawn from a photo of the device, with turning knobs, keys and buttons that sink when pressed » (plugin VST3/AU/CLAP FM-1).
- **thefirmwarehub.com** : chaque firmware a une image carrée, recommandée comme **capture de l'écran 240×240 en PNG** ; bouton « Play in browser » vers fm1-emu.
- Conséquence pour l'APK : **paysage** (`sensorLandscape`), écran en haut, panneau (encodeurs + boutons) et clavier en bas — cf. branche `vibe/zulutek-apk-landscape-1f7cfe`.

Note : le LCD du FM-1 est 240×240 non tactile — d'où SAVE+K1/K4 pour le circuit bend côté hardware.
La charte zuluTek (fond noir, encre blanche monospace, accents fluo rose/jaune/turquoise, REC rouge, warnings ambrés)
est calquée sur la famille sombre du stock FM-1 ; l'aperçu APK rend déjà ce LCD 240×240 à l'échelle.

## Doc rendu écran FM-1 & outils réutilisables (2e passe)

- **Écran** : TFT **240×240 ST7789-class, RGB565 sur SPI1** (confirmé par le RE Felucca + posts Reddit PCB). Flash 1 Mo, ~430 Ko RAM. Le rendu zuluTek cible ce format : nos visuels LCD sont dessinés pour 240×240 RGB565.
- **m-vave/fm1_open** (officiel, GPL-3.0 + SDK binaire) : source app + **templates**, SDK, **FM1 Workbench** navigateur (flash SysEx, presets, samples), émulateur web. Toolchain JieLi 2.5.3 (pi32v2 clang) via `FM1_PI32_TOOLCHAIN`.
- **joakimeriksson/fm1-emu** : émulateur (navigateur + natif) qui fait tourner les firmwares FM-1 **inchangés** — idéal pour tester le patch ATEK303 sans hardware.
- **swe24-phon/fm1-projector** : miroir de l'écran du FM-1 sur PC (SysEx `F0 7D 'F' 'S' …`, lignes en runs RGB565) — debug de l'UI FM-1 sans regarder le petit écran.
- **ferluht/fm1-sampler** : projet APK↔FM-1 existant (FM-1 = surface de contrôle + 2e écran 240×240 dessiné en SysEx, **police pixel 3x**, `ui/Theme.h` synchronisé avec le thème téléphone) — bonne référence de protocole écran.
- **sbegueria/fm1-dev-wiki** : wiki communautaire dev FM-1/SMK-37. **AL-255/FM-1-RE** + hub aroum (`REVERSE_ENGINEERING.md`) : maps mémoire AC791N, OTA SysEx. **wero1414.github.io/atlas-fm-1** : atlas/généalogie des firmwares. **cotrino/fm1-betis-mode** : exemple de firmware thémé 240×240 RGB565 avec artwork embarqué.
- **DA retenue** : pixel art fin (style console portable 16 bits type Game Gear), généré par `tools/make_icon.py` (grille interne 240×240, redimensionnement au plus proche voisin) — icône launcher + splash « ghetto blaster Nyan Cat + ZULUTEK cyberpunk ».
