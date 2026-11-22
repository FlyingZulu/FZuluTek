# Android audio & network — handoff technique

Dernière revue : 2026-11-22  
Branche de travail : `fix/apk-audio-network-transport`  
Base : `fix/apk-touch-bend-rec` (inclut le correctif tactile REC/LCD de la PR #44)

## Règles impératives pour les prochains assistants

- **Périmètre autorisé : APK Android, Kotlin et moteur natif C/C++ embarqué dans l'APK.** L'utilisateur a explicitement demandé les corrections nécessaires et la documentation. Le firmware FM-1, les patches embarqués et les autres projets restent strictement hors périmètre.
- Ne jamais modifier le firmware FM-1, les patches embarqués ni les autres projets/modules. Les sources C/C++ du moteur Android sont dans le périmètre de correction autorisé.
- Ne pas tenter de « corriger » un risque de concurrence du moteur natif depuis Kotlin : le signaler et proposer une interface native dédiée seulement si l'utilisateur autorise ultérieurement ce périmètre.
- Dans la nomenclature de l'utilisateur, **SCL signifie SEL**. Ce n'est pas un bug à renommer ni à corriger.
- Préserver les modifications de la branche `fix/apk-touch-bend-rec` ; travailler sur une branche dédiée issue de celle-ci.
- Ne jamais annoncer un test matériel FM-1/téléphone s'il n'a pas réellement été exécuté. Un build CI réussi n'est pas une validation audio USB ni réseau sur appareil.
- La spécification détaillée de ZuluLink est privée. Ne pas recopier sa spécification complète dans une documentation publique ; ce document ne décrit que les interfaces et constats nécessaires au code Android.

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

11. **Sélection locale du moteur :** la page MAIN n'exposait pas de commande locale pour choisir les cinq moteurs ; le changement n'était possible que par CC MIDI distant ou par le cycle de parties qui ne sélectionnait que le premier moteur de basse. Un appui long sur l'encodeur `ALG` en page MAIN fait maintenant défiler `ATEK303 → PLUK → FM → SUB → 8BIT`, appelle `ZtekAudio.setEngine()` et met à jour l'état affiché. L'appui court `ALG` conserve son réglage d'onde et le cycle HOME conserve ses fonctions existantes.

10. **Concurrence JNI/Oboe :** le moteur Android partage ses objets DSP entre les setters JNI et le callback Oboe. Un verrou récursif protège désormais les appels JNI de contrôle/lecture et le callback utilise exclusivement `try_lock()` : il ne bloque jamais le thread audio. Si un contrôle détient brièvement l'état, le callback de sortie émet un bloc silencieux (et le callback d'entrée abandonne le bloc) puis reprend au cycle suivant. `nativeStop` et `nativeDestroy` arrêtent/rejoignent le flux avant de prendre le verrou afin d'éviter un interblocage avec le callback. Cette protection est une première correction de cohérence ; elle doit être validée sur appareils et profilée pour mesurer les blocs audio abandonnés.

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
