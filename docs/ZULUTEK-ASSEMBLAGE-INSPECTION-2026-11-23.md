# ZuluTek — assemblage et inspection transversale (23 novembre 2026)

> **Document de travail pour la reprise.** Il rassemble la visite utilisateur et une première confrontation à des sources du dépôt. Il **ne certifie pas** la conformité du firmware FM-1 ou de l'APK et ne modifie aucun moteur audio ni raccourci. Source prioritaire : [journal des décisions](ZULUTEK-WORKFLOW-DECISIONS-2026-11-22.md). Les dernières décisions explicites priment sur les anciennes propositions du même journal et sur les spécifications historiques.

## 1. Périmètre et état de l'assemblage

| Partie | Décisions actuelles à conserver | Éléments repérés dans le dépôt | Reste à remettre d'équerre |
|---|---|---|---|
| **BASSE** | Synthé accessible dans HOME BASSE→LEAD→DRUMS ; SEQ/ARP ATEK303 ; tempo ARP indépendant K3=GLOBAL à zéro avec zone tampon 4 crans ; SEQ CHANGE à 5 choix | `app-zulutek/src/main/cpp/dsp/bass_engines.c` : ATEK303, PLUK, FM, SUB, 8BIT ; `ztek_engine.cpp` : instance `at_voice_t`, `bass_t`, moteur acid, arpégiateur | Vérifier le parcours des pages, le moteur réellement sélectionné, la différence BASSE/LEAD et la portée du tempo indépendant ; inspecter les contrôles et tests, sans confondre les noms historiques |
| **LOOP** | REC+SEQ ouvre LOOP ; REC seul déclenche au **relâchement** ; 16 slots d'événements ; samples audio à importer, dont workflow Koala souhaité ; état du looper inclus dans KIT | `docs/ZULUTEK-LOOP.md` : `midi_looper.c`, slots, quantification, resampling audio ; `ztek_engine.cpp` : `ml_t loop`, buffers et page SMP | Vérifier enregistrement/overdub, Note Off, import audio réel, persistance des échantillons, capacités FM-1 et migration du raccourci REC+SEQ |
| **SNAP / NO SNAP / FLY** | Capture sélective REC+PLAY, SHIFT pour capturer ; NO SNAP retire des paramètres ; ZU/LU via OCT−/OCT+ ; PRESET **toujours ZULU** ; FLY K1 MODE/K2 ELASTIC/K3 CHAOS/K4 STUTTER ; macros capturables, ZULU non capturable | `docs/ZULUTEK.md` décrit l'ancienne page SNAP (K1 morph, K4 FLY whammy) ; `ztek_engine.cpp` possède des paramètres de capture SNAP/KIT | Remplacer l'ancienne conception whammy, vérifier la réalité des 16 slots et des gestes, définir la capture de valeurs et l'absence de boucle SNAP→MACRO→SNAP |
| **KIT** | **8 banques ×16 = 128 KIT** ; REC+SAVE ; banques OP1–OP6/PIT/GLO (à vérifier physiquement) ; changement immédiat par défaut ou 1/2/3 BEATS/1 BAR ; duplication contextuelle ; KIT réinitialise l'état de référence | `docs/ZULUTEK.md` et autres docs historiques : 16 KIT et gestes anciens ; `ztek_engine.cpp` mentionne état SNAP/KIT | Stockage/migration 16→128, rappel fiable des samples, vérification OP6/PIT/GLO, LED banque affichée vs active, conflit avec GLO GLOBAL |
| **MIX** | Vision APK : BASSE, LEAD, DRUMS, LOOP, SIRÈNE puis MASTER ; filtre LP/neutre/HP, pan, volume, solo/mute, PoW!, AUX LOOP, quatre départs pedalboard | **Constat code** : `ztek_engine.cpp` possède `strip[ZTEK_NSTRIP]` avec cut/fmode/vol/pan/mut/solo/aux_pow/aux_loop/aux1..aux4. **Constat doc** : `docs/ZULUTEK-MIX-SIREN.md` décrit l'ancien modèle VOICE/DRUMS/LOOP/SIREN, quatre tranches, partagé FM-1 | **Conflit d'architecture non résolu** : tranches logiques APK séparées BASSE/LEAD vs voix physique commune FM-1 ; contrôler `ZTEK_NSTRIP`, routage et persistance, et SIRÈNE avant migration ; ne pas renuméroter les ports MIDI/ZTF sans plan |
| **MACROS** | Quatre macros K1–K4 multi-cibles ; SELECT=Dictateur, ALGORITHM=10 slots ; REC+SHIFT widget persistant toggle ; REC+EDIT page complète ; séquenceur Dictateur quatre pistes propre à MACROS ; bibliothèque universelle de courbes distincte ; sources ARP STEP/PITCH/VELOCITY | Journal détaillé ; docs historiques décrivent d'autres raccourcis et MACRO moins avancé | Auditer dispatch APK/FM-1, définition des affectations et stockage, arbitrage avec SNAP, contrôle physique et coûts CPU ; **ne pas choisir les mappings usine avant PEDALBOARD** |
| **PoW!, SIRÈNE, GLOBAL, ASSIGN** | PoW! effets momentané à quatre paramètres ; widget SIRÈNE persistant ; GLOBAL séparé des pages ; ASSIGN et sources universelles | Journal et documents historiques | Confirmer gestes GLO, gestion des widgets simultanés, accès aux sources, conflits contextuels |

