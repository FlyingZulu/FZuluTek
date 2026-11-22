# Android audio & network — handoff technique

Dernière revue : 2026-11-22  
Branche de travail : `fix/android-native-audio-thread-safety`  
Base : `fix/apk-touch-bend-rec` (inclut le correctif tactile REC/LCD de la PR #44)

## Règles impératives pour les prochains assistants

- **Périmètre autorisé : APK Android, Kotlin et moteur natif C/C++ embarqué dans l'APK.** L'utilisateur a explicitement demandé les corrections nécessaires et la documentation. Le firmware FM-1, les patches embarqués et les autres projets restent strictement hors périmètre.
- Ne jamais modifier le firmware FM-1, les patches embarqués ni les autres projets/modules. Les sources C/C++ du moteur Android sont dans le périmètre de correction autorisé.
- Les corrections de concurrence et de DSP sont autorisées dans le moteur natif C/C++ de l'APK. Les contrôles Kotlin passent par JNI ; le callback Oboe ne doit jamais attendre un verrou. Cette autorisation ne s'étend jamais au firmware ni aux patches FM-1.
- Dans la nomenclature de l'utilisateur, **SCL signifie SEL**. Ce n'est pas un bug à renommer ni à corriger.
- Préserver les modifications de la branche `fix/apk-touch-bend-rec` ; travailler sur une branche dédiée issue de celle-ci.
- Ne jamais annoncer un test matériel FM-1/téléphone s'il n'a pas réellement été exécuté. Un build CI réussi n'est pas une validation audio USB ni réseau sur appareil.
- La spécification détaillée de ZuluLink est privée. Ne pas recopier sa spécification complète dans une documentation publique ; ce document ne décrit que les interfaces et constats nécessaires au code Android.

## Contrat de surface à préserver

- `ALG` / `ALGORITHM` est un **encodeur rotatif**. Sur MAIN, sa rotation règle `AT_WAVE` (forme d'onde) ; ce n'est pas un bouton de sélection du moteur. Aucun appui long sur ALG ne doit cycler les moteurs.
- La sélection BASSE/LEAD se fait par HOME court ; les changements de moteur reçus par MIDI utilisent CC 80. Ne pas détourner ALG pour contourner ces commandes.
- Sur MAIN, les 16 touches blanches pilotent les slots du looper. Les touches noires jouent des notes, avec les fonctions spéciales documentées (Shift et commandes sirène/choke selon la page). La page FX/SIREN donne accès au clavier chromatique complet. Ce mapping doit être décrit clairement plutôt que qualifié de panne du moteur.

## Topologie actuelle

### Audio local

`MainActivity` / UI Android  
→ façade Kotlin `ZtekAudio.kt`  
→ JNI  
→ moteur natif `ztek_engine.cpp` et modules DSP  
→ sortie Oboe.

Le sampler utilise également une entrée Oboe séparée. L'état du moteur et les buffers peuvent donc être partagés entre callbacks et appels de contrôle ; la synchronisation doit être garantie dans la couche native, pas par des verrous ajoutés dans le callback audio.

### Liaison USB de contrôle FM-1

`Fm1Usb.kt`  
→ endpoints bulk de l'interface USB-MIDI (MIDIStreaming)  
→ paquets d'événements USB-MIDI de 4 octets  
→ réassemblage SysEx  
→ `ZtfTransport.kt` / `ZtfCodec`  
→ commandes ZTF et companion.

Le protocole ZTF est transporté sur USB-MIDI. Il ne s'agit pas d'un flux audio PCM.

### Liaison USB audio FM-1

`Fm1UsbAudio.kt` utilise `AudioRecord` pour la capture USB UAC (FM-1 vers Android) et `AudioTrack` pour la lecture vers le FM-1. Le routage dépend de la sélection du périphérique USB par Android et de `setPreferredDevice`.

**Limite fonctionnelle importante :** `onCapture` est un callback optionnel, et aucun consommateur PCM compatible n'est actuellement raccordé par `MainActivity`. La capture peut donc lire des échantillons puis les jeter. `ZtekAudio` ne présente pas, dans son API Kotlin actuelle, de fonction publique de push PCM vers le sampler. Ne pas détourner `smpRec` pour cela : cette API pilote l'enregistrement natif Oboe, ce n'est pas un injecteur PCM. Une vraie intégration capture USB → sampler exige une interface native explicitement conçue et autorisée ; en attendant, décrire honnêtement la capture comme non raccordée.

### Réseau ZuluLink

`ZuluLink.kt` utilise UDP :
- port de découverte : HELLO/BYE ;
- port direct : CC, CLOCK, START/STOP, TAP, SNAP, PING/PONG.

**ZuluLink n'est pas un transport audio IP.** Il transporte des commandes, des paramètres et des informations de tempo. Le seul appel CLOCK relevé dans l'UI est lié au tap-tempo ; cela ne constitue pas une horloge réseau continue ni une synchronisation sample-accurate.

## Constats de l'audit

### Corrigé dans cette branche

1. **USB-MIDI :** les paquets sortants étaient alloués à `4 + take` octets et les données commençaient à l'offset 4. En USB-MIDI 1.0, chaque event packet fait exactement 4 octets : en-tête CIN/câble + 3 octets de données. L'écriture utilise désormais l'offset 1 dans un paquet de 4 octets. Le parseur RX lit également les données à partir de l'offset 1.
2. **Cycle de vie USB-MIDI :** `UsbMidiLink.close()` interrompt et attend brièvement le lecteur, libère l'interface revendiquée et ferme la connexion. Les erreurs pendant l'ouverture libèrent aussi la connexion et l'interface si nécessaire.
3. **Permission micro :** `linkAudioToggle()` ne démarre plus `AudioRecord` avant le retour de la permission Android. Le callback de permission reprend l'action si elle est accordée.
4. **Cycle de vie capture :** `stopCapture()` appelle `AudioRecord.stop()` pour débloquer une lecture bloquante, attend le thread et libère l'enregistreur.

### Correctifs supplémentaires dans cette branche

1. **Concurrence JNI/Oboe :** le moteur Android partage ses objets DSP entre les setters JNI et le callback Oboe. Un verrou récursif protège désormais les appels JNI de contrôle/lecture et le callback utilise exclusivement `try_lock()` : il ne bloque jamais le thread audio. Si un contrôle détient brièvement l'état, le callback de sortie émet un bloc silencieux (et le callback d'entrée abandonne le bloc) puis reprend au cycle suivant. `nativeStop` et `nativeDestroy` arrêtent/rejoignent le flux avant de prendre le verrou afin d'éviter un interblocage avec le callback. Cette protection est une première correction de cohérence ; elle doit être validée sur appareils et profilée pour mesurer les blocs audio abandonnés.

### Observabilité du démarrage audio

`MainActivity.onCreate()` appelle actuellement `ZtekAudio.start()` sans exploiter son booléen de retour. Si la création ou le démarrage Oboe échoue, l'interface continue de s'initialiser alors que le handle Kotlin peut rester nul et que les commandes audio deviennent des no-op. C'est un point de diagnostic à traiter : journaliser et rendre visible l'échec de démarrage, puis relever les propriétés réelles du flux et la réception des callbacks. Ce constat n'établit pas que le démarrage Oboe est la cause du son limité à un clic.

### Cycle de vie des touches blanches

`SurfaceView.onTouchEvent()` envoie le DOWN blanc immédiatement et le callback long après 500 ms. Le relâchement est maintenant toujours envoyé sur UP/CANCEL, même après l'action longue, afin de garantir note-off / OFF. Voir la section « Correctif clavier » ci-dessus. Ce correctif est commité, mais n'a pas encore été validé sur appareil.

### Risques / travaux encore ouverts

1. **Concurrence moteur natif :** le verrouillage JNI/Oboe est maintenant en place dans cette branche. Il reste à valider la latence et l'absence de glitches sur appareils Android variés ; un modèle de commandes lock-free serait préférable si le profilage montre des blocs abandonnés.
2. **Capture USB sans destination PCM :** callback non raccordé au sampler. Ne pas prétendre que FM-1 → Android → sampler fonctionne de bout en bout.
3. **Sélection UAC :** `findUsbDevice()` prend le premier périphérique audio USB exposé par Android, pas nécessairement celui du FM-1. Tester les cas avec plusieurs périphériques USB et envisager une association plus fiable.
4. **Opérations asynchrones Fm1Usb :** `ping()`, `pull()` et `push()` renvoient actuellement des valeurs locales avant l'exécution du worker ; leurs retours synchrones sont donc trompeurs. Préférer des callbacks, futures ou résultats livrés par le worker avant d'exposer ces valeurs.
5. **Ordre permission/connexion USB :** `scan()` demande la permission de façon asynchrone. Des commandes déclenchées immédiatement ensuite peuvent être mises en file avant l'établissement de la connexion et échouer. Prévoir une notification `onConnected` puis lancer les commandes dépendantes.
6. **ZuluLink :** les datagrammes sont désormais filtrés sur magic/version et les messages directs inconnus sont ignorés. La résolution d'un pair utilise son adresse IP au lieu d'indexer la map (indexée par nom) avec l'IP. Cela ne fournit **aucune authentification cryptographique** : un pair LAN peut potentiellement usurper une adresse/identité.
7. **Validation ZuluLink :** vérifier les longueurs exactes par type, les valeurs numériques (tempo/beat) et la gestion des sockets au démarrage. Le démarrage peut encore échouer si un port UDP est déjà pris.
8. **Horloge réseau :** pas de clock périodique ni de correction de phase/jitter constatée. Ne pas ajouter de boucle d'horloge approximative sur le thread UI. Concevoir une synchronisation dédiée seulement si elle est demandée.
9. **USB-MIDI :** vérifier sur matériel FM-1 que le descripteur et le firmware suivent bien le format USB-MIDI standard. Le correctif est fondé sur l'interface MIDIStreaming et les paquets event 4 octets documentés dans le code.

## Plan de validation à chaque modification

1. Compiler l'APK debug via le workflow GitHub Actions.
2. Vérifier que seuls les fichiers Android/Kotlin et la documentation autorisée changent.
3. Tests USB-MIDI sur appareil : connexion, ping ZTF, liste de fichiers, transfert SysEx de tailles 1/2/3 et tailles non multiples de 3, notifications companion, déconnexion/reconnexion.
4. Tests permission : première demande, autorisation, refus, arrêt pendant capture, reprise après arrêt.
5. Tests UAC sur appareil : vérifier le périphérique routé, la fréquence effective, l'absence de fuite de ressources et le chemin réel des données PCM. Tant que le consommateur PCM n'existe pas, ne pas valider la capture comme une fonction sampler opérationnelle.
6. Tests réseau sur deux appareils : découverte, pairs renommés ou adresse IP modifiée, messages directs, paquet malformé, source inconnue, perte/reprise réseau et port déjà occupé.
7. Tests physiques FM-1 séparés des tests CI. Noter précisément le modèle Android, version Android, FM-1/firmware et le résultat observé.

## Fichiers de référence

- `app-zulutek/src/main/java/com/zulutek/preview/MainActivity.kt`
- `app-zulutek/src/main/java/com/zulutek/preview/ZtekAudio.kt`
- `app-zulutek/src/main/java/com/zulutek/preview/Fm1Usb.kt`
- `app-zulutek/src/main/java/com/zulutek/preview/ZtfTransport.kt`
- `app-zulutek/src/main/java/com/zulutek/preview/Fm1UsbAudio.kt`
- `app-zulutek/src/main/java/com/zulutek/preview/ZuluLink.kt`
- `app-zulutek/src/main/cpp/ztek_engine.cpp` et `jni_bridge.cpp` (moteur natif Android ; le firmware reste non modifié)

## Historique de cette branche

- Créée depuis `fix/apk-touch-bend-rec`, afin de ne pas modifier la PR #44.
- Correctif tactile REC/LCD conservé par héritage.
- Correctifs Kotlin de framing USB-MIDI, fermeture USB, permission audio et validation/identification des pairs UDP.
- Build et essais matériels à consigner après exécution ; aucune validation matérielle n'est présumée par ce document.


## Correctif clavier — relâchement après appui long (2026-11-22)

Dans `SurfaceView.onTouchEvent()`, les touches blanches envoyaient immédiatement `onWhiteKey(idx, true, false)` à l'appui. Après 500 ms, `longRunnable` exécutait l'action longue puis mettait `longPosted = false`. Le traitement `ACTION_UP/ACTION_CANCEL` conditionnait alors l'événement de relâchement à `longPosted` : aucun `onWhiteKey(idx, false, false)` n'était envoyé après un appui long.

Conséquences possibles :
- sur SIREN, une note blanche démarrée à l'appui pouvait ne jamais recevoir son `noteOff` ;
- sur LINK, une note déclenchée à distance pouvait ne jamais recevoir son message OFF ;
- sur les autres pages, le relâchement des actions momentanées pouvait être perdu.

Correction : le relâchement d'une touche blanche est maintenant toujours transmis sur `ACTION_UP` et `ACTION_CANCEL`, indépendamment de l'exécution du callback long. Le callback long conserve son rôle (ex. armement/enregistrement du slot looper) ; le mapping et les fonctions des touches ne changent pas.

Validation : correction de code commitée sur la branche Android. Aucun build CI ni test tactile/audio sur appareil n'est encore revendiqué pour ce commit. À vérifier dans le prochain APK : appui bref et appui >500 ms sur une touche blanche en SIREN et LINK, puis vérifier que la note s'arrête au relâchement. Vérifier également que l'appui long d'un slot looper conserve son armement/enregistrement attendu.


## Apparence du clavier APK — touches blanches et roses (2026-11-22)

Le clavier tactile Android utilise désormais une palette dédiée, indépendante des skins DA ZULU / MAISON :
- touches blanches : ivoire clair (`WHITE_KEY_TOP/BOT`) ;
- touches noires : rose (`PINK_KEY_TOP/BOT`).

Les états actifs continuent de recevoir la surbrillance ambre existante (touches jouées, slots actifs ou armés). Les dimensions, positions, zones de hit-test, notes MIDI, slots du looper, glissando et événements DOWN/UP ne sont pas modifiés par ce changement de palette.

Commit : `f88d38a69bf5a846aefb2270db913f77b8ac7cd8`. Vérification statique des quatre couleurs et des deux chemins de dessin effectuée. Le rendu réel, le contraste et la lisibilité restent à confirmer sur appareil après génération de l'APK.


## Démarrage audio Android observable — 2026-11-22

Constat : `MainActivity.onCreate()` appelait `ZtekAudio.start()` sans examiner son booléen de retour. En cas d'échec de `nativeCreate()` ou `nativeStart()`, l'interface continuait donc à s'initialiser alors que les commandes audio Kotlin étaient ensuite des no-op (handle nul).

Correction : le résultat est maintenant contrôlé. Le succès est journalisé sous `ZuluTekAudio` avec la fréquence demandée ; l'échec est journalisé en erreur et affiche un Toast explicite « Audio indisponible : démarrage du moteur synthé échoué ». Aucun changement du DSP, des gains, du routage, des événements clavier ou du firmware.

Commit : `8ca0aaf54911a9bfe616823ffa2882e3046fe88b`.

Validation : inspection statique du chemin `start()` et de la gestion du handle Kotlin. Le build de cette nouvelle modification n'a pas encore été exécuté au moment de cette note ; le run #337 réussi concerne le commit précédent `f88d38a69bf5a846aefb2270db913f77b8ac7cd8`. Aucun résultat de test appareil n'est revendiqué.

Suite : compiler cette modification, puis sur Android provoquer/observer un démarrage normal et consulter Logcat (`ZuluTekAudio`). Si l'échec apparaît, relever la cause native Oboe (résultat d'ouverture / démarrage, fréquence, format, canaux) plutôt que modifier arbitrairement le gain. Si le flux démarre, instrumenter les callbacks et le niveau crête de sortie pour suivre une note depuis le toucher jusqu'au buffer Oboe.


