# ZuluTek & Zulu Kontrol — charte graphique (APK preview, compagnon tablette, firmware FM-1)

L'écran du FM-1 est **240×240**, monochrome avec accents fluo. L'APK `app-zulutek` (LcdView)
imite cet écran à l'identique : **mêmes règles, mêmes couleurs, mêmes animations** côté APK
et côté firmware (patch `animatek/FM1-Animatek`). Le compagnon tablette **Zulu Kontrol**
(`FlyingZulu/FZulu-Control`, dépôt privé) applique **la même charte à l'identique strict** — un seul design pour tout
l'écosystème Zulu, comme iOS sur iPhone et iPad (section « Zulu Kontrol » plus bas).

**Rendu fidèle à l'écran FM-1 (règle « simulateur matériel », demande 2026-11-21)** : l'APK
`app-zulutek` ne se contente pas d'appliquer les mêmes règles que le FM-1 — il **rend l'UI
comme le ferait un simulateur matériel** (façon fm1-emu) : tout est dessiné dans le
**framebuffer natif 240×240** de l'écran **ST7789** (1 unité de dessin = 1 pixel matériel),
 chaque pixel est **quantifié en RGB565** (5R/6V/5B, le format réel du panneau : banding
fin sur les accents fluo, exactement comme sur le matériel), puis l'image est agrandie **au
plus proche voisin** (aucun lissage), avec une **trame discrète des interstices entre
pixelsphysiques** (visible uniquement quand un pixel logique fait ≥3 px à l'écran). Les
14 retours anticipés (captures, pages) passent par le même pipeline. **RÈGLE POUR TOUT
PROJET FUTUR ZULU** : toute app qui simule un écran Zulu rend ses pixels exactement comme
le panneau simulé (résolution native, format couleur réel, nearest).

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

**Remix Hello Kitty × Nyan Cat × Ghetto Blaster (v3)** dans la même charte : le corps du
blaster porte des **zébrures de zèbre EN TONS DU BLASTER** (jamais noir/blanc) + une
**trame pop art halftone** (petits points décalés), la platine-visage remontée et élargie
est **ZÉBRÉE noir sur clair** `rgb(218,222,232)` où **LES DEUX HP XXL SONT LES YEUX**
collés aux bords dans un cadre noir élargi (rondelle très claire `rgb(232,236,244)` +
membrane `rgb(148,154,168)` + cœur blanc + reflets kawaii), **truffe OVALE contourée**
Hello Kitty (jaune fluo à liseré sombre), joues roses, **TROIS moustaches par côté avec
ombre noire**, oreilles **blanches** à bord noir fin, **LECTEUR CASSETTE = LA BOUCHE**
(étiquette, fenêtre bande + 2 bobines, 4 vis, boutons **REC/PLAY/STOP**), antenne fine
collée au blaster, traînée arc-en-ciel étoilée. Plus de poignée, plus de sourire trait.
Line-art pixel art fin + accents **rose fluo / jaune fluo / turquoise**.

**Titre d'intro : ÉTIQUETTE COMPACTE façon logo Game Boy remixée Zulu Cat** — DEUX lignes
pixel art 5x9 CONDENSÉES À EMPATTEMENTS, **JUSTIFIÉES à la même largeur** et SERRÉES
pour former UNE étiquette compacte, plus petite que le logo, posée en sticker : « ZULU »
sur « TEK » sous le boombox dans le splash (jaune fluo `rgb(255,238,0)`, liseré noir,
ombre turquoise `rgb(0,255,224)` en bas-droite, légère arche sur la 1re ligne) ; côté Zulu
Kontrol, l'IntroView dessine la même étiquette pixel art « ZULU » sur « KONTROL », avec
les petites étoiles blanches de la traînée du logo.
Le prompt pour IA générative vit dans `docs/PROMPT-BOOT-IA.md`, l'outil de conversion
image → LCD (240×240, 1 bit + accents) dans `tools/lcd_image.py`.

**Crédit** : pendant le splash, un petit **« Flying Zulu »** s'affiche tout en bas de
l'écran, centré, gris atténué, police mono (comme un générique de fin de film).

## Zulu Kontrol (compagnon tablette — FlyingZulu/FZulu-Control)

L'app tablette **Zulu Kontrol** (console Vankyo, module `zkontrol`, package `com.zulukontrol`,
label **ZKontrol**) applique **la même charte à l'identique strict** — un seul design
pour tout l'écosystème Zulu (comme iOS sur iPhone et iPad) : mêmes couleurs, mêmes durées,
même dessin. Fond sombre, encre mono, accents fluo ZU/LU/turquoise/REC/ambre **uniquement
pour les états** (thème `ZcDark`/`ZcTheme`, `Theme.kt`). Aucune variante :

- Intro : étiquette compacte pixel art **« ZULU » sur « KONTROL »**, deux lignes 5x9 à
  EMPATTEMENTS **justifiées à la même largeur** et serrées, façon logo Game Boy remixé
  Zulu Cat, plus petite que le logo — **jaune fluo `rgb(255,238,0)`**, liseré noir fin,
  **ombre turquoise `rgb(0,255,224)`** en bas-droite, légère arche sur « ZULU », petites
  étoiles blanches autour de l'étiquette ; ~2 s, tap = passer ; même pixel art fin que
  le boot ZuluTek.
