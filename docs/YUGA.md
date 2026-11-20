# YUGA — passer le Sony Xperia Z en « mode yuga » (module sonore dédié)

## C'est quoi « mode yuga »

- **yuga** = nom de code Sony du **Xperia Z** (APQ8064 / Adreno 320, 2 Go RAM, sorti
  02/2013, Android 4.1 → 5.1 ; ROM custom **LineageOS `yuga`** existe, port
  postmarketOS/Halium `sony-yuga` en noyau mainline partiel).
- Rôle dans le système Zulu : **module sonore dédié** — ZuluDrums (`:app-drums`,
  boîte à rythme) + ZuluLink y tournent, pilotés depuis la tablette **Vanqio**
  (console, `:app-linktest`). Développement/test sur **Redmi Note 8 Pro**.
- Contrainte matérielle absolue : CPU **strictement 32 bits** (armeabi-v7a, aucun
  cœur ARMv8). Aucun APK `arm64-v8a`-only ne s'y installera, **quelle que soit la ROM**.
  Le yuga tourne en Android 5.1 (API 22) : seuls les modules `minSdk <= 22` passent.

## Quels APK installent sur le yuga (5.1, 32 bits)

| Module | applicationId | minSdk | ABI natif | APK (CI) | Yuga 5.1 |
|---|---|---|---|---|---|
| `:app` | `com.zulu` | 24 | arm64-v8a seul | `app-debug.apk` | ❌ |
| `:app-drums` | `com.zulu.drums` | 21 | armeabi-v7a + arm64-v8a | `app-drums-debug.apk` | ✅ |
| `:app-linktest` | `com.zulu.linktest` | 21 | aucun (Kotlin pur) | `app-linktest-debug.apk` | ✅ |
| `:app-zulutek` | `com.zulutek.preview` | 24 | arm64-v8a seul | `ZuluTek-debug.apk` | ❌ (voir « ZuluTek sur le yuga ») |
| `:app-zulutek-drums` | `com.zulutek.drums` | 24 | arm64-v8a seul | `ZuluTekDrums-debug.apk` | ❌ (idem) |

## Procédure A (recommandée) — rester en 5.1 stock, formater proprement

Bootloader **verrouillé** : les clés DRM Sony sont conservées, aucun flash.

