# ZuluTek — décisions de workflow et points d'inspection (22 novembre 2026)

> Spécification validée en conversation. Ce document consigne des décisions de conception, **pas une preuve d'implémentation** sur APK ou FM-1. Les anciens documents peuvent décrire d'autres raccourcis. Ne pas les prendre comme autorité pour ces nouvelles décisions.

## Rituel de navigation

- Les gestes doivent être cohérents avec la famille fonctionnelle de leur bouton. Éviter d'ouvrir des pages sans rapport par double-tap.
- **HOME court** parcourt uniquement **BASSE → LEAD → DRUMS → BASSE**. Retirer LOOP de ce cycle.
- **REC + SEQ** ouvre **LOOP**.
- **REC + EDIT** ouvre **MACRO**.
- **EDIT court** ouvre/ferme la page **XY**. Fermer la page ne désactive pas XY HOLD ni les effets.
- **EDIT long (~0,8 s)** active/désactive l'édition des pas **uniquement si une page de séquenceur Zulu est déjà ouverte** (DRUMS ou LOOPER, ONE SHOT / LOOP). Hors d'une page de séquenceur, EDIT long ne déclenche pas l'édition et ne navigue pas. EDIT fixe en édition, clignotant sur page XY. Priorité LED en cas de chevauchement à préciser.
- En édition des pas : 16 pas sélectionnables ; K1–K4 éditent quatre paramètres ; SELECT change la page de quatre paramètres ; changer de pas conserve la page de paramètres ; EDIT court quitte l'édition.
- **Nouvelle extension ARP/ATEK303** : lorsque la page de l'arpégiateur est déjà ouverte, **EDIT long (~0,8 s)** ouvre également une édition des pas sur le clavier (sans changer le moteur acid). Les 16 touches représentent/sélectionnent les 16 pas de la page ; **un appui sur OP1, OP2, OP3 ou OP4 choisit directement la page d'édition** : OP1 = pas 1–16, OP2 = 17–32, OP3 = 33–48, OP4 = 49–64. Pendant la lecture, leurs LEDs indiquent aussi la page actuellement jouée ; **le défilement de lecture ne change pas la page d'édition sélectionnée**. K1–K4 éditent les paramètres de la page courante ; SELECT parcourt les groupes de quatre paramètres ; EDIT court quitte. Distinguer visuellement la page sélectionnée pour l'édition de la page en cours de lecture, si elles divergent. **À vérifier** : mapping exact des paramètres acid, gestion des LEDs, conflits éventuels des OP1–OP4 avec d'autres usages et fonctionnement si pattern de moins de 64 pas.
- **SEQ seul** conserve son rôle de demande/génération de nouveau pattern acid ATEK303, avec nouveau timing d'application défini plus bas.
- **ARP seul** conserve sa fonction ATEK303.
- **REC + ARP** ouvre l'éditeur avancé acid **SEQ+**, sans régénérer le pattern courant.
- **REC + double-tap ARP** ouvre l'éditeur de notes pas à pas original ATEK303. Exception cohérente car toujours dans la famille ARP ; vérifier la reconnaissance single/double.
- **GLO court** ouvre le menu **GLOBAL** (nouveau nom de l'ancien OPTIONS).
- **GLO double-tap** : **aucune affectation confirmée** ; les anciennes mentions DRUMS/MACRO sont contradictoires et ne sont pas retenues.
- **GLO triple-tap** : raccourci ONE SHOT / pads / sirène rappelé par l'utilisateur ; **à confirmer dans le code** avant toute modification.
- **REC + GLO** : procédure d'enregistrement LOOP (documentée, à vérifier).
- **HOME + GLO** : redo (documenté, à vérifier).
- **REC + OCT−** : entrée/sortie du mode ASSIGN global (documenté, à vérifier).

## Tempo indépendant de l'arpégiateur ATEK303

- Conserver le réglage **TEMPO** de la page **SEQ**, sur **K3** (ordre documenté : K1 STEPS, K2 SCALE, K3 TEMPO, K4 ROOT).
- **K3 = 0** signifie **GLOBAL** : le séquenceur/arpégiateur ATEK303 suit le tempo général de ZuluTek. Ce n'est ni 0 BPM ni STOP.
- **K3 > 0** : tempo propre à l'arpégiateur, indépendant du tempo global. Exemple demandé : arpégiateur 280 BPM alors que ZuluTek est à 75 BPM.
- Revenir à **0 / GLOBAL** réactive la synchronisation sur le tempo global, **avec une zone tampon de 4 crans d'encodeur entre le BPM indépendant minimum et GLOBAL** afin d'éviter un basculement accidentel. Dans la zone tampon, conserver le BPM minimum sans changement de mode ; prévoir également une séparation perceptible dans le sens GLOBAL → tempo libre. **4 crans validés par l'utilisateur.**
- À inspecter : plage réelle de BPM disponible, mécanisme de synchronisation/rephase lors du retour à GLOBAL et persistance du réglage.

## GLOBAL et changement de pattern SEQ

- GLOBAL rassemble les réglages peu fréquemment modifiés : ECO, tempéraments/accordages, préférences système et autres paramètres globaux. Ne pas créer un second menu « Options ».
- Ajouter **SEQ CHANGE** dans GLOBAL :
  - **IMMEDIATE** (défaut) : demande et application immédiates, sans attendre la mesure/la boucle ;
  - **NEXT BAR** : application à la prochaine mesure ;
  - **+2 BARS** : application deux mesures plus tard.
- L'appui SEQ demande/génère le nouveau pattern immédiatement ; seule son application est différée selon le réglage. Ne pas redémarrer le transport. Vérifier dans le moteur acid le traitement des événements déjà en cours et la définition exacte de +2 BARS.

## ASSIGN global et MIDI

- ASSIGN reste actif lors des changements de pages ; sélectionner une source (contrôle physique, XY X/Y, modulateur, MIDI), confirmer via SELECT OUI/NON, puis ajouter des destinations au panier avec Shift et confirmation SELECT. Quitter ASSIGN ne supprime pas les affectations ; sauvegarde via KIT.
- Modes MIDI envisagés : INTERNAL, MIDI OUT CC, MIDI OUT NOTE, MIDI OUT NOTE + CC, MIDI IN, MIDI LEARN, MIDI TEACH. Vérifier l'interface exacte.
- Chaque source assignable possède jusqu'à **trois slots MIDI CC** configurables séparément : canal 1–16, numéro CC 0–127, courbe normale/inversée. Un slot vide n'émet rien.
- En ASSIGN, **OP1 / OP2 / OP3** activent ou désactivent les slots correspondants ; LED allumée = slot actif. Un seul slot actif facilite MIDI Learn ; plusieurs slots actifs peuvent émettre ensemble ; aucun slot actif = aucun CC externe.
- **OPn maintenu + potentiomètre** permet de piloter exclusivement le CC du slot n sans modifier le paramètre interne du potentiomètre. Vérifier la coexistence des gestes maintien et bascule, ainsi que le contexte hors ASSIGN avant implémentation.
- MIDI OUT NOTE + CC réutilise les configurations NOTE et CC existantes. Configurations et états des slots persistés dans KIT.

## Idée différée — MACRO

- Explorer plus tard la possibilité de **segmenter virtuellement la course d'un encodeur en zones** (par exemple premier quart affecté à une fonction MACRO). Idée seulement évoquée, **aucun comportement ni mapping validé** à ce stade ; reprendre lors de l'inspection MACRO.

## PoW! — punch-in, inspirations et contraintes de conception (visite du 23 novembre 2026)

- **Intention confirmée** : effets punch-in immédiats et expressifs, inspirés notamment des Pocket Operator (PO-12 Rhythm) et d'Amenity sur FM-1 ; priorité musicale aux **altérations rythmiques / glitchs temporels** : shuffle, BPM stutter, beat repeat, micro-décalages, granulaire, déplacements dans le temps. Garder aussi des filtres, delays, saturations et traitements de timbre.
- **Contrôle souhaité** : déclencher l'effet par maintien d'une touche et pouvoir **tourner K1–K4 pendant le maintien** pour régler en direct **quatre paramètres propres à chaque effet**. Modalités précises de sélection des effets, de mémorisation des paramètres et de relâchement à définir ; ne pas les inventer.
- **Palette artistique privée de conception** : Aphex Twin, High Tone, MF DOOM, Tom Waits, Noisia (DnB), Skrillex (dubstep), Daft Punk, Boards of Canada, Radiohead, plus une famille glitchcore. Évoquer ces univers par les traitements et l'esthétique, **sans citer explicitement les artistes dans l'interface publique**.
- **Pistes non figées** : environ quatre effets par famille, soit un catalogue exploratoire d'environ 40 propositions, **pas** 40 effets définitivement approuvés ni 40 effets simultanés sur les 16 touches. Exemples à explorer : Shuffle Punch, BPM Stutter, Time Slip, Granular Scrub, Beat Repeat, Reverse Slice, Micro-Jitter, Time Stretch Glitch. Éviter les doublons et prioriser le plaisir de jeu.
- **Identité visuelle APK** : donner aux familles PoW! une couleur / signature graphique reconnaissable et une présentation des potentiomètres adaptée à leur personnalité, sans nuire à la lisibilité et sans noms d'artistes.
- **Performance** : concevoir d'abord pour une APK Android stable, compatible autant que possible avec des appareils récents et plus anciens ; effets économes en CPU et mémoire, tampons limités, éviter les allocations dans le fil audio. ECO est un soutien, **pas une excuse pour une version normale instable**. Le portage FM-1 sera évalué séparément, effet par effet, avec mesures et aménagements possibles. **Faisabilité FM-1 et performances Android non encore vérifiées**.
- **Point technique à inspecter plus tard** : moteurs et buffers audio disponibles, latence, polyphonie/voix, synchro BPM, charge maximale de plusieurs punch-in, impact ECO, faisabilité des traitements temporels. Les références artistiques et noms d'effets sont des pistes, pas des copies de traitements existants.

## Visite MIX GLOBAL — description utilisateur, arbitrage requis (23 novembre 2026)

- **Vision exprimée lors de la visite, non encore arbitrée contre le code/docs anciens** : quatre tranches **BASSE, LEAD, DRUMS, LOOPER**, plus MASTER. Sur chaque tranche : filtre traversant LP / centre neutre / HP en tête, puis panoramique ; départ auxiliaire PoW! avec dosage propre ; départ auxiliaire d'enregistrement LOOPER sur BASSE/LEAD/DRUMS uniquement ; **quatre départs auxiliaires vers quatre pedalboards** (chaque pedalboard comprend trois slots de pédales, routage à détailler plus tard) ; volume et solo/mute à retrouver/préciser.
- **Proposition ergonomique à explorer, non encore validée dans ses détails** : PRESET et ALGORITHM déplaceraient la fenêtre des quatre knobs K1–K4 dans une grille de mixage : lecture **horizontale** (K1 BASSE, K2 LEAD, K3 DRUMS, K4 LOOPER pour un même paramètre) ou **verticale** (K1–K4 = quatre paramètres successifs d'une même tranche). Définir gestes exacts, transitions, gestion du MASTER et des cases sans AUX LOOP.
- **Conflit documentaire explicite** : `docs/ZULUTEK.md` et `docs/ZULUTEK-MIX-SIREN.md` décrivent **VOICE / DRUMS / LOOP / SIREN**, VOICE réunissant BASSE et LEAD, avec interface SELECT/K1–K4 différente. Ce schéma est documenté comme ayant eu des PR d'implémentation. **Ne pas remplacer silencieusement** : demander si la vision actuelle rétablit BASSE/LEAD séparés, et ce qu'il advient de SIREN, puis inspecter le code APK/FM-1.
- **Clarification utilisateur** : le regroupement BASSE+LEAD en VOICE dans l'ancienne version est **une adaptation FM-1 pour économiser les ressources** ; un seul des deux synthés y joue à la fois, ce compromis est accepté. La **vision de mixage APK** conserve BASSE, LEAD, DRUMS, LOOPER et envisage d'**ajouter SIRÈNE comme cinquième tranche, juste avant MASTER**. La sirène ne remplace donc pas une des quatre tranches d'origine dans cette vision. **Proposition favorable mais à confirmer comme schéma définitif**, notamment séparation effective des bus BASSE/LEAD côté APK et représentation/adaptation des cinq tranches côté FM-1. Ne pas déclarer l'architecture audio modifiée.
- Ne pas traiter les quatre départs pedalboard comme déjà câblés : l'ancien mixeur ne mentionne que deux AUX libres assignables. Vérifier la faisabilité/routage plus tard.

## Principe transversal — navigation bidirectionnelle des paramètres (23 novembre 2026)

- **Intention approuvée** : généraliser à toutes les parties pertinentes de ZuluTek le principe d'une **fenêtre de quatre paramètres K1–K4**, parcourable dans deux orientations via les encodeurs **PRESET et ALGORITHM** : par **ligne** (même paramètre sur quatre sources/voies) et par **colonne/tranche** (quatre paramètres successifs d'une même source). Vise à rendre les réglages accessibles sans multiplier les sous-pages.
- **À concevoir page par page**, sans imposer artificiellement une grille aux fonctions qui ne s'y prêtent pas et **sans écraser les gestes déjà validés** (ATEK303 SEQ K1 STEPS/K2 SCALE/K3 TEMPO/K4 ROOT, édition des pas, PoW! maintien+tournage K1–K4, etc.).
- **Points non arrêtés** : affectation exacte PRESET/ALGORITHM aux axes, geste de bascule de l'orientation, comportement aux frontières de groupes de quatre, paramètres manquants (AUX LOOP sur LOOPER), MASTER, rappel des sélections, affichage/LEDs, et compatibilité avec les fonctions natives de PRESET/ALGORITHM. Ne pas déclarer ces choix résolus.

## SNAP — capture sélective et morphing ZOU / LOU (23 novembre 2026)

- **REC + PLAY** entre en mode capture SNAP et en ressort après la session ; il ne faut pas confondre ce geste avec un déclenchement de lecture. En capture, choisir un slot de destination parmi **16 touches blanches** (huit physiques à gauche, huit à droite) puis **SHIFT confirme** ce slot.
- Ensuite, comme en ASSIGN, naviguer librement entre les pages, toucher/tourner un paramètre cible et **appuyer sur SHIFT pour capturer explicitement chaque paramètre** ; répéter autant que souhaité. **REC + PLAY** termine la capture. Préciser lors de l'inspection la valeur exacte mémorisée, le comportement d'un slot déjà rempli et l'indication de capture.
- Les slots sont initialement répartis **8 ZOU (gauche) / 8 LOU (droite)**, avec couleur distincte pour chaque camp dans l'APK. Ces groupes sont des **pôles de morphing**, pas des limites rigides de stockage.
- Le **potentiomètre de morphing reste disponible après la sortie du mode SNAP**. **OCT−** active/désactive le pôle ZOU ; **OCT+** active/désactive LOU (hors geste contextuel de réaffectation en capture, à préciser). Deux pôles actifs : interpolation ZOU ↔ LOU ; un seul actif : interpolation entre ce pôle et l'absence de contribution snapshot (« dry ») ; aucun actif : déplacement du potentiomètre sans effet, mais **réactivation applique immédiatement la position courante**, saut éventuel assumé.
- **Réaffectation d'appartenance** : pendant la capture, avant de quitter, possibilité d'affecter un slot physiquement situé dans l'un des groupes au **pôle opposé** (exemple utilisateur : OCT+ pour LOU puis sélection d'un slot situé de l'autre côté). Le slot prend la **couleur du groupe d'affectation** dans l'APK ; sur FM-1, faute de couleurs, **la touche du slot déplacé clignote** pour indiquer cette exception. Le geste exact de réaffectation, la priorité OCT+/OCT− dans le contexte SNAP, la gestion des groupes et les LEDs sont à préciser, sans inventer de raccourci.
- **Point à inspecter** : anciens documents mentionnent REC+SEL pour page SNAP, REC+PLAY pour capture ; vérifier les handlers existants, les 16 slots, le moteur de morphing, la persistance KIT/SNAP, la signification de « dry » et les interactions avec ASSIGN/automation. **Cette note est une spécification de la visite, pas une preuve d'implémentation.**

## FLY — compagnon du morphing SNAP (23 novembre 2026)

- L'utilisateur précise que **Zoé et Lou** sont accompagnées du gnome **Fly**, qui module **le comportement du morph entre les deux pôles**, et non simplement une transposition : trajectoire **linéaire**, **erratique**, **élastique** (atteint une valeur puis revient légèrement vers le côté opposé), autres modes à explorer sans les figer.
- **Souhait actuel** : envisager **K1 pour piloter Fly** ; déterminer s'il choisit le mode, l'intensité ou un autre paramètre, et où se trouve alors le morph principal.
- **Concordance / conflit documentaire vérifié** dans `docs/ZULUTEK.md` : page SNAP ancienne **K1 = fader ZU↔LU**, **K2/K3 libres**, **K4 = FLY whammy ±12 demi-tons** (voir passages en tête de document et vers lignes 263–265). L'idée de Fly existe donc, mais **son rôle ancien ne correspond pas à la nouvelle intention**. Ne pas présenter l'ancien whammy comme validé pour la nouvelle ergonomie.
- **Mise à jour utilisateur — mapping Fly souhaité** : **K1 MODE**, **K2 ELASTIC**, **K3 CHAOS**, **K4 STUTTER** (remplace la proposition antérieure INERTIA/RANGE, non retenue). K4 doit être un **petit BPM looper / stutter** lié au morph ZU/LU, dont le caractère se transforme selon K1–K3, plutôt qu'un effet indépendant ou un simple réglage de vitesse. Conserver le morph principal comme commande distincte. Les détails de division rythmique, quantité, buffer audio vs répétition de valeurs de paramètres, synchronisation et faisabilité FM-1 restent à concevoir et tester ; ne pas inventer de comportement définitif.
- **Précision utilisateur sur K4 STUTTER** : comportement **hybride entre répétition de fragments audio et répétition de positions/valeurs du morph ZU/LU**, avec poids et caractère **variables selon le MODE K1**. Au moins un mode doit offrir un **stutter BPM relativement classique et immédiatement musical**, avec une légère signature Fly ; les autres modes peuvent modifier plus fortement le rythme, les rebonds et l'irrégularité en fonction de K2 ELASTIC et K3 CHAOS. Ne pas figer le nombre de modes ni leur DSP exact. La suggestion « K4 augmente l'intensité du stutter » reste une interprétation à valider.
- À préciser : paramètre piloté par K1, place du contrôle de morph permanent, choix des modes Fly, restitution des paramètres SNAP et impact sur moteur/automation ; aucune implémentation modifiée.

- **Correction explicite de l'animation SNAP (utilisateur)** : l'ordre **flash puis appareil photo est erroné**. À **REC+PLAY**, montrer **d'abord une icône appareil photo** pour annoncer l'entrée en mode capture. Lorsque l'utilisateur navigue dans les pages de paramètres, rendre l'affichage normal avec une **petite icône appareil photo persistante dans un coin** pour signaler que le mode capture est toujours actif. **Chaque pression sur SHIFT qui capture un paramètre déclenche alors un bref flash d'écran**, sans masquer durablement le réglage. Métaphore retenue pour les captures accumulées : **pellicule photo** plutôt que panier ; forme exacte de son indicateur à concevoir. Sortie REC+PLAY = fin du mode et disparition du témoin.
- **Règle UX transversale** : privilégier des pictogrammes immédiatement intelligibles **sans dépendance linguistique**, états visibles d'un coup d'œil ; « panneau d'autoroute à 130 km/h, compris en trois secondes ». Animations courtes, non intrusives et lisibles sur APK comme sur l'affichage FM-1 (à adapter aux capacités matérielles). Un compteur sur la pellicule a été proposé par l'assistant, **non validé**.

## Inspection technique prioritaire

1. Inspecter les handlers HOME, GLO, REC, SEQ, ARP, EDIT et leurs appuis courts/longs/doubles/triples sur APK et FM-1 ; dresser la matrice des conflits.
2. Confirmer le vrai raccourci GLO vers le menu système et le triple-tap vers ONE SHOT / pads / sirène.
3. Vérifier le moteur acid_pattern : moment de génération et d'application du pattern, quantification à la mesure, transport.
4. Inspecter l'éditeur de pas commun DRUMS / LOOPER, et la persistance de la page de paramètres.
5. Inspecter ASSIGN, routage MIDI, LEDs OP et sauvegarde KIT.

**Aucune modification de code fonctionnel n'est impliquée par cette note.**
