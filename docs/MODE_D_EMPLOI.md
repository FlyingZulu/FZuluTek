# ZuluFx — Mode d'emploi (v0.41)

## Démarrage
1. Ouvrir l'app (écran en paysage).
2. Le moteur audio démarre automatiquement. Si le message « AUDIO : demarrage impossible » apparaît, fermer les autres apps audio et relancer.
3. « TEMPLATE DE BASE CHARGE » s'affiche : l'app est dans un état neutre (aucun son qui traîne).

## Organisation de l'écran
- **Haut gauche** : pad XY tactile (cercles A et B = axes F et Z).
- **Barre haute** : boutons FX, TOOLS, PLAY, REC, MUTE, SHIFT, UNDO, VU-mètre, écran BPM.
- **Gauche** : faders ZU/LU, FLY, MIX, entrée ligne (LINE-IN).
- **Centre** : grille de 12 pads (banques), éditeur de pas, PUNCH, sélecteur FX.
- **Panneaux** (ouverture par bouton) : FX SELECT, GAMME, PROJET, ARPEGGIATEUR/LFO, SAMPLE, PANIQUE.

## Pad XY (cercles A/B)
- Toucher le pad = jouer le synthé / déclencher des hits selon le moteur actif.
- Le cercle **A** = axe **F** (jaune), le cercle **B** = axe **Z** (rose) ; chaque axe pilote les paramètres qui lui sont affectés.
- Boutons **F** / **Z** : ON/OFF de chaque axe.
  - Appui long sur F : bascule MOTION (enregistrement du mouvement de l'axe) et REC WET.
  - Appui long sur Z : idem pour l'axe Z.

## Boutons principaux
- **FX** : ouvre la page FX. Dans la page : TAP = ON/OFF · SHIFT+TAP = RÉGLAGES · SYNTH = jouer sur le pad. Appui long sur FX : route/retire l'effet du MASTER.
- **TOOLS** : ouvre le menu des outils en grille — ARP, GRID (percussions/samples joués en grille, SHIFT+TOOLS change le mode), CLAVIER, COMMUNICATION.
- **CLAVIER (outil)** : 2 octaves à la place du pad. Multi-cibles : taper un pad loop = la loop est coupée et jouée par le clavier (pitch aux touches) ; re-taper = détachée et la boucle repart. Taper un slot 1–9 qui clignote = son synthé/drum est joué par le clavier (les slots compatibles clignotent). Plusieurs cibles simultanées (synthé + loops ensemble). Couper l'outil (re-taper CLAVIER ou ×) détache tout et réanime les boucles.
- **PLAY / STOP** : démarre/arrête la lecture.
- **REC** : protocole d'enregistrement (voir ci-dessous).
- **MUTE** : coupe la sortie (MUTE ● = actif).
- **UNDO** : annule le dernier enregistrement (UNDO REC).
- **SHIFT** : maintenu = modifie la fonction des autres commandes ; verrouillable (le bouton clignote).
- **PANIQUE** (en haut du menu) : remise à zéro complète — « PANIQUE : TOUT EST CALME ».

## Enregistrement (REC)
1. Taper **REC** → « REC : ARMÉ ».
2. Taper un **pad (1 à 14)** = destination de la banque → « PAD n ARMÉ — REC = LANCER ».
3. Taper **REC** = démarrer la capture ; taper le pad armé ou REC = stop (« REC STOP »).
4. REC WET (capturer avec les effets) : appui long sur F (axe F) ou Z (axe Z).
5. MOTION : enregistrer le mouvement d'un axe (appui long F/Z), puis relancer sur un pad.

## Scènes ZU / LU
- **TAP** sur ZU ou LU = changer de côté (gauche/droite) seulement.
- **Maintenir ZU ou LU (>0,5 s)** en réglant puis relâcher = capturer le snapshot sur la banque active du côté (« SNAPSHOT ZU : CHOISIR PAD »).
- Fader **ZU/LU** : fondu/morphing entre les deux scènes.
- Rangement par défaut (mode 12 pads) : 6 scènes de gauche = ZU, 6 de droite = LU.

## Pads (grille de 12)
- Tap = jouer la banque.
- SHIFT + tap (mode longueur) : régler la longueur de boucle (pads 1–9 = chiffres).
- FX maintenu + tap : longueur de boucle rapide.
- SHIFT + tap (mode sample) : ouvrir la page SAMPLE du pad.
- Double-tap SHIFT + pad : effacer la motion du pad.

## Projets / sauvegarde
- Page **PROJET** : TAP = CHARGER · APPUI LONG = SAUVER · DOUBLE-TAP = NOMMER · SWIPE = PAGE (4 pages).
- **Sauvegarde globale** : armer la sauvegarde puis taper un pad 1–4 (« GLOBAL n SAUVÉ »).
- **Chargement global** : SHIFT + MUTE maintenu + pad 1–4 de la rangée du bas.

## Nouveautés moteur (v0.42)
- **Synthés** : KIK8 (kick 808 : TUNE/PUNCH/DECAY), VOY (voix formantique : MODE < 50 % = robotique, > 50 % = humain avec vibrato et souffle), MAL (percussion modale type marimba/bois), BELL (cloche FM inharmonique).
- **Effets** : TEMPO (delay synchro BPM : 1/16, 1/16T, 1/16., 1/8, 1/8T, 1/4, 1/4., 1/2), PING (ping-pong stéréo), PLATE (réverb plaque), SHIM (shimmer, queue à l'octave), GATE (réverb gated).
- Tous ont leurs tirettes (SHIFT+TAP), sont snapshotables et mappables sur les axes F/Z, les LFO et l'ARP.
- **Punch-ins** : chaque déclenchement pousse les réglages complets de l'effet (tirettes) — un LFO branché dessus rend chaque punch-in légèrement différent. SHIFT + zone punch = page de réglages.
- **Longueur de boucle** : pendant que REC est armé (destination choisie, avant le lancement), la rangée 1–9 règle la longueur. Optionnel.
- **Boucles quantisées** : à l'arrêt manuel, la longueur est arrondie au temps le plus proche pour boucler proprement au tempo. Débrayable dans le menu (ENREGISTREMENT → BOUCLES QUANTISÉES).

## Communication entre tirettes (v0.43)
- Les tirettes d'un même **groupe** (slot 1–9) peuvent se parler : le mouvement d'une tirette influence une autre.
- **Maintenir une tirette** (~0,5 s) = ouvre la boîte **COMMUNICATION** : on voit ses connexions (source et destination) et on règle chaque connexion :
  - **INTENSITÉ** : force de l'influence (0–100 %)
  - **PROBABILITÉ** : chance que le message passe (100 % = toujours)
  - **HACHIS** : gate rythmique au tempo (OFF, 1/4, 1/8, 1/16, 1/32, 1/8T)
  - **RETARD** : la communication arrive en écho (jusqu'à 250 ms)
  - **DOUCEUR** : l'influence monte doucement (vent qui soulève les feuilles)
- Accès aussi via **OUTILS → COMMUNICATION → TIRETTES** (connexions du groupe actif + création de nouvelles connexions).
- Les connexions sont **sauvegardées par groupe** dans le projet. Un groupe n'influence pas les autres.

## BPM
- Glisser verticalement l'écran BPM = ajuster.
- Tapper dessus en rythme = tap tempo.

## Faders
- **ZU/LU** : blend/morph entre scènes.
- **FLY** : effet fly (voir fader dédié).
- **MIX** : niveau de mix (vert).
- **LINE-IN** : gain d'entrée micro/ligne.

## Dépannage
- **Grésillement / craquements** : réduire le nombre d'effets actifs, vérifier qu'aucune autre app n'utilise l'audio, débrancher/rebrancher le casque (le flux audio redémarre automatiquement).
- **Son figé** : PANIQUE (menu) puis relancer.
- **Pas de son** : vérifier MUTE, le fader MIX, et le volume média du téléphone.

## Édition des boucles / samples (v0.45)
- **SHIFT + pad de boucle** ouvre la page d'édition : PITCH, ATTAQUE, DECAY, TONE, DÉBUT, FIN, **STRETCH** (OFF/CLEAN/BEAT/RETRO), **PING-PONG** (lecture aller-retour), **DÉCALAGE** (décale le point de lecture de -2 à +2 slots, réglage fin).
- **Grille de 8 slots** (style Kaoss Pad 3) : dans cette page, les pads 1-8 s'éclairent en **violet**. Tap = activer/désactiver le slot ; seuls les slots allumés sont lus.
- **Maintenir un pad violet et glisser** : verticalement = **volume du slot** ; horizontalement = **probabilité** (le slot est joué 100 %, 95 %, 50 %... du temps).
- Les réglages de slots sont sauvegardés dans le projet.

## Édition des motions (v0.45)
- **SHIFT + pad de motion** ouvre la page d'édition :
  - **LONGUEUR** : cycle 1-19 temps ; boutons **×2** et **÷2**.
  - **MODE** : NORMAL, **CHAOS** (la lecture saute à des points imprévus de la motion), **INTENSITÉ** (la motion respire : l'amplitude monte et descend doucement).
  - **FORCE DU HASARD** : 0 % = sages comme des agneaux, 100 % = jumeaux maléfiques.
  - **EFFACER** : supprime la motion du pad.

## Style (v0.45)
- Menu → STYLE : **84** (crayonné), **RETRO**, **GAME BOY** (fond vert Game Boy, scanlines, angles droits — couleurs de l'app conservées).
- Menu → **FOND** : couleur de fond au choix (NOIR, NUIT, GB, BRUN, ARDOISE).
- Le petit chat a été retiré de l'interface.
- **GRILLE** : tirette de la page d'édition sample — à mi-course et plus, la grille de slots s'étend de 8 à **12 pads** (les nouveaux slots s'allument automatiquement).

## Séquenceur de pas / one-shot (v0.46)
- **ONE-SHOT** : tirette de la page d'édition sample — à mi-course et plus, le sample est lu en **one-shot** (une seule passe, pas de boucle) et la grille devient un **séquenceur**. Taper le pad de la banque relance la lecture.
- **GRILLE** : 8, 10 ou **12 pas** (tirette en 3 zones).
- **DÉBUT / FIN** : points de départ et de fin de lecture (valables boucle, ping-pong et one-shot).
- Par pas (pad violet) :
  - **Tap** = activer/désactiver ; **double-tap** = lecture **REVERSE** de ce pas (marqueur ◀).
  - **Glisser vertical** = volume ; **glisser horizontal** :
    - en mode **LOOP** (∞, violet) : décale le **point de départ global** de la boucle (tirette DÉCALAGE) ;
    - en mode **ONE-SHOT** (▶, orange) : décale le **point de lecture du pas** dans le temps.
  - **MUET maintenu + glisser horizontal** = probabilité (100 %, 95 %...).
  - En mode one-shot : **SHIFT + glisser vertical** = hauteur (±12 demi-tons), **SHIFT + glisser horizontal** = pan.
- **Distinction visuelle** : LOOP = pads violets + badge « ∞ LOOP » sur la grille ; ONE-SHOT = pads orange + badge « ▶ SEQ ». Le pad XY affiche le mode en cours (B1 · LOOP / B1 · ONE-SHOT).
- Tout est sauvegardé dans le projet (slots, reverse, volumes, probabilités, hauteurs, pans, décalages).

## Fenêtre d'aide sur le pad XY (v0.47)
- L'**écran BPM/VU** affiche en permanence **où on est** (aide-mémoire + messages) : MENU, FX RACK, SAMPLE B2, ÉDITION PARAMS · G3, REC → DESTINATION, LONGUEUR ?, TOOLS · CLAVIER, POW, ARP, SHIFT, SCÈNE G1... Le pad XY reste dédié au jeu XY.
- En haut du pad : le mode sample en édition (B1 · LOOP / B1 · ONE-SHOT).
- Menu → STYLE → **AIDE XY** : coupe l'aide-mémoire si on n'en a pas besoin.

## Panneau VU + BPM style Game Boy / Pocket Operator (v0.48)
- Le panneau (VU mètre + afficheur BPM) est refait en style **Game Boy / Pocket Operator** : fond DMG, segments verts, digits rétro, petite créature pixel qui clignote au tempo.
- Deux nouveaux boutons dans le panneau : **IN** (entrée) et **OUT** (sortie).
  - **Presser + glisser vertical** = niveau (IN = gain d'entrée, OUT = volume master).
  - **Glisser horizontal** = filtre : **low-pass vers la gauche**, **high-pass vers la droite** (affiché LP/HP sur le bouton).
  - **Double-tap** = filtre remis à zéro.
- Les glissés sur le VU mètre d'origine continuent de fonctionner (volume / entrée).

## Skins + fader ZU/LU (v0.49)
- **Fader ZU/LU** : plus fin (largeur fixe, centré) pour laisser de l'air au reste de la colonne.
- Menu → STYLE : 5 skins au choix — **84** (crayonné), **RETRO**, **GAME BOY**, **ELECTRIBE** (charbon métallique, lignes fines horizontales, vis assumées), **CAUSTIC** (bleu-gris nuit, grille de points discrète).
- Les petites vis des modules n'apparaissent plus que en skin **ELECTRIBE**, où elles sont dessinées assumées (métal franc).

## Console audio + capture des apps (v0.50)
- Menu → **AUDIO · CONSOLE** :
  - **Filtres IN / OUT** : LP / NEUTRE / HP (mêmes réglages que les boutons IN/OUT du panneau, synchronisés).
  - **Compression** : OFF / DOUX / PUNCH / DUR (compresseur master natif, sauvegardé dans le projet).
  - **Volume** : x0.5 / x1 / x1.5 / x2.
  - **CAPTURE APPS OFF/ON** : récupère le son des autres apps du téléphone (Koala Sampler, lecteur musique, jeux...) pour le sampler/resampler comme une entrée line-in. Nécessite Android 10+ ; une demande de confirmation apparaît à la première activation. Le son capté passe par toute la chaîne IN (profil d'entrée, filtre IN, EQ, gain IN) et s'enregistre avec REC comme n'importe quelle source.

## MIDI, logique modulaire, envelope follower, baie de patch (v0.51)
- **Menu → MIDI** :
  - **OUT ON/OFF** : envoie les CC vers les appareils MIDI du téléphone (apps virtuelles type Koala, interfaces USB...).
  - **CANAL G** : canal global (1-16).
  - **TEACH** : active le mode apprentissage — bouge n'importe quel contrôle (tirette, fader, XY, VOL, BPM...) et il reçoit le prochain CC libre. Le contrôle enverra ensuite sa valeur en MIDI à chaque mouvement.
  - **EFFACER MAP** / **APPAREILS** (liste) / **MAP : n** (nombre de contrôles mappés).
  - La map MIDI est sauvegardée dans le projet.
- **TOOLS → LOG** (logique modulaire) : 2 sources (XY X, XY Y, ENTRÉE) + 1 porte (AND/OR/XOR/NOT-A) → un paramètre. Cible choisie avec APPRENDRE TIRETTE (bouge la tirette visée).
- **TOOLS → ENV** (envelope follower) : le niveau d'entrée pilote un paramètre (cible = axes ou tirette apprise), avec INTENSITÉ et SENSIBILITÉ.
- **TOOLS → PATCH** : la **baie de patch** s'affiche à la place de l'écran XY — tous les branchements de la machine (axes, LFOs, ENV, LOGIQUE, liens COMMUNICATION, MIDI) avec des câbles colorés. Tap pour fermer.
- **Éditeur punch-in créatif** : appui long sur une zone punch → éditer son FX, son paramètre et sa valeur. Sauvegardé dans le projet.
- **Mini-clavier** : rangée de contrôles rapides sous les touches — FILTRE, ATQ, DEC (enveloppe des synthés).

## Layout live + skins réparés + toasts (v0.52)
- **Boutons verticaux** : la colonne de droite (entre le pad et la grille) regroupe désormais **FX, TOOLS, REC, UNDO, MENU** en haut, puis **F, Z, SHIFT, MUTE** en bas (ordre : F, Z, Shift, Mute).
- **Panneau VU/BPM** : en haut de la colonne de droite, aligné avec les boutons IN / VU / PLAY — toujours accessible, ne bouge plus.
- **Fader ZU/LU** : horizontal, **toute la largeur disponible** pour une grande course, hauteur fine (~40 px) pour ne pas voler de place. Boutons **ZU/LU plus gros**.
- **Toasts** : tous les messages (REC, modes, alertes...) s'affichent désormais **dans la zone dédiée de l'écran XY** (bandeau du bas), jamais en popup système. Ils restent ~2,5 s puis l'aide-contexte reprend.
- **Skins réparés** : les styles (84, RETRO, GAME BOY, ELECTRIBE, CAUSTIC) colorent désormais **toutes les surfaces** — boutons, pads, grilles, faders, panneaux d'édition, clavier — et plus seulement le fond. Le changement de style est visible immédiatement sur tout l'écran.

## Panneau VU/BPM direct (v0.52)
- **Plus de potards IN/OUT** : tout se règle directement sur les vues mètres du panneau haut-droit.
  - **Swipe vertical côté OUT** (barres de gauche) = volume master ; **côté IN** (barres de droite) = gain d'entrée.
  - **Swipe horizontal côté OUT** = filtre master (LP \u00e0 gauche / HP \u00e0 droite) ; **c\u00f4t\u00e9 IN** = filtre d'entr\u00e9e.
  - **Double-tap** sur un c\u00f4t\u00e9 = filtre du c\u00f4t\u00e9 remis \u00e0 z\u00e9ro. Les \u00e9tats LP/HP s'affichent en bas des barres.
- **Panneau \u00e9largi** (VU + BPM) pour un confort de lecture, avec un **petit chat pixel** qui se balance sur le tempo \u2014 clin d'\u0153il aux Pocket Operator.
- **PLAY** est plac\u00e9 juste \u00e0 gauche de l'\u00e9cran VU/BPM.

## Typo jeu vidéo (v0.52)
- Nouvelle police UI : **DotGothic16** \u2014 pixel douce type petit jeu d'aventure, kawaii et tr\u00e8s lisible en petit. Remplace le marqueur manuscrit sur toute l'interface.
- Tailles de texte r\u00e9duites (boutons, grilles, menus) pour un rendu plus fin et plus dense.

## v0.52 \u2014 banques couleur, raccourcis \u00e9dition, polices
- **Chaque banque a sa couleur** (1 orange, 2 turquoise, 3 violet, 4 vert, 5 rose, 6 jaune, 7 rouge, 8 bleu, 9 magenta) \u2014 visible sur les pads de la grille ET sur le panneau d'\u00e9dition (poign\u00e9es de tirettes + bandeau).
- **Panneau d'\u00e9dition unifi\u00e9** : en haut, chips **1-9** pour jumper directement d'une banque \u00e0 l'autre (le panneau suit), num\u00e9ro **Bx** en couleur, et acc\u00e8s direct **COMM** (liens du dernier param\u00e8tre touch\u00e9) et **PATCH** (baie de patch) sans refaire la navigation.
- **F et Z** plac\u00e9s dans la colonne de droite, cal\u00e9s \u00e0 hauteur de la grille de pads (entre deux zones flexibles).
- **Menu \u2192 STYLE \u2192 POLICE** : 3 variantes \u2014 **PIXEL** (DotGothic16, par d\u00e9faut), **FORMEL** (sans-serif), **MARQUEUR** (fantaisiste). Le changement s'applique instantan\u00e9ment \u00e0 toute l'interface.
- **Menus compacts** : boutons plus fins, panneau menu \u00e9largi pour que rien ne soit coup\u00e9 sur les c\u00f4t\u00e9s.

## Profils IN / OUT (v0.52)
- **Profils d'entr\u00e9e** (Menu \u2192 ENTREE) : **TRANSP** (transparent, soft-clip doux seulement), **INSTRU** (line-in iPad/synth\u00e9s, HPF l\u00e9ger + comp doux), **VOIX** (chant\u00e9e/guitare/fl\u00fbte : HPF 95 Hz, comp medium, pr\u00e9sence), **BBOX** (beatbox : HPF 70 Hz, comp rapide et punchy, saturation l\u00e9g\u00e8re).
- **Profils de sortie** (Menu \u2192 PROFIL SORTIE) : **STUDIO** (neutre, propre), **LOUDNESS** (compression moyenne + makeup, plus de niveau), **COULEUR** (fa\u00e7on Dolby On mais discret : \u00e9largissement st\u00e9r\u00e9o subtil, chaleur dans le bas, air dans le haut, compression l\u00e9g\u00e8re \u2014 jamais extr\u00eame, pour d\u00e9foncer le son il y a les FX en amont).

## v0.53 — persistance complète « tel qu'on l'a laissée »
- **Tout est restauré au redémarrage** : skin (84/RETRO/GAME BOY/ELECTRIBE/CAUSTIC), couleur de fond, police, aide XY, profil d'entrée (TRANSP/INSTRU/VOIX/BBOX), profil de sortie (STUDIO/LOUDNESS/COULEUR), filtres IN/OUT, volume master, gain d'entrée, orientation, transpose snap, journal samples.
- **Session auto** : la session complète (projet : boucles, presets, snaps, motion, liens, LFOs, MIDI map, punch-ins...) est sauvegardée automatiquement dans un slot dédié à la fermeture de l'app, et rechargée au démarrage (« SESSION RESTAURÉE »).
- **Retour arrière (quitter)** : un dialogue propose **SAUVER** (sauvegarde la session puis quitte), **QUITTER** (quitte sans sauver la session) ou **ANNULER** (rester). Les réglages UI sont de toute façon conservés.

## v0.54 — layout variante A, grille 16, mode groupe
- **Ligne de contrôle horizontale** : FX · TOOLS · REC · UNDO · MENU en haut de l'écran (plus de coupures, marge par rapport au bord). La colonne verticale ne garde que F, Z, SHIFT, MUTE alignés sur la grille.
- **Grille 16 pads** : 8 banques (1-8) + 8 pads de groupes (9-16, numérotés). L'écran XY est un peu moins haut pour laisser respirer le haut.
- **Faders remontés** : ZU/LU (long et fin) en position centrale-décalée, MIX et FLY juste au-dessus, plus rien n'est collé en bas.
- **Mode groupe (façon Octatrack)** : le **badge de mode** (SCÈNE → MOTION → SAMPLES → **GRP**) bascule en mode groupe — persistant, animation pixel « MODE GROUPE », les 16 pads s'illuminent en turquoise. Dans ce mode, **chaque pad = un groupe complet** (samples, FX, réglages — 16 groupes), tap = charger le groupe, priorité absolue sur les snapshots.
- **Capturer un groupe** : **Shift + Mute** = sauver les réglages actuels dans le groupe sélectionné · **Shift + double-tap Mute** = sauver dans le **prochain groupe libre** (sans écraser le courant).
- **Écran BPM élargi** : le chat pixel est bien visible, et l'écran affiche le **nom du track en cours** + le **groupe actif** (G1-G16).

## v0.55 — badge agrandi + mode X-Files
- **Badge de mode agrandi** (48×26) : nom du mode bien lisible + petite info utile dessous (ex. le groupe actif « G3 » en mode groupe).
- **Mode X-FILES** (Menu → STYLE → X-FILES) : pour les puristes — tous les petits textes des pads et du badge laissent place à des **symboles cryptiques** (mais liés au sens : chaque mode et chaque pad garde son propre glyphe, toujours le même). Persistance incluse.

## v0.56 — billard uniquement en hold
- **Mode momentané** : relâcher le doigt ne déclenche plus l'effet billard (le point s'arrête net) — l'axe XY redevient jouable.
- **Mode hold** : taper sur un point posé le fait **rebondir** (piou !) dans la direction opposée au tap, avec l'inertie billard réglée dans le menu (FRICTION).
- **Grouper les deux points** : pincer avec deux doigts pendant 1 s (déjà en place).

## v0.57 — F/Z directs + curseurs redesign
- **Boutons Ψ (F) et Ξ (Z)** : un appui = commuter **momentané ↔ hold** (allume l'axe s'il était éteint). C'est leur seul rôle hors contextes REC/FX.
- **Muter un point** : **Ψ+MUTE** = mute l'axe F · **Ξ+MUTE** = mute l'axe Z · **Ψ+Ξ+MUTE** = mute les deux. MUTE seul = mute master comme avant.
- **Curseurs XY redesign** : plus ronds (double halo + anneau blanc + cœur), plus contrastés, avec le glyphe **Ψ / Ξ** sur pastille arrondie — même typo extraterrestre que les boutons, identique en permanence.

## v0.58 — liaison Shift+F+Z
- **Lier les deux points** : **Shift + Ψ + Ξ simultanés** — les points fusionnent sur l'écran XY, entourés d'une lueur avec un **petit cadenas** entre eux.
- **Délier** : refaire la même manip — déliaison **sobre**, sans effet billard (les points restent en place).

## v0.59 — baie de patch façon AUM
- Refonte de la **baie de patch** : deux colonnes de nœuds (**SOURCES** à gauche, **DESTS** à droite), un nœud par source/destination unique, et des **câbles en éventail** qui partent d'une source vers toutes ses destinations — le chemin de chaque connexion est visible d'un coup d'œil (halo + trait coloré par type, comme AUM).

## v0.60 — câbles distincts + stutter progressif
- **Baie de patch** : chaque câble a maintenant une **teinte légèrement différente** (rotation de teinte par connexion) — encore plus lisible façon AUM.
- **Stutter FLY progressif** : le stutter démarre doucement à 75 % de la course et **accélère jusqu'au fond** — la fenêtre de répétition se resserre et le mixe s'intensifie au fur et à mesure. Fin de course plus musicale, plus de seuil tout-ou-rien.

## v0.61 — bandeau contrôle en haut à droite
- **FX · TOOLS · REC · UNDO · MENU** forment un bandeau **en haut de la colonne droite, juste avant PLAY et le panneau VU/BPM**. La zone gauche est réservée au pad XY et à sa rangée de banques.
- **Ψ et Ξ écartés** dans la colonne verticale (plus de gap entre les deux) pour appuyer sur les deux confortablement (combinaisons Ψ+Ξ+MUTE, Shift+Ψ+Ξ).

## v0.62 — badge sous Ψ/Ξ + faders descendus + POW
- **Badge de mode** (SCÈNE/MOTION/SAMPLES/GRP) déplacé **sous les boutons Ψ et Ξ**, dans la même colonne verticale.
- **Faders légèrement descendus** (marge au-dessus de ZU/LU) : la **grille de 16 pads est plus haute** (104 dp) et plus large.
- **PUNCH renommé POW** : le bouton est **aligné sur la rangée des banques** (même taille de texte, même ligne, dernier de la rangée).

## v0.63 — infos déplacées vers l'écran BPM
- Les **messages et l'aide-mémoire** (MENU, FX RACK, REC → DESTINATION, MODE GROUPE, POW, ARP...) s'affichent désormais dans l'**écran BPM/VU**, **élargi à 190 dp** pour laisser toute la place aux animations (chat pixel, battement).
- Le **pad XY reste dédié au jeu** : plus aucun texte d'état parasite en bas du pad.

## v0.64 — VU IN à gauche / OUT à droite + billard double-tap
- **Écran VU** : **IN à gauche, OUT à droite**. Côté IN : swipe vertical = volume d'entrée, swipe horizontal = filtre (gauche = low-pass vers le grave, droite = high-pass vers les aigus), **double-tap = filtre à zéro**. Côté OUT : pareil sur la sortie.
- **Billard** : en mode **hold**, c'est désormais le **double-tap sur le curseur** qui le fait rebondir (piou !) — un simple tap ne fait plus rien. En mode fusion, double-tap sur les points fusionnés = effet séparé en mode billard.

## v0.65 — mode MACROS + VU fluo + sable plus vif
- **Mode MACROS** : **swipe vers le haut sur le bouton FX** = bascule XY ↔ panneau de macros (les deux modes sont **persistants**, le mode est conservé au redémarrage). Le panneau remplace l'écran XY : **6 faders verticaux** (M1-M6), **1 fader horizontal long** (MACRO) en bas, et **4 boutons** sur le côté droit (affectation à venir). Refaire le swipe vers le haut = retour au mode XY.
- **VU-mètre fluo** : couleurs flashy (vert fluo OUT, cyan fluo IN, jaune acide, rose néant rouge).
- **Animations sable/kaleido** : effacement plus rapide sur l'écran XY.

## v0.66 — boutons Ψ/Ξ en couleur pleine
- **Ψ (F) jaune plein et Ξ (Z) rose plein**, comme les curseurs de l'écran XY : toujours colorés, jamais noirs (légèrement assombris quand l'axe est OFF, texte sombre lisible par-dessus). Le clignotement d'affectation se fait sur la couleur du fond.
- En mode MACROS, **Ψ et Ξ continuent de déclencher les motion sequences** comme sur l'écran XY (process identiques, boutons indépendants du mode d'affichage).