## Diagnostic du rendu LCD Android — 2026-11-22

Le LCD reste signalé blanc par l'utilisateur. L'inspection statique confirme que `LcdView.onDraw()` remplit le Canvas écran et le framebuffer en noir avant de dessiner, puis blitte un bitmap RGB565. `MainActivity` superpose le `LcdView` au `SurfaceView` et positionne le LCD à partir de `surface.lcdRect()`. Cela ne suffit pas à identifier la cause visuelle : il faut mesurer les dimensions réelles, l'emplacement et vérifier que `onDraw()` s'exécute.

Instrumentation ajoutée, sans changer le rendu :
- tag Logcat `ZuluTekLCD` sur le premier changement de taille de la vue LCD ;
- trace du premier `onDraw()` (taille, échelle, mode wide, visibilité) ;
- trace unique du placement (dimensions SurfaceView, rectangle LCD, dimensions et marges du LCD).

Commits : trace placement `7b34b62f5dfbba303b6780597c5fe18883e0e80a` ; trace taille/dessin `a4f3fa0d40528903e75204b6cc4b5c8c6bf39053`.

Validation : instrumentation statique uniquement ; build CI #342 déclenché sur le head `a4f3fa0d40528903e75204b6cc4b5c8c6bf39053` et encore en attente au moment de cette note. Pas de test appareil. Prochaine observation sur téléphone : filtrer Logcat `ZuluTekLCD` ; absence de « first draw » = problème de visibilité/invalidation/lifecycle ; dimensions/placement incohérents = problème de layout ; traces correctes avec écran toujours blanc = poursuivre dans le blit/composition SurfaceView.



