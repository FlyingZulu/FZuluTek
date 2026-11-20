# PROMPT-BOOT-IA.md — prompt pour générer l'anim d'intro (boot ~2 s) avec une IA générative

L'anim de boot du FM-1 (et variante APK) = le **ghetto blaster façon Nyan Cat dans l'intérieur de la
machine** + « ZULUTEK ». On génère l'image avec une IA, puis on la convertit pour le LCD 240×240 RGB565 :

```
python3 tools/lcd_image.py boot.png res/boot --w 240 --h 240
```

## Prompt (français)

> Pixel art fin, style console portable 16 bits (Game Gear), image carrée 240×240, fond noir profond.
> Au centre : un ghetto blaster rose façon Nyan Cat (corps pop-tart rose avec contour sombre, deux
> oreilles de chat triangulaires, deux gros haut-parleurs noirs avec cœur rose, une petite platine
> cassette servant de visage de chat : yeux blancs, museau rose, moustaches blanches, antenne en haut
> à droite) ; une traînée arc-en-ciel à 6 bandes (rouge, orange, jaune, vert, bleu, violet) sort de
> l'arrière gauche. Le ghetto blaster flotte au milieu de l'intérieur d'un appareil électronique :
> PCB vert très sombre avec pistes cuivrées, gros chip carré sombre broché sur les 4 côtés (style
> AC791N) derrière lui, câbles colorés (rose fluo, jaune vif, turquoise) qui serpentent, deux
> condensateurs cylindriques argentés, une LED verte allumée en haut à droite, vis aux coins.
> Éclairage dramatique cyberpunk : halos rose fluo rgb(255,45,160), jaune vif rgb(255,238,0) et
> turquoise rgb(0,255,224) sur les câbles et le chat. En bas, le texte « ZULUTEK » en police pixel
> épaisse, dégradé rose fluo vers jaune vif, léger effet de séparation RGB (décalage turquoise).
> Ambiance glitch vidéo vintage légère (quelques lignes de balayage sombres). Pas de flou, pixels nets.

## Prompt (English, pour les IA anglophones)

> Fine pixel art, 16-bit handheld console style (Game Gear), square 240×240 image, deep black
> background. Center: a pink Nyan Cat-style boombox (pink pop-tart body with dark outline, two
> triangular cat ears, two big black speakers with pink cores, a small cassette deck as the cat's
> face: white eyes, pink nose, white whiskers, antenna top right); a 6-band rainbow trail (red, orange,
> yellow, green, blue, purple) streams from the back left. The boombox floats inside an electronic
> device: very dark green PCB with copper traces, a large dark square pinned chip (AC791N style)
> behind it, colored wires (fluo pink, vivid yellow, turquoise) snaking around, two silver cylindrical
> capacitors, a lit green LED top right, corner screws. Cyberpunk dramatic lighting: fluo pink
> rgb(255,45,160), vivid yellow rgb(255,238,0) and turquoise rgb(0,255,224) glows on the wires and the
> cat. Bottom: the text "ZULUTEK" in thick pixel font, fluo pink to vivid yellow gradient, slight RGB
> split (turquoise offset). Light vintage VHS glitch (a few dark scanlines). No blur, crisp pixels.

## Règles de conversion pour le LCD du FM-1

- Sortie visée : **240×240 RGB565** (ST7789 sur SPI1) — `tools/lcd_image.py` sort `boot.bin` (brut LE)
  et `boot.h` (`const uint16_t img[57600]`).
- Garder les aplats et les contours sombres : le LCD n'a pas d'anti-aliasing, le pixel art net passe mieux.
- Palette limitée à la charte : noir, blanc, rose fluo, jaune fluo, turquoise, rouge, ambre + arc-en-ciel
  du Nyan (les seules couleurs vives autorisées hors charte).
- Anim boot ~2 s côté FM-1 : fondu/entrée du décor PCB, le chat traverse, « ZULUTEK » apparaît en
  pop-in + shrink (règle charte : apparition plein cadre puis rétrécissement 400 ms).
