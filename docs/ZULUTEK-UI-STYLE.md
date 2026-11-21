successfully downloaded text file (SHA: de0902c9eb79242cf0eb10eb7f7aebeae81fb3cc)
# ZuluTek & Zulu Kontrol — charte graphique (APK preview, compagnon tablette, firmware FM-1)

L'écran du FM-1 est **240×240**, monochrome avec accents fluo. L'APK `app-zulutek` (LcdView)
imite cet écran à l'identique : **mêmes règles, mêmes couleurs, mêmes animations** côté APK
et côté firmware (patch `animatek/FM1-Animatek`). Le compagnon tablette **Zulu Kontrol**
(`FlyingZulu/FZulu-Control`, dépôt privé) applique la même charte — ses variantes sont
documentées dans la section « Zulu Kontrol » plus bas.

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

La baie de patch ajoute sa propre gamme de couleurs par module (voir plus bas).

## Règles

- **Fond noir, encre blanche**, texte mono ; les accents fluo servent **uniquement** aux états
  importants (témoins ZU/LU, REC, cadres de fenêtres, icônes de mode).
- **Toute fenêtre / icône qui apparaît** (caméra snap, logo REC, proc REC+GLO, singe à la
  connexion, animation d'intro) : **apparition plein cadre puis rétrécissement sur 400 ms**
  après un bref maintien (1,2–1,6 s) — même esprit partout. Tap du pad pendant une capture :
  rétrécissement immédiat, toujours 400 ms.
- **Panneau pop d'un paramètre touché** : cadre turquoise fluo, **valeur en grand au centre**
  (prioritaire), nom du contrôle au-dessus en atténué, état en dessous en ambre
  (S = snap armé, L = page LFO, E = page ENV ; côté firmware : X = LOG/N1) + message en cours.
  Si le contrôle sert à la **navigation** (SELECT), c'est la **cible** qui s'affiche au centre
  (PAGE x / SLOT n / OPT … / LEN xB / STEP n) à la place du pourcentage.
  Implémentation APK : `MainActivity.popPanelInfo()` remplit `LcdState.popVal/popState`,
  `LcdView.onDraw` dessine le panneau (durée = le pop du widget, 1,2 s).
- **Procédures armées** : les boutons déclencheurs clignotent (260 ms) jusqu'à fermeture.
  Rappel lock clavier MIDI : après **20 s de silence clavier**, HOME pulse une fois
  (260 ms, turquoise) toutes les 20 s, jusqu'à la prochaine note.
- Icônes en **line-art** 1,6–2 px (caméra, caméra ciné, micro barré, cadenas, boucle 🔁, singe).
- Option VHS (OPTIONS > LUXE) : scanlines légères **uniquement sur le noir**, jamais sur le
  flash inversé. Côté APK : trame dessinée en blend LIGHTEN avec un gris très sombre
  (`scanPaint`) — seul le noir s'éclaircit, l'encre blanche et les accents restent intacts.
- Flash (snap pris) : inversion blanc/noir le temps du flash (120 ms).

## Baie de patch : une couleur par module

Chaque module de la baie possède **sa propre couleur stable** (`LcdState.patchCableColors`,
12 teintes) : les sources prennent leur couleur par `cableColor()` (index source), les
modules entrants par `modColor()` (décalage +8 pour ne pas recouper les sources).
Usages : cadres des boîtes de la vue 0 (si câbles présents), noms des boîtes, en-têtes de
modules dans la liste IN, et toutes les mentions de câbles. Les couleurs :

`rgb(255,45,160)` · `rgb(255,238,0)` · `rgb(0,255,224)` · `rgb(255,60,60)` ·
`rgb(255,160,40)` · `rgb(150,80,255)` · `rgb(60,220,90)` · `rgb(60,160,255)` ·
`rgb(255,120,200)` · `rgb(120,200,255)` · `rgb(220,255,120)` · `rgb(255,200,90)`

## Intro (boot, ~1,9 s)

**Remix Ghetto Blaster × Nyan Cat** dans la même charte : le corps du walkman est une
pop-tart rose (croûte + sprinkles), la platine cassette est devenue le visage du chat
(yeux, joues roses, sourire, moustaches), oreilles, poignée, antenne, deux HP, traînée
arc-en-ciel étoilée. Line-art pixel art fin + accents **rose fluo / jaune fluo / turquoise**.
Le prompt pour IA générative vit dans `docs/PROMPT-BOOT-IA.md`, l'outil de conversion
image → LCD (240×240, 1 bit + accents) dans `tools/lcd_image.py`.

**Crédit** : pendant le splash, un petit **« Flying Zulu »** s'affiche tout en bas de
l'écran, centré, gris atténué, police mono (comme un générique de fin de film).

## Zulu Kontrol (compagnon tablette — FlyingZulu/FZulu-Control)

L'app tablette **Zulu Kontrol** (console Vankyo, module `zkontrol`, package `com.zulukontrol`,
label **ZKontrol**) applique **la même charte, unifiée** : fond sombre, encre mono, accents
fluo ZU/LU/turquoise/REC/ambre **uniquement pour les états** (thème `ZcDark`/`ZcTheme`,
`Theme.kt`), **5 skins**. Variantes assumées :