## 2. Contrat d'interaction transversal à implémenter

1. **REC sur relâchement** : REC down ne démarre rien ; REC up déclenche la fonction d'enregistrement de la page **uniquement si aucun accord REC n'a été reconnu pendant ce maintien**. Après REC+EDIT/SHIFT/SEQ/SAVE/PLAY/OCT, REC up ne doit rien enregistrer. Tester les deux ordres de relâchement.
2. **Accords REC** : priorité si REC est déjà maintenu ; fenêtre **20 ms maximum** pour quasi-simultané, uniquement sur les touches à double rôle ; jamais de Note On parasite ni de Note Off perdu. Les appuis musicaux ordinaires ne doivent pas attendre cette fenêtre.
3. **Mapping actuel** : REC+SEQ→LOOP ; REC+EDIT→page MACROS ; REC+SHIFT→widget MACROS toggle ; REC+PLAY→capture SNAP ; REC+SAVE→KIT ; REC+OCT−→ASSIGN ; REC+OCT+→PRESETS (historique à confirmer). Les anciennes affectations REC+SEQ→MACRO / REC+EDIT→LOOP sont obsolètes **dans la conception**, pas nécessairement dans le code.
4. **ZULU permanent** : PRESET physique pilote le morph interpages ; OCT−/OCT+ pilotent les pôles ; HOME+OCT± transpose. Ne pas recycler PRESET pour page de paramètres, niveau LOOP, navigation KIT ou Dictateur sans arbitrage explicite.
5. **Widgets** : restent superposés lors des changements de page, ne se ferment que par commande explicite ; leurs commandes contextuelles prennent temporairement la main sur les touches concernées. La coexistence MACROS/SIRÈNE est encore ouverte.
6. **États et modulation** : **KIT > SNAP/ZULU > MACROS/autres sources** pour la hiérarchie d'état, mais **le geste physique est toujours opérant**. Quand le filtre est déplacé, sa position devient **la nouvelle base durable** de la modulation (fermé/milieu/ouvert), sans retour à l'ancienne valeur. Pour les cibles compatibles, concevoir `base physique + contributions modulantes bornées` ; **ne pas implémenter naïvement une addition de toutes les sources** sans préciser composition, courbes, ordre et écrêtage.
7. **SNAP vs MACRO** : SNAP peut piloter la position d'une MACRO, qui module ses destinations ; exclure les rétroactions et la capture du contrôleur ZULU lui-même.

## 3. Écarts et risques identifiés lors de cette première lecture

- **Critique — raccourcis** : les anciennes docs sont contradictoires avec le mapping REC validé. Risque de navigation erronée, REC parasite et notes bloquées. Priorité : inventaire réel des handlers key-down/key-up dans firmware et APK, puis tests de régression.
- **Critique — PRESET** : plusieurs documents historiques utilisent PRESET pour le volume, les pages, le choix des sources, etc. Désormais **ZULU permanent** ; toutes ces fonctions demandent un autre accès. L'ancienne proposition de navigation universelle PRESET+ALGORITHM est donc **supplantée** pour PRESET.
- **Critique — MIX** : le code APK contient les champs d'une table quatre tranches et six types d'AUX ; cela **ne prouve pas** la séparation effective BASSE/LEAD ni le câblage audio de quatre pedalboards. La documentation de migration vers VOICE/DRUMS/LOOP/SIREN et la vision actuelle divergent.
- **Critique — persistance** : 128 KIT + samples + snapshots + routes de macros dépassent le contrat historique de 16 KIT. Il faut définir format versionné, stockage des références audio, migration, sauvegarde atomique et compatibilité FM-1.
- **Élevé — modèle de modulation** : la prise de main temporaire puis retour à SNAP, évoquée avant la dernière clarification, est **rejetée**. La base physique doit rester déplacée. Prévoir tests des limites 0/100 %, conflits SNAP direct + SNAP→MACRO et changement KIT pendant mouvement.
- **Élevé — capacités matérielles** : vérifier les touches OP6/PIT, la nature de SHIFT, les LEDs et la SRAM FM-1 ; éviter de transposer directement des possibilités APK au firmware.
- **À clarifier** : quantification des changements KIT/SEQ (frontière musicale vs délai relatif), déclenchement de REC lors d'un changement de page pendant maintien, priorité entre widgets, geste NO SNAP, profils usine MACROS et détails de FLY STUTTER.

