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