- Crédit **« Flying Zulu »** : petit, **en bas, centré, dans le splash** — identique ZuluTek
  (gris atténué, police mono). Aucun crédit permanent dans l'app.
- Mode VHS : scanlines **uniquement sur le noir** (blend LIGHTEN, gris très sombre), les
  plus **fines et discrètes** possible : lignes 1 px tous les 3 px (`VhsOverlay`), jamais
  sur le flash inversé ; le glitch est **réactif aux moteurs audio** — les notes (VCO),
  paramètres et blips du `LocalEngineLink` envoient des pulses (`onPulse` câblé dans
  `MainActivity`) qui densifient brièvement la trame et font défiler une bande de
  tracking, sans jamais gêner la lecture des paramètres. ON/OFF global.
- Nyan Cat (`NyanView`) : **le** remix officiel **v3**, géométrie strictement identique au
  `drawCat` de FZuluTek (zébrures en tons du blaster + trame halftone, platine-visor
  zébrée claire, yeux = HP XXL collés aux bords à cadre noir élargi, truffe ovale
  contourée, 3 moustaches ombrées par côté, joues, oreilles blanches à bord fin, lecteur
  cassette = bouche REC/PLAY/STOP, antenne fine, traînée arc-en-ciel 6 bandes + étoiles) ;
  blaster **menthe ZK** `rgb(175,255,240)` / croûte `rgb(46,210,180)`, comme l'icône —
  aucun autre chat, aucune variante.
- **Étiquettes Game Boy partout** : TOUS les éléments apparents de l'UI — labels des
  pads/kits/widgets qui pop dans les menus ET **titres de toutes les pages/menus** —
  utilisent la typo pixel **5×9 à EMPATTEMENTS** de l'étiquette de titre (`PixelFont.kt`
  côté ZK, `PixFont` intégré au `LcdView` côté ZT, mêmes glyphes que `TITLE_GLYPHS` de
  `make_icon.py`) : un mot court = une ligne, un mot composé (« MIDI LOOP ») = DEUX
  lignes JUSTIFIÉES à la même largeur, à l'échelle du contrôle. Couvert : PadBar, KitBar,
  ControlZone, OptionsMenu (sections + items en `PixelLabel`), en-tête du miroir FM-1 de
  LcdZone, **tous les titres de pages du LcdView ZuluTek** (SMP, LOOP, SNAP, KIT, XY, FX,
  SIREN, DRUMS, MIX, PATCH, OPTIONS, FINE, CABLES, LINK, SEQ+… + barre ZULUTEK + grande
  valeur du panneau pop). **RÈGLE POUR TOUT PROJET FUTUR ZULU** : toute nouvelle app/écran
  hérite de cette typo pour ses titres et labels apparents (police pixel 5×9 à
  empattements, glyphes `TITLE_GLYPHS`) ; seules les micro-lignes d'aide denses (rappels
  de touches, listes longues) restent en MONOSPACE système pour la place. Les accents sont
  pliés (É→E), caractère inconnu → '?'.
### Icônes des apps : un dessin, des initiales

Chaque app Zulu garde **la même icône** (le remix officiel généré par `tools/make_icon.py`,
strictement identique dans tous les dépôts), **plus un petit cartouche d'initiales** en bas
à droite (pixel art blanc sur fond sombre, liseré à la couleur d'accent de l'app), pour ne
pas confondre les apps sur l'écran d'accueil. La teinte du fond varie très légèrement entre
apps (imperceptible seule, repérable côte à côte) :

- **ZT** (ZuluTek) — liseré rose fluo, fond `rgb(10,10,16)`
- **ZK** (Zulu Kontrol) — liseré turquoise fluo, fond `rgb(9,12,16)`
- **ZD** (Drums) — liseré jaune fluo

**Couleur du ghetto blaster par app** : le dessin reste identique, seule la teinte du corps
varie — **ZT** rose `rgb(255,158,222)` / croûte `rgb(226,74,168)` ; **ZK** menthe
`rgb(175,255,240)` / croûte `rgb(46,210,180)` ; **ZD** (Drums) : tambour, pas de blaster.