## 4. Ordre recommandé de remise en ordre du code

1. **Établir une carte réelle des modules et handlers** (APK Kotlin/JNI/C++ ; FM-1 C/patches), avec preuves et chemins, puis écrire des tests de gestes.
2. **Isoler la machine d'états REC et les priorités de touches** ; sécuriser les Note On/Off et la règle key-up, sans changer silencieusement l'interface.
3. **Stabiliser la couche de paramètres** : valeur de base physique, contributions SNAP et MACRO, bornes, ordre, persistance et tests DSP. Commencer sur le cutoff BASSE comme cas de référence.
4. **Raccorder BASSE et LOOPER** au contrat REC/SEQ/ARP, vérifier le clock, les notes et les enregistrements.
5. **Assembler SNAP/FLY et KIT** : données versionnées, captures, 128 emplacements, migration et test d'un rappel complet.
6. **Réconcilier MIX** (APK logique vs FM-1 physique) **avant** de câbler quatre pedalboards ou de renuméroter les tranches.
7. **Intégrer MACROS** en deux surfaces (page complète et widget), sources ARP universelles et courbes séparées, après validation de la couche de modulation.
8. **Visite suivante** : PEDALBOARD, LEAD et DRUMS avec le capitaine ; ne pas figer leurs nouvelles ergonomies avant cette visite.

## 5. Critères de validation pour la reprise

- REC seul sur BASSE/LEAD/DRUMS/LOOP ne déclenche qu'au relâchement ; REC+SHIFT/EDIT/SEQ ne déclenche aucun enregistrement parasite.
- Maintenir REC puis toucher SHIFT, relâcher dans les deux ordres : aucun événement de note parasite, aucune note préexistante bloquée.
- Filtre BASSE à 0/50/100 % : une même modulation suit la **nouvelle base** après mouvement manuel, sans saut vers l'ancienne valeur.
- Changement de KIT : nouvel état de base, SNAP/assignations et boucles rappelés selon le contrat, sans référence audio perdue.
- PRESET/ZULU reste disponible après changement de page et lorsque le widget MACROS est ouvert.
- Affichage KIT : banque consultée et banque réellement active différenciées ; pas de faux slot actif.
- MIX : canaux et bus identifiés explicitement ; pas de renumérotation incompatible des paramètres synchronisés.

## 6. Statut exact de cette livraison

**Effectué** : consolidation des décisions, relecture ciblée de `bass_engines.c`, `ztek_engine.cpp`, `docs/ZULUTEK-LOOP.md`, `docs/ZULUTEK-DRUMS.md`, `docs/ZULUTEK-MIX-SIREN.md` et du journal ; inventaire des conflits et plan d'intégration.

**Non effectué** : compilation, exécution des tests, audit exhaustif des handlers, modification de firmware/APK ou validation sur FM-1. Ce document est une **base d'assemblage**, pas un certificat de correction. La partie DRUMS sera inspectée avec le capitaine lors de la prochaine visite ; sa spécification historique ne vaut pas approbation de son ergonomie finale.

## 7. Première intervention réelle dans le moteur APK (23 novembre 2026)

**Commit code** : `f8492f708b4d4b5ec6de45608aaf7f404e64a84e` — `app-zulutek/src/main/cpp/ztek_engine.cpp`.

- `ztek_acid_play(z, 0)` libère désormais explicitement la note `seq_note` du séquenceur acid au lieu de simplement mettre `acid_on=0`. Cela traite un risque concret de note tenue lors de l'arrêt d'ATEK303.
- Dans `ztek_acid_step`, les fins de notes générées par le séquenceur vont directement au moteur de voix courant (`at_gate` / `bass_gate`) plutôt que dans `ztek_note_off`, qui est réservé aux notes de jeu et touche aussi l'enregistrement MIDI LOOP et l'ARP. La génération des Note On acid avait déjà `record=0` ; cette correction aligne les Note Off sur cette séparation.
- **Portée** : modification ciblée du moteur **APK**, **pas** du firmware FM-1 ; aucun changement de raccourci, de format KIT ou d'architecture MIX.
- **Validation restante impérative** : compiler l'APK avec le toolchain Android/Oboe, exécuter des tests audio : ATEK303 note tenue→STOP, transitions de pitch, REST/tie, ARP activé pendant acid, MIDI LOOP en REC, engine BASSE actif, relance après STOP, bascule de moteur. **Ni compilation ni écoute audio exécutées dans cette session.**
- **Risque à revoir** : un séquenceur acid et une note de clavier/ARP partageant une voix mono peuvent encore s'interrompre mutuellement ; la séparation complète des propriétaires de note et le routage BASSE/LEAD seront traités dans la suite.

**Bilan d'avancement** : on est passé de l'inventaire à une **première correction de code**, sans prétendre que toute la chaîne audio est désormais validée.
