# ZULUTEK — Archive de conception ZULU BLOK
**Version :** 0.1 — **Date :** 2026-11-23  
**Statut :** photographie des décisions de conception discutées ; ne constitue ni une validation du firmware ni une preuve d'implémentation.

## 1. Vision et architecture
ZuluTek est une famille d'instruments et d'environnements musicaux : FM-1 physique, application Android (APK) et Zulu Control. Les idées initialement explorées dans Zulu Control ont nourri ZuluTek ; les modules affinés dans ZuluTek doivent pouvoir enrichir Zulu Control en retour.

**Nom retenu : ZULU BLOK.** Un ZULU BLOK est une brique musicale autonome, connectable, réutilisable et évolutive. Devise : « Un ZULU BLOK peut vivre seul, jouer avec les autres et grandir sans tout reconstruire. »

Architecture cible : (1) cœur fonctionnel indépendant de l'interface ; (2) contrats de connexions typées : notes, modulation, audio, événements et état ; (3) interfaces adaptées aux commandes physiques, au tactile ou au bureau. Services partagés : horloge/transport, audio, routage, sauvegarde. Prévoir identifiants, versions, compatibilité des KIT et migrations. **Portabilité du code et distribution de mises à jour indépendantes sont deux sujets distincts** ; le FM-1 pourra nécessiter un firmware complet.

## 2. Principes de jeu
LIVE d'abord, exploration sans tutoriel imposé ; ATELIER et TOOLS pour la profondeur. « LABO donne envie. TOOLS donne les moyens. » Les LABO sont des organismes musicaux avec un comportement audible, non de simples animations. « Des bactéries et des électrons. »

## 3. LABO — structure retenue
**Quatre instances simultanées**, types duplicables, chacune avec une sortie de modulation OUT assignable à plusieurs destinations. Fonctionnement **SOLO par défaut** ; mode **ECOSYSTEM** optionnel, limité aux interactions compatibles, sans boucle de rétroaction incontrôlée.
- **NATURE** : MARÉE, HUMUS, MYCELIUM, OISEAUX, INSECTES.
- **MACHINE** : NET VINTAGE, CHAOS, LINK.

### MARÉE
Cycle asymétrique FOND → MONTÉE → ÉTALEMENT → courte SUSPENSION → RETRAIT, jamais exactement identique. K1 PERIOD, K2 SHORE, K3 WILD, K4 DRIFT (de synchronisé au BPM à libre). Les changements de BPM ne doivent pas téléporter la vague en cours.

### MYCELIUM
Explore des sources de modulation autorisées, découvre des événements et forme un réseau interne borné, sans modifier le routage externe. K1 SPEED, K2 LIFE, K3 CURIOSITY, K4 MEMORY. **Décision explicite : LIFE = 0 signifie arrêt absolu, effacement du réseau et sortie neutre avec lissage ; toute valeur LIFE > 0 engendre une nouvelle génération.** SPEED et LIFE indépendants.

### HUMUS
« La mémoire de ce qui disparaît » : traces compactes de contrôle, décomposition et germination, particulièrement dans le calme ; aucun enregistrement audio. K1 FEED, K2 DECAY, K3 FERTILITY, K4 DEPTH ; sens exact de DECAY à affiner.

### OISEAUX
Appels, silences, réponses, murmuration collective, rapprochement, **respiration collective** avec suspension puis dispersion, non périodique. Proposition K1 FLOCK, K2 CHATTER, K3 BOND, K4 SKY ; nombre d'agents et détails DSP à confirmer.

### INSECTES
Micro-impulsions, frottements, essaims, mues et métabolisme. Proposition K1 DENSITY, K2 AGITATION, K3 SWARM, K4 MUTATION. Interaction facultative bornée avec OISEAUX : chasse, cachette, retour.

### CHAOS
Dynamique non linéaire ÉQUILIBRE → VERTIGE → BASCULE, pas un simple bruit aléatoire. Proposition K1 EDGE, K2 TENSION, K3 GRAVITY, K4 MEMORY.

### NET VINTAGE
Microsociété électronique : échauffement, fuites, fatigue et incidents rares qui se propagent. Proposition K1 AGE, K2 HEAT, K3 LEAK, K4 STABILITY. Influence possible sur CHAOS et LINK en ECOSYSTEM MACHINE.

### LINK
Observe **deux entrées de modulation SRC A / SRC B** et crée **une troisième modulation OUT**, sans altérer les sources : convergence, écart, attraction, tension et mémoire de relation. Plusieurs LINK peuvent coexister ; LINK2 peut écouter LINK1 si le graphe reste acyclique. Différence avec MYCELIUM : LINK reste attaché à deux sources, MYCELIUM explore un ensemble. Proposition K1 BOND, K2 TENSION, K3 FOLLOW, K4 MEMORY. Algorithme et commandes définitives à valider.