Toute nouvelle app suit le même schéma : mêmes cotes, initiales + accent dédiés. Le splash
reste sans badge : le crédit « Flying Zulu » est au bas de l'écran.
- Veille : après le délai d'inactivité → **fond « intérieur du FM-1 »** : le décor PCB
  pixel art du `draw_pcb` de `tools/make_icon.py` (mêmes cotes : chip CPU épinglé, pistes
  cuivre, câbles colorés, condensateurs, LED, vis) en grand écran très sombre, parcouru
  par le chat qui se balade avec un **effet lampe de poche** — un halo radial révèle le
  circuit autour de lui — et des pulses de signal turquoise le long des câbles
  (`NyanView`, bitmap grille 240 agrandie au plus proche voisin). Gyro armé = le chat
  glisse selon le tilt (niveau à bulle, fond noir). Désactivable.
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
  + `drawable-nodpi/splash_boombox.png` (le remix Hello Kitty × Nyan Cat × Ghetto Blaster v3). Splash ~1,9 s :
  boombox zébré pop en tons du blaster + trame halftone (couleur du blaster par app),
  visage de chat sur la platine zébrée où les yeux-HP XXL sont collés aux bords du cadre
  noir, truffe ovale, lecteur cassette en bouche (REC/PLAY/STOP), traînée arc-en-ciel
  étoilée, tap = passer. Étiquette de titre « ZULU » sur « TEK », pixel art 5x9 à EMPATTEMENTS : DEUX lignes
  JUSTIFIÉES à la même largeur, serrées en bloc compact façon logo Game Boy (plus
  petite que le logo), jaune fluo `rgb(255,238,0)`, liseré noir fin, ombre turquoise
  `rgb(0,255,224)` en bas-droite (`draw_title` dans `tools/make_icon.py`).
- Côté LCD 240×240 du FM-1 : mêmes règles, dessin direct en pixels (RGB565, ST7789) ; voir
  `docs/FM1-RESSOURCES.md` (émulateur fm1-emu, miroir d'écran fm1-projector, police pixel 3x de fm1-sampler).

## Façade simulée (SurfaceView, APK et compagnon)

La façade reproduit le panneau canonique du FM-1, dessiné d'après l'art de référence de
l'émulateur fm1-emu (github.com/joakimeriksson/fm1-emu, crates/fm1-live/src/panel.rs, lui
même d'après le diagramme Felucca) : canevas logique 1280x820 mis à l'échelle au centre de
la vue. Disposition : MASTER/SELECT/PRESETS/ALGORITHM à gauche avec OCT-/OCT+ dessous dans
leur tray, bezel LCD au centre (le LcdView s'affiche au-dessus), KNOB1..4 puis les 12
boutons carrés en 2 rangées de 6 dans le tray droit (FX SCL ENV LFO EDIT GLO / HOME SAVE
ARP SEQ PLAY REC, PLAY+REC entourés), et les 27 touches pill F3..G5 en bas (16 blanches =
slots, 11 noires = notes, noir d'abord au hit-test). Thème « black-green », celui de la
machine : corps rgb(58,58,61)/rgb(46,46,48), trays rgb(117,192,183), sérigraphie
rgb(160,216,208), boutons et pills menthe rgb(101,198,184)/rgb(82,176,160) à encre sombre
rgb(30,56,48), ombres et enfoncement (offset 2-3 px) quand enfoncé. Les états de la charte
teintent la capsule en ambre (vert pour PLAY), le pop d'encodeur fait un halo ambre, et les
pills chauffent à l'ambre selon slot/rec/sélection. La sérigraphie des encodeurs est
statique (SELECT/PRESETS/ALGORITHM/KNOB1..4) et le relabel dynamique s'affiche dessous.
Le dispatch reste par index SurfaceListener (l'ordre matrice machine est converti).
La façade suit le style MATÉRIEL ; le LCD garde la charte noir/encre/fluo inchangée.

**Aménagement DA Zulu (21/11, Flying Zulu)** : la façade garde la disposition Felucca mais
porte la charte Zulu, pas le thème menthe de la machine : fond et pièce noirs rgb(9,12,16),
sérigraphie menthe pâle rgb(159,224,214), bonbons et pills turquoise fluo
rgb(0,255,224)/rgb(0,166,146) à encre noire, marque « ZULUTEK » rose fluo rgb(255,45,160)
en bas de façade. Les états (ambre, vert PLAY, chaleur des pills) restent ceux de la charte.

## Zulu Kontrol : widgets
Les widgets du controleur (boutons carres, pads, knobs) suivent la meme recette bonbon que la facade : ombre portee legere, rrect arrondi avec degrade vertical (accent vers accent assombri), encre sombre candy pour les labels, halo translucide a l'appui. Knob = jupe grise degradee (skirtHi vers skirtLo) + pointeur dans l'accent du widget, balayage -135 a +135 degres. Les pads chauffent (degrade eclairci) pad appuye.
## Skin de facade : OPTIONS > SKIN (APK preview)
La facade accepte deux palettes, choisies par la ligne SKIN de la page OPTIONS (SEL navigue, ALG bascule, persistee dans prefs, APK only, identique sur les 3 refs). DA ZULU (defaut) : noir charte, bonbons/pills turquoise fluo, marque ZULUTEK rose fluo. MAISON : le FM-1 custom maison peint de Flying Zulu (photo du 21/11) : corps vert fluo (0x00D90C vers 0x039004), fond vert-noir, plateaux/trays bleu vif 0x0A62D8, bonbons orange 0xFC7A03 vers 0xD85700 a encre brune 0x301202, pills bleu profond 0x2F8CFF vers 0x0A48A8 a ridee blanche, knobs jupe vert fonce, marque ZULUTEK rouge sang 0xF90910, serigraphie blanche 0xECFDF0. La geometrie Felucca et les etats charte (LED ambre, vert PLAY, chaleur des pills) ne changent pas : seule la palette change (couleurs extraites de la photo).