## Audit de sélection des moteurs de voix — 2026-11-22

Constat vérifié dans `MainActivity.kt` et `docs/ZULUTEK.md` :
- HOME court fait défiler les parties BASSE / LEAD / DRUMS / LOOP ; il ne propose que LEAD (engine 0) et BASSE (engine 1) depuis l'interface locale.
- Le seul chemin observé qui sélectionne explicitement les cinq moteurs 0..4 est le CC MIDI 80 reçu via ZuluLink.
- Le moteur natif accepte bien 0..4 (`ztek_set_engine()` borne l'index à `AT_NENGINES`), donc le manque observé est côté commande locale APK, pas dans le setter natif.

Conséquence : depuis les commandes tactiles APK actuelles, les moteurs 2 FM, 3 SUB et 4 8BIT ne disposent pas d'un sélecteur local dédié. Je ne détourne pas ALG (encodeur rotatif waveform sur MAIN) ni HOME long (LINK) pour éviter de casser le contrat documenté. Il faut définir un contrôle local compatible avec la bible avant de modifier le mapping.

Validation : lecture statique uniquement ; aucun test appareil. Aucun code moteur modifié.



## Sélecteur local des cinq moteurs de voix — 2026-11-22

L'audit a montré que l'APK n'offrait localement que LEAD/engine 0 et BASSE/engine 1 ; le CC MIDI 80 était le seul chemin permettant d'atteindre 2 FM, 3 SUB et 4 8BIT. Le setter natif gérait déjà les cinq valeurs.

Correction additive, sans détourner les commandes existantes :
- page OPTIONS (16) : nouvelle ligne ENGINE ;
- SEL choisit la ligne, ALG règle la valeur 0..4, selon le même modèle que les autres options ;
- libellés : ATEK303, PLUK, FM, SUB, 8BIT ;
- application immédiate à `ZtekAudio.setEngine()`, affichage via `LcdState.engineNames` et persistance SharedPreferences (`engine`);
- CC80 distant met également à jour la préférence et la partie BASSE/LEAD ;
- HOME court, HOME long/LINK et ALG waveform sur MAIN restent inchangés. Reset de la ligne ENGINE ou reset global = engine 0.

Commits code : `c157c75a6f17ff010b64829b5048d77d0879f55c` (MainActivity), `69729922105169b115dfcb4739cfb29a543f81ad` (LcdView). Bible `docs/ZULUTEK.md` mise à jour dans 407e087e9e0bb405144c961b32bf16ad42bb6c81.

Validation : inspection statique des plages, du setter, de la préférence et de l'affichage. Build du nouveau code et validation tactile sur appareil à effectuer ; aucun test appareil revendiqué.


## APK Android — option VHS indépendante de ECO (2026-11-22)

Constat : le dessin des scanlines VHS était conditionné par `!st.ecoOn`, donc l’activation de ECO supprimait l’effet, contrairement à la demande.

Correction :
- ajout de `VHS` dans OPTIONS (page 16), après ENGINE ; SEL sélectionne la ligne et ALG règle ON/OFF ;
- préférence `vhs` dans SharedPreferences, chargée au démarrage avec défaut `true` pour conserver l’aspect VHS existant ;
- scanlines dessinées selon `st.vhsOn`, jamais selon ECO ; ECO conserve uniquement ses effets d’économie/throttling/blink ;
- reset VHS et reset global restaurent VHS=ON ;
- aucun changement aux paramètres audio, à la palette clavier, ni aux zones tactiles existantes.

Commits code : LcdView `81352ba2c67949e42fb9e871a8d84f46105cf4d5`, MainActivity `ab3305b035152e0c3dbc696740a1ab07303d5c87`. Documentation bible : eee336a386a04cd867d8245e1b7925a71f767ad8.

Validation : contrôles statiques des indices OPTIONS (0..6), affichage de la septième ligne, chargement/défaut/persistance, reset et condition de dessin VHS. Build CI et test tactile sur appareil restent à effectuer ; aucun test appareil revendiqué.

Complément de revue statique : la borne de surbrillance de la ligne sélectionnée a également été étendue à l’index 6 (commit LcdView `fe0838d47d65ce86a868dc39d52730720a2d014d`) ; sans cette correction, la ligne VHS aurait été affichée mais pas sélectionnable visuellement.

## VHS — couverture de toutes les pages LCD

Revue du chemin de rendu : la majorité des pages appellent `blitTo()` puis quittent `onDraw()` par un `return`. Les scanlines dessinées uniquement à la fin de `onDraw()` ne couvraient donc que le chemin de rendu sans retour anticipé ; OPTIONS et plusieurs autres pages n’étaient pas couvertes. Correction : déplacer l’application des scanlines dans `blitTo()`, après le blit du framebuffer et la grille, en coordonnées écran calculées depuis le rectangle LCD. Le réglage `st.vhsOn` s’applique désormais à chaque page qui passe par le blit commun. Commit LcdView : `0297db2a17e8b729e8d81c1bd35dce78caa7312f`.

## Audit palette clavier APK — 2026-11-22

Inspection de `SurfaceView.kt` : les constantes `WHITE_KEY_TOP/BOT` (ivoire `#FFFFFEFC` → `#FFE4E0E5`) et `PINK_KEY_TOP/BOT` (`#FFFF8BC8` → `#FFD92B83`) sont bien celles utilisées directement dans les deux boucles de dessin des touches blanches et noires. `applySkin(st.skinMaison)` est appelé avant le dessin, mais les couleurs des touches sont des constantes dédiées, non remplacées par la palette DA ZULU/MAISON. Le seul mélange dynamique est l’état actif (teinte ambre). Conclusion : pas de défaut de palette évident dans le code actuel ; la teinte turquoise rapportée n’est pas reproductible par inspection statique seule et doit être comparée à l’APK construit puis mesurée sur capture/appareil. Aucun changement clavier supplémentaire effectué ici.

## Audit tactile LCD — paramètres génériques non encore éditables

Le listener `lcd.setOnTouchListener` de `MainActivity.kt` prend en charge le bend (REC maintenu), la baie PATCH (tap = PLAY/assignation) et le pad XY (tap/drag + HOLD/MOM). Les événements `ACTION_MOVE` sont exploités pour bend et XY. En dehors de ces cas, le listener retourne tout de même `true` et consomme le toucher, sans sélection/édition générique des paramètres dessinés sur MAIN ou les autres pages. Le sélecteur d’options et les encodeurs matériels restent opérationnels, mais ils ne constituent pas l’édition tactile demandée. Aucune interception générique n’a été ajoutée dans cette intervention pour éviter de détourner les gestes PATCH/XY/BEND ni d’inventer un mapping natif ; la suite doit établir, page par page, le lien entre chaque zone dessinée, son état UI, l’encodeur équivalent et le setter `ZtekAudio` correspondant, puis ajouter sélection + glisser sans casser les gestes existants.


## VHS — validation CI et polling (2026-11-22)

- Build #362 / run `44992316338` : succès sur le commit `fead29cd20935683454a553b2c020beba59bf28c`. Étapes de compilation des APK de test et publication réussies ; artefact `zulu-apk-362`, 6 317 504 octets. L'étape de génération de l'APK Release dédiée a été ignorée : ne pas confondre l'artefact CI avec une release signée.
- Le commit suivant `96728ede72f0aed37d2ce51c2e05440a4c400f16` supprime la double lecture des 16 sources de modulation lorsque VHS est activé. Il n'est pas inclus dans le run #362 ; un build du head actuel reste nécessaire pour valider l'ensemble.
- Aucun essai sur appareil Android physique n'a été effectué. La stabilité du rendu, le comportement tactile, la continuité audio et la mesure visuelle restent à valider sur appareil.
- Périmètre inchangé : APK Android uniquement ; aucun firmware/patch embarqué FM-1 modifié.


## Diagnostic LCD et audio — 2026-11-22

Objectif : transformer les symptômes « LCD blanc » et « audio clic seul » en observations Logcat vérifiables, sans supposer une cause matérielle.

- LcdView : log one-shot au premier blit avec dimensions de la vue et du Canvas, échelle, décalage, clip et nombre de pixels non noirs du framebuffer. Commit `9a1c46afc245d448669d60a7d1d24e7240ad3b7f`.
- Oboe : log one-shot du premier callback (frames, fréquence effective, canaux, format, device ID, frames/burst), de la première contention du verrou et du niveau de crête du premier bloc rendu après une note. Commit `beb3be0767772b4b14bd56ca79b1a0e265e9c6d4`; optimisation pour ne calculer la crête qu'après un déclenchement de note : `cb384588d184118e899c62f2f6dce71a9152c927`. Aucun calcul de diagnostic continu dans le callback audio.
- Correction de la conversion RGB565 : masque rouge corrigé de 8 à 5 bits ; vert 6 bits, bleu 5 bits, alpha préservé. Commit `7753fa2e9892f48f0971c9ec038e6542527fe54d`. Cette erreur affectait la précision de couleur, mais n'explique pas à elle seule l'écran blanc.
- Builds #365 (run `44992852556`) et #366 (run `44992855477`) réussis, artefacts `zulu-apk-365` (6 317 817 octets) et `zulu-apk-366` (6 318 429 octets).
- Build #368 (run `44992946473`) : compilation et publication réussies sur le head intégrant la correction RGB565 et l'optimisation du diagnostic audio ; artefact `zulu-apk-368` (6 318 292 octets), étape Release dédiée ignorée sur la branche PR.
- Build #367 (run `44992903393`) lancé sur le commit intermédiaire RGB565 seul ; il n'est pas nécessaire pour la validation du head ultérieur #368.
- Les logs ne deviennent probants qu'après lancement sur un appareil Android et reproduction des symptômes. Aucun essai matériel effectué à ce stade. Aucun changement du DSP audio, du routage, du verrouillage ou du firmware FM-1 dans cette passe.


## Audit complémentaire — intégrité des transports ZTF / USB-MIDI / ZuluLink (2026-11-22)

### Défauts corrigés
- `ZtfTransport.zrequest()` ignore maintenant les réponses ZTF dont le caractère de commande ne correspond pas à la requête active. Cela évite qu'une trame retardée ou étrangère valide accidentellement une autre opération.
- `fileRead()` rejette les tailles impossibles à allouer dans un tableau JVM et interrompt explicitement une lecture qui ne retourne aucun octet utile, au lieu de boucler sans progression.
- `fileWrite()` valide l'offset de reprise renvoyé par BEGIN et exige que chaque réponse DATA fasse progresser l'offset sans dépasser la taille annoncée.
- `UsbMidiLink.send()` exige désormais que chaque paquet d'événement USB-MIDI de 4 octets soit intégralement transféré. Un transfert partiel n'est plus déclaré comme un succès.
- `ZuluLink.start()` lie les deux sockets avant de lancer les threads et nettoie les ressources si l'un des ports ne peut pas être ouvert. L'échec est exposé par `lastStartError`, renvoyé comme `false`, puis journalisé par MainActivity ; un nouvel essai reste possible.
- Un changement d'adresse IP d'un pair ZuluLink déclenche désormais `onPeersChanged`.
- Les paquets ZuluLink entrants valident la plage MIDI (CC 0..127, canal 0..15, valeur 0..127), les tempos finis (30..300 BPM), une position beat finie/non négative et un timestamp non négatif. Les PING trop courts ou sans timestamp positif sont ignorés.

### Commits Android
- `e65ad086b07fb14707799f92b673e9c12c60ee0f` — réponses ZTF et progression des transferts.
- `8889a214f13c3531912cbd5f49ac3300fc04bfa3`, `dc0918f002f9e90f2d7345b735fa06da7b7cad49`, `3a00748d2b9b9e2b4923f0f252468f4c035cdc76` — démarrage ZuluLink récupérable, diagnostic UI et capture non nullable des sockets.
- `433490e915324460166e9dda39f428b12024b1df` — rejet des écritures USB-MIDI partielles.
- `6a8eb2e20b16af52d74bcacb57ea8b0c4cf963dc` — validation des champs réseau entrants.

### Validation
- Builds #370 à #373 : succès ; artefacts debug CI publiés.
- Build #374 / run `44994695432` : succès sur `433490e915324460166e9dda39f428b12024b1df`, artefact `zulu-apk-374` (6 319 159 octets).
- Build #375 / run `44994810690` : en cours sur le head `6a8eb2e20b16af52d74bcacb57ea8b0c4cf963dc` au moment de cette note. Son résultat doit être vérifié avant de considérer la validation du dernier contrôle réseau terminée.
- Aucune validation sur matériel USB/FM-1 ou réseau multi-appareils n'a été réalisée. Les correctifs sont des protections de protocole et de cycle de vie statiquement auditées, pas une preuve de fonctionnement matériel.
- Les API haut niveau `Fm1Usb.ping()/pull()/push()` conservent encore leurs retours synchrones trompeurs alors que l'opération est mise en file sur le worker ; leur évolution vers des callbacks/résultats asynchrones reste à traiter séparément, après revue des appelants.
- Aucun firmware FM-1, patch embarqué ni projet sans rapport n'a été modifié.


## Cycle de connexion USB : synchronisation des premières requêtes (2026-11-22)

- Défaut constaté dans `MainActivity.linkScan()` : après `Fm1Usb.scan()`, l'écran lançait immédiatement PING, refreshFiles et WATCH. Quand Android demandait encore la permission USB, ou que l'ouverture/claim de l'interface était en attente, ces requêtes pouvaient passer avant l'établissement de `transport` et échouer avec « FM-1 non connecté ».
- Correction : `Fm1Usb.onConnected` est déclenché sur le worker après la création du lien, l'instanciation de `ZtfTransport` et le passage de `connected=true`. `MainActivity.linkScan()` poste alors PING, refreshFiles et WATCH après ce signal.
- Les méthodes `ping/pull/push/pushNamed/pullNamed` ne renvoient plus de valeurs synchrones qui étaient en réalité des valeurs locales par défaut avant l'exécution du worker. Elles sont maintenant explicitement asynchrones ; résultat et progression passent par `onStatus`, `onProgress` et `onFiles`.
- Revue des 11 fichiers Kotlin de l'application : les appelants trouvés ne consomment pas ces anciennes valeurs de retour ; les appels sont des commandes dont les résultats sont affichés via callbacks.
- Commits : `088051da7f3b6cbbe05aca0e5e97b8431b2499c3` (callback ready), `7a3acd1cd00e615f662b84f747e20b4b0f64512a` (ordonnancement LINK), `774f685c35a12aae59fd0f6f70b111af09b9635d` (contrat API asynchrone).
- Build #376 / run `44994960851` : succès sur le handoff précédent, artefact debug `zulu-apk-376` (6 319 448 octets).
- Builds #377 / run `44995039610`, #378 / run `44995046947` et #379 / run `44995115379` : en cours au moment de cette mise à jour ; le #379 porte le head contenant les changements d'API asynchrone.
- Aucun test matériel USB/FM-1 n'a été effectué. Vérifier sur appareil les scénarios permission accordée/refusée, connexion déjà autorisée, déconnexion pendant scan et nouvelle tentative.


## Notifications companion pendant les requêtes ZTF brutes (2026-11-22)

- Dernière lacune identifiée : `ZtfTransport.request()` conservait les notifications PQ_CHANGED reçues pendant une requête companion, mais `zrequest()` (commandes ZTF brutes) ignorait toutes les trames non-ZTF. Une opération PING/LOAD/paramètre pouvait donc faire perdre des notifications de paramètres en direct.
- Correction : `zrequest()` analyse désormais les trames companion reçues pendant l'attente et place les messages `CMD_PARAMS / PQ_CHANGED` dans la même file `notes`, sans les confondre avec la réponse ZTF attendue.
- Commit : `5331cb00924fb14820fe9cd6af08f38002a83cf9`.
- Build #381 / run `44995223691` : en cours au dernier contrôle ; il compile cette correction.
- Toujours aucun test matériel USB/FM-1. Le comportement des notifications pendant transfert/commande reste à vérifier sur appareil.
