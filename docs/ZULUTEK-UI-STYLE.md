# ZuluTek — charte graphique (APK preview + firmware FM-1)

L'écran du FM-1 est **240×240**, monochrome avec accents fluo. L'APK `app-zulutek` (LcdView)
imite cet écran à l'identique : **mêmes règles, mêmes couleurs, mêmes animations** côté APK
et côté firmware (patch `animatek/FM1-Animatek`).

## Palette (constantes dans `LcdView.companion`)

| Usage | Couleur |
|---|---|
| fond | noir `0xFF000000` |
| encre principale | blanc, police **MONOSPACE** |
| ZU (témoin engagé) | rose fluo `rgb(255,45,160)` |
| LU (témoin engagé) | jaune fluo `rgb(255,238,0)` |
| pops / fenêtres / cadenas off | turquoise fluo `rgb(0,255,224)` |
| REC / alertes clignotantes | rouge `argb(255,255,60,60)` (éteint : `argb(255,96,32,32)`) |
| avertissements (boucle, état) | ambre `argb(255,255,200,60)` |
| atténué | gris |

## Règles

- **Fond noir, encre blanche**, texte mono ; les accents fluo servent **uniquement** aux états
  importants (témoins ZU/LU, REC, cadres de fenêtres, icônes de mode).
- **Toute fenêtre / icône qui apparaît** (caméra snap, logo REC, caméra ciné REC+GLO, pop de
  paramètre, singe à la connexion, animation d'intro) : **apparition plein cadre puis
  rétrécissement sur 400 ms** après un bref maintien (ex. 1,2–1,6 s) — même esprit partout.
- **Panneau pop d'un paramètre touché** : cadre turquoise fluo, **valeur en grand au centre**
  (prioritaire), nom du contrôle + paramètre au-dessus en atténué, état en dessous en ambre
  (S = détaché d'un snap, L = piloté par LFO, E = ENV/EF, X = LOG/N1) + message en cours.
  Si le contrôle sert à la **navigation** (SELECT), c'est la **cible** qui s'affiche au centre
  (PAGE x / SLOT n / OPT … / LEN xB / STEP n) à la place du pourcentage.
- **Procédures armées** : les boutons déclencheurs clignotent (260 ms) jusqu'à fermeture.
  Rappel lock clavier MIDI : HOME clignote toutes les 20 s.
- Icônes en **line-art** 1,6–2 px (caméra, caméra ciné, micro barré, cadenas, boucle 🔁, singe).
- Option VHS : scanlines légères **uniquement sur le noir**, jamais sur le flash inversé.
- Flash (snap pris) : inversion blanc/noir le temps du flash.

## Intro (boot, ~2 s)

Ghetto blaster cyberpunk + « ZuluTek » dans la même charte : line-art blanc, accents
**rose fluo / jaune fluo / turquoise** (ZU/LU + turquoise), apparition pop puis fondu.
Le prompt pour IA générative vit dans `docs/PROMPT-BOOT-IA.md`, l'outil de conversion
image → LCD (240×240, 1 bit + accents) dans `tools/lcd_image.py`.

## Fichiers

- `app-zulutek/.../LcdView.kt` — palette (`FLUO_PINK/YEL/TURQ`, `REC_RED`, `AMBER`) + toutes les
  animations (pop paramètre, icônes, singe, intro).
- `docs/PROMPT-BOOT-IA.md`, `tools/lcd_image.py` — génération d'images conformes à cette charte.
- Le patch firmware reprend les mêmes constantes et les mêmes durées d'animation.

## Pixel art (DA visuels embarqués)

- Les visuels embarqués (icône launcher, splash, art d'intro FM-1) sont en **pixel art fin**,
  style console portable 16 bits (Game Gear) — pas le gros pixel Game Boy : grille interne **240×240**
  (comme l'écran du FM-1), redimensionnement **au plus proche voisin** (pixels nets, jamais de lissage).
- Générateur : `tools/make_icon.py` (pur Python, PNG écrits en zlib) → `mipmap-*/ic_launcher.png`
  + `drawable-nodpi/splash_boombox.png`. Splash ~1,9 s : ghetto blaster Nyan Cat (pop-tart rose, oreilles,
  traînée arc-en-ciel) + « ZULUTEK » cyberpunk (dégradé rose→jaune, split RGB turquoise, glitch VHS),
  tap = passer.
- Côté LCD 240×240 du FM-1 : mêmes règles, dessin direct en pixels (RGB565, ST7789) ; voir
  `docs/FM1-RESSOURCES.md` (émulateur fm1-emu, miroir d'écran fm1-projector, police pixel 3x de fm1-sampler).