- Intro **« Zulu Kontrol »** ~2 s, split RGB ; même pixel art fin que le boot ZuluTek.
- Crédit **« Flying Zulu »** : petit, **en bas à droite** côté Zulu Kontrol (en bas, centré,
  côté ZuluTek) — même gris atténué, même police mono.
- Mode VHS : scanlines **sur toute l'app**, ON/OFF global (là où le FM-1 les réserve à
  l'option LUXE, page par page).
- Nyan Cat (`NyanView`) : même remix Ghetto Blaster × Nyan Cat, **variante galop** — le chat
  pop-tart (croûte + sprinkles, oreilles, yeux, joues roses, moustaches) porte la boombox
  sur le dos (2 HP, cassette, poignée, antenne, LED REC clignotante 260 ms), pattes en galop,
  traînée arc-en-ciel 6 bandes ondulée, étoiles scintillantes.
- Veille : 30 s sans toucher → Nyan en grand écran qui se balade ; gyro armé = le chat glisse
  selon le tilt (niveau à bulle). Désactivable.
- Prompt pour UI designer externe : `docs/PROMPT-UI-ZKONTROL.md` (FZulu-Control), dérivé de
  cette charte. Spec complète : `docs/CONTROL.md`.

## Fichiers

- `app-zulutek/.../LcdView.kt` — palette (`FLUO_PINK/YEL/TURQ`, `REC_RED`, `AMBER`) + toutes les
  animations (panneau pop paramètre, icônes, singe, scanlines VHS, caméra 400 ms,
  couleurs par module de la baie).
- `app-zulutek/.../MainActivity.kt` — `popPanelInfo()` : valeur/cible du panneau pop.
- `app-zulutek/.../SurfaceView.kt` — rappel HOME 20 s (pulse turquoise 260 ms).
- `app-zulutek/.../SplashActivity.kt` — splash ~1,9 s + crédit « Flying Zulu » en bas.
- `docs/PROMPT-BOOT-IA.md`, `tools/lcd_image.py`, `tools/make_icon.py` — génération d'images
  conformes à cette charte.
- Le patch firmware reprend les mêmes constantes et les mêmes durées d'animation.

## Pixel art (DA visuels embarqués)

- Les visuels embarqués (icône launcher, splash, art d'intro FM-1) sont en **pixel art fin**,
  style console portable 16 bits (Game Gear) — pas le gros pixel Game Boy : grille interne **240×240**
  (comme l'écran du FM-1), redimensionnement **au plus proche voisin** (pixels nets, jamais de lissage).
- Générateur : `tools/make_icon.py` (pur Python, PNG écrits en zlib) → `mipmap-*/ic_launcher.png`
  + `drawable-nodpi/splash_boombox.png` (le remix Ghetto Blaster × Nyan Cat). Splash ~1,9 s :
  boombox-pop-tart rose (sprinkles), visage de chat sur la platine, traînée arc-en-ciel
  étoilée, tap = passer.
- Côté LCD 240×240 du FM-1 : mêmes règles, dessin direct en pixels (RGB565, ST7789) ; voir
  `docs/FM1-RESSOURCES.md` (émulateur fm1-emu, miroir d'écran fm1-projector, police pixel 3x de fm1-sampler).
