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

## Inspection technique prioritaire

1. Inspecter les handlers HOME, GLO, REC, SEQ, ARP, EDIT et leurs appuis courts/longs/doubles/triples sur APK et FM-1 ; dresser la matrice des conflits.
2. Confirmer le vrai raccourci GLO vers le menu système et le triple-tap vers ONE SHOT / pads / sirène.
3. Vérifier le moteur acid_pattern : moment de génération et d'application du pattern, quantification à la mesure, transport.
4. Inspecter l'éditeur de pas commun DRUMS / LOOPER, et la persistance de la page de paramètres.
5. Inspecter ASSIGN, routage MIDI, LEDs OP et sauvegarde KIT.

**Aucune modification de code fonctionnel n'est impliquée par cette note.**