## 4. Modulation et TOOLS — conception actuelle
- **4 LFO** : SIN, TRI, SQ, SAW, S&H, DRUNK ; synchro BPM et retrigger.
- **12 TOOLS** : ENV1 (ADSR, boucle facultative), ENV2/ENV3 (AD/AR, bouclables), EF1/EF2 (envelope followers), LOG, SLEW, CMP, QUANT, ATT1/ATT2/ATT3 (UNI+, UNI−, BI).
- **4 LABO** : instances, non types.
- Total conceptuel : **20 sources de modulation** (4 + 12 + 4). La documentation historique du dépôt mentionne encore **16 sources et 8 TOOLS** : mise à jour et alignement du code nécessaires.
- ASSIGN relie des sorties de modulation à des paramètres compatibles ; prévention des cycles par graphe dirigé acyclique (DAG), plutôt que règle d'index figée.

## 5. OSC — proposition de voix audio minimale
Petit oscillateur accessible dans TOOLS pour transformer des explorations de modulation en instrument audible. **Voix audio**, distincte des 12 TOOLS de contrôle et des 20 sources de modulation. Proposition SIN/TRI/SAW/SQR ; K1 WAVE, K2 TUNE, K3 LEVEL, K4 GATE (KEY/DRONE) ; anti-clic, assignation pitch/level/wave, sortie MIX/PEDALBOARD. Détails et coût DSP **non validés**.

## 6. KEYWIDGET — conception et gestes retenus
Clavier universel indépendant des pages, capable d'émettre des événements de notes vers OSC, EAST, WEST, DRUMS et cibles futures ; destination **toujours visible** (« KEY → OSC »), conservée en changeant de page.
**Distinction physique impérative :**
- **REC + SEL** = appui simultané sur **deux boutons** : afficher/masquer KEYWIDGET.
- **REC maintenu + rotation SELECT** = tourner **l'encodeur/potentiomètre**, changer rapidement la destination.
- **SEL** n'est **pas** **SELECT**. Ces raccourcis sont **retenus sous réserve de vérifier leur disponibilité et les conflits dans le firmware**.
Clavier sous forme de tiroir, accessible depuis LABO, TOOLS, LFO, ASSIGN, FX. Protocole de travail **ZTK-KEY v1** : NOTE_ON(note,velocity,channel), NOTE_OFF(note,channel), PITCH_BEND(value,channel), ALL_NOTES_OFF(channel), SET_TARGET(target_id), SET_OCTAVE(offset). La séparation notes/routage de notes versus modulation/ASSIGN doit rester explicite ; une seule source de vérité du routage. Prévenir les notes bloquées : mémoriser la cible de chaque NOTE_ON, relâcher proprement à changement de cible, fermeture ou chargement de KIT ; gérer les notes de plusieurs dispositifs. Toucher APK, touches physiques FM-1 et MIDI peuvent partager le protocole. **Aucun moteur audio KEYWIDGET n'est déclaré implémenté dans cette archive.**

## 7. Autres familles et fonctions évoquées
EAST (soustractif), WEST (West Coast), DRUMS (4 voix riches + 4 auxiliaires), LOOPER, MIX GLOBAL, PEDALBOARD (4 plateaux indépendants de 3 FX), PoW!, SNAP ZOU/LOU, FLY, SEQ+ (Turing Machine/mutation), ARP, MACRO (16), PRESETS, baie PATCH audio, LIVE/MOTEUR/ATELIER, KIT. Ces éléments sont des éléments du périmètre discuté, **pas une liste de fonctions testées dans cette session**.

## 8. Décisions / propositions / vérifications
**Décisions de conception explicites :** nom ZULU BLOK ; modularité portable ; 4 LABO simultanés duplicables ; SOLO par défaut ; MYCELIUM LIFE=0 arrêt absolu ; LINK produit un troisième signal à partir de deux sources ; KEYWIDGET destination visible ; gestes REC+SEL et REC+rotation SELECT **conditionnels à audit**.

**Propositions à finaliser :** mapping de plusieurs K1–K4, interactions ECO, modèle d'OSC, paramètres complets ZTK-KEY, stratégie de versions/migrations, politique de notes en HOLD, coût CPU.

**À vérifier techniquement :** audit des conflits de touches et états REC/SEL/SELECT ; état réel APK et patches FM-1 ; tests DSP/anti-aliasing/latence ; cycles de modulation ; sauvegarde/restauration KIT ; validation sur matériel FM-1 ; mise à jour du référentiel 16→20 sources. Ne pas confondre conception discutée et code committé.

## 9. Règle de continuité
Pour toute nouvelle idée : identifier le ZULU BLOK concerné, ses entrées/sorties, son état, ses dépendances, ses variantes d'interface et sa compatibilité. Consigner la date, le statut (décidé/proposé/testé/implémenté), et le lien vers les commits/tests avant de déclarer la fonctionnalité livrée.

*Archive de synthèse issue de la conversation et des documents disponibles, non transcription exhaustive de tous les échanges antérieurs.*