1. **Sauvegarde** : photos/contacts vers carte SD ou compte. (Si un déverrouillage
   du bootloader est prévu un jour : sauvegarder la partition TA avec Backup TA
   **avant** — l'unlock efface les clés DRM.)
2. **Formatage** : Paramètres → Sauvegarder et réinitialiser → **Restaurer les
   valeurs d'usine** ; ou boot en recovery (volume haut + power) →
   `wipe data/factory reset` + `wipe cache partition`.
3. **Premier démarrage** : passer l'assistant, puis activer **Sources inconnues**
   (Sécurité) et **Débogage USB** (Options développeur, 7 taps sur « Numéro de
   build »).
4. **Récupérer les APK** depuis la CI (dernier run vert sur `main`) :
   - PC : `gh run list --repo FlyingZulu/FZuluTek --limit 1` puis
     `gh run download <id> --repo FlyingZulu/FZuluTek --name zulu-apk-<run_number>`
   - Depuis un téléphone : github.com → FlyingZulu/FZuluTek → Actions → run →
     artefact `zulu-apk-<run_number>` (zip). **Ne pas passer par le connecteur
     Vibe pour les binaires** : ils arrivent corrompus (vérifié par SHA, voir
     `docs/PRISE-EN-MAIN.md`) — téléchargement navigateur GitHub uniquement.
   - Le zip contient les 5 APK debug (`app/`, `app-linktest/`, `app-drums/`,
     `app-zulutek/`, `app-zulutek-drums/`).
5. **Installer sur le yuga** uniquement les deux compatibles :
   `app-drums-debug.apk` (ZuluDrums) et `app-linktest-debug.apk` (ZuluLink Test) :
   - copie USB (MTP) → dossier Téléchargements, puis ouvrir chaque `.apk` avec
     le gestionnaire de fichiers → Installer (sources inconnues déjà actives) ;
   - ou via ADB : `adb install app-drums-debug.apk app-linktest-debug.apk`.
6. **Vérification** : `adb shell pm list packages | grep zulu` doit sortir
   `com.zulu.drums` et `com.zulu.linktest` (sinon : icônes « Zulu Drums » /
   « ZuluLink Test » dans le lanceur).
7. **Test du mode yuga** : yuga et Vanqio sur le **même Wi-Fi**, lancer
   ZuluLink Test sur les deux → les pairs se voient seuls (découverte broadcast
   UDP **53333**, direct **53334**, multicast lock géré par l'app). C'est le
   scénario « yuga ↔ poste » de la veille Koala/ZuluLink.
8. **Dédier la machine** : retirer compte Google/synchro, baisser veille écran
   si besoin, garder le Wi-Fi actif (ZuluLink en dépend).

## Procédure B (optionnelle) — flasher LineageOS `yuga`

Seulement si besoin d'Android > 5.1 (ex. faire tourner l'APK WebView ZuluFx,
minSdk 24 et sans `.so`) :

1. IMEI (`*#06#`) → code de déverrouillage sur le site développeur Sony →
   `fastboot oem unlock` (**efface tout + clés DRM perdues** ; partition TA
   sauvegardée avant, étape 1 de la procédure A).
2. Flasher **TWRP**, wipe data/system/cache/dalvik, installer **LineageOS yuga**
   (build **arm 32 bits**) + GApps nano.
3. Le CPU reste 32 bits : les APK arm64-only restent refusés. Avec LineageOS 14.1
   (Android 7.1.2, API 25), les APK minSdk 24 sans natif passent.

## Règles de sideload (rappel)

- Tous les APK sont signés avec la clé fixe `signing/zulu.p12` (alias `zulu`,
  dépôt privé FZuluFx) : **ne jamais supprimer ni régénérer cette clé**, sinon
  Android refusera les mises à jour. Les nouvelles versions s'installent
  par-dessus l'ancienne.
- Sur Android 5.1, « Sources inconnues » est dans Paramètres → Sécurité.
- Ne jamais pousser de binaires (APK, polices, images) via le connecteur Vibe :
  contenu corrompu (vérifié par SHA) → upload manuel navigateur ou template
  repository (`docs/PRISE-EN-MAIN.md`).

## Faire tourner l'APK ZuluTek sur le yuga (plus tard)

`app-zulutek` / `app-zulutek-drums` sont minSdk 24 + arm64-only : il faut un
**build 32 bits** — `minSdk 21` et `abiFilters 'armeabi-v7a', 'arm64-v8a'`
dans leur `build.gradle`. Précédent : fix NEON de DaisySP (`vmaxnm/vminnm.f32`
→ scalaire) pour compiler en armv7 sans FPU NEON (PR #18 de FZuluFx). Sous 5.1,
Oboe retombe sur **OpenSL ES** (AAudio n'existe qu'à partir d'API 26) : latence
plus haute, acceptable pour du contrôle/preset, moins pour du synthé live.

## VANQYO — la tablette console : surface de contrôle agrandie du FM-1

Rôle (Bible Zulu) : **tablette Vanqio = CONSOLE** — elle pilote les modules : le yuga
en ZuluLink (Wi-Fi) et le **FM-1 en USB**. Modèle exact inconnu ; port **micro-USB**
→ génération 2019 probable (type MatrixPad Z4 : Android 9, 2 Go RAM / 32 Go, Wi-Fi
2,4 GHz, USB 2.0 avec OTG). Checklist sur l'appareil : modèle (Paramètres → À propos),
ABI (`adb shell getprop ro.product.cpu.abi` : `arm64-v8a` → APK actuelle OK,
`armeabi-v7a` → build 32 bits, le même que pour le yuga), test OTG (adaptateur
micro-USB → USB-A + clé USB), routage UAC.

### Connexion au FM-1 : un seul câble (micro-USB OTG)

- **USB-MIDI** : SysEx companion `0x60/61/62` (capacité / fichiers / params) +
  commandes ZTF `F0 7D 'Z' 'T' …` (ping, param, page, notes, play, load) — contrôle,
  push/pull de fichiers (ZSMP/ZLUP/ZMAC/ZTP1), miroir d'état.
- **UAC audio** dans les deux sens si le routage le permet (`usb_audio_in` actif côté
  FM-1 ; `usb_audio_out` + hook `app_usb_in`, patch 0010). Monitoring sinon en jack
  sur le FM-1 ; fallback export WAV. USB 2.0 (480 Mb/s) suffit largement (MIDI + UAC1
  ≈ 1,4 Mb/s) ; le FM-1 a sa propre batterie, la puissance 5 V limitée du port ne
  bloque pas.
- **Wi-Fi** : ZuluLink vers le yuga (UDP 53333/53334, même réseau, 2,4 GHz OK) — contrôle
  uniquement ; l'audio suit la règle de routage révisée (jack/RCA/XLR d'abord, puis USB,
  réseau en repli, latence minimale — voir `docs/ZULUTEK.md`).

### Ce que la tablette affiche en synchro

La tablette n'est pas une simple télécommande : c'est une **surface tactile agrandie**
du FM-1, synchronisée en live (watch `0x62` + notifications `PQ_CHANGED`, déjà en v2
côté APK) :

- un **miroir fidèle du LCD du FM-1** (240×240) dans une zone de l'écran tablette ;
- les **modules en grand, tout tactile** : pad **XY**, **boîte à rythme** (page DRUM),
  **synthés** (voix/moteurs), **baie de patch** (page PATCH — vue étendue v3 plein
  écran), **table de mixage** (page MIX : tranches, LDR/SOLO/PAN) ;
- presets **ZTP1** éditables sur tablette puis poussés vers le FM-1 ; push/pull
  samples / loops / macros (page LINK 17).

### Duplication côté FM-1

Sur le LCD 240×240 du FM-1, réserver **une partie de l'écran** à un résumé de ce qui
est affiché en grand sur la tablette (module courant + gros contrôle actif), pour
garder la lisibilité des deux côtés. À prévoir dans un patch firmware (zone/bandeau
dédié de l'écran).

### Layout façon ZuluFX / ZuluX

Même principe que l'UI ZuluFx/ZuluX : **partie gauche = toutes les infos du module
affiché** (état, valeurs, raccourcis), **partie droite = petits contrôles à
tirettes** (sliders verticaux, comme la baie d'effets). Un **layout par module**
(l'UI s'adapte à ce qui est affiché) : XY = grand pad plein écran ; DRUMS = grille
8 pads ; SYNTH = clavier 2 octaves + params ; PATCH = baie de câbles tactile ;
MIX = tranches avec tirettes.

### Blocs déjà en place (à brancher)

- APK (`app-zulutek`, PR #13 mergée) : page LINK 17 — v1 push/pull fichiers, v2
  miroir live `0x62` + télécommande (page/tempo/notes/transport), v3 vue étendue
  (EDIT = baie plein écran) + presets ZTP1. Fichiers : `ZtfTransport.kt`,
  `Fm1Usb.kt`, `Fm1UsbAudio.kt`, `SampleLibrary.kt`, `ZtfCodec.kt`.
- Firmware : patch 0010 (PR #12) — companion `0x60/61/62`, commandes ZTF, hook
  `app_usb_in`. ⚠️ patches 0001–0010 jamais buildés (toolchain JieLi) ni testés
  (fm1-emu) → à faire **avant tout flash**.

### Ordre de travail proposé

1. Identifier la tablette (modèle, version Android, `ro.product.cpu.abi`) et tester
   l'OTG.
2. Si `armeabi-v7a` : build 32 bits de `app-zulutek` (`minSdk` 21/24, `abiFilters
   'armeabi-v7a', 'arm64-v8a'`) — même build que pour le yuga (voir section ci-dessus).
3. Sideload sur la Vanqio : `app-zulutek` + `app-linktest` (clé `zulu.p12` fixe).
4. fm1-emu + build JieLi des patches 0001–0010 avant de flasher le FM-1.
5. Brancher le miroir UI (v2) sur la page LINK, puis les vues agrandies par module
   avec le layout gauche infos / droite tirettes.
6. Côté FM-1 : zone de duplication sur le LCD (patch) une fois l'UI tablette
   stabilisée.

### Chemin audio : micro → Vankyo → FM-1, boucles audio dans la Vankyo

Règle de routage audio révisée (ZuluLink §0.2, `docs/ZULUTEK.md`) : **connexions physiques
d'abord** — jack/RCA/XLR, puis USB (UAC) — réseau (Wi-Fi/BLE) uniquement en repli ; dans
tous les cas, la solution de **latence minimale** gagne.

- **Micro → FM-1** : micro de la Vanqio (ou interface UAC externe sur la tablette) →
  `AudioRecord` (`RECORD_AUDIO` déjà dans le manifest, demandé à l'exécution
  `MainActivity.kt:595-597`) → moteur audio APK → `AudioTrack` sur l'UAC-out
  (`Fm1UsbAudio.kt`, `setPreferredDevice` API 23+, thread `fm1-uac-tx`) → FM-1
  `usb_audio_out`/`uac_play` + hook `app_usb_in` → `zml_usb_feed` (patch 0010) →
  ring resample du looper (REC armé) → chaîne FX du FM-1 → **sortie jack**.
- **FM-1 → Vankyo** : `usb_audio_in`/`uac_tap` → `AudioRecord` sur l'UAC-in (thread
  `fm1-uac-rx`) = capture/miroir de ce que joue le FM-1.
- **Boucles audio longues dans la Vankyo** : tablette = 48 kHz Oboe, heap (`FX_HOST`),
  2-3 Go RAM + microSD → les longues boucles audio tournent et sont stockées côté
  tablette, et montent vers le FM-1 en entrée live par l'UAC. Côté FM-1 le ring est
  court (`ML_RS_MAX` 4096 ≈ 128 ms µ-law 32 kHz, patch 0008 ; ZSMP max 4064 o ≈ 92 ms)
  et le Loop FM-1 est un **looper MIDI** (`docs/ZULUTEK-LOOP.md`) : boucles MIDI +
  presets = ZTF (ZLUP/ZTP1) ; boucles audio longues = APK.
- **Limites** : patch 0010 à builder/tester avant tout flash (fm1-emu + toolchain JieLi) ;
  routage UAC du port micro-USB OTG à vérifier (try/catch, no-op propre sinon ; fallback
  export/import WAV) ; alimentation 5 V de l'OTG à vérifier (le FM-1 a sa propre
  batterie) ; latence UAC1 + ring = dizaines de ms, **pas sample-accurate** — le
  transport/l'horloge passent en MIDI (ZTF play/notes, ZuluLink CLOCK), l'audio suit ;
  full-duplex UAC selon le descripteur UAC du FM-1, sinon half-duplex par session.
