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
