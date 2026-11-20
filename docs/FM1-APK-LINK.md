# FM-1 ↔ APK (Android) — liaison « ZTF » : pistes de conception

SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
Doc de conception (dépot privé ; la spec ZuluLink reste confidentielle et n'est PAS décrite ici).

Objectif demandé : **l'APK du smartphone sert de bibliothèque et de télécommande/extension du FM-1**,
sans saturer sa mémoire : samples stockés sur le téléphone, « glissés » dans le FM-1 ; presets et loops
transférables aussi. Deux modes visés :

- **Mode A — « deux machines »** : FM-1 et APK communiquent largement (params, transport, notes, snapshots),
  comme deux machines en réseau.
- **Mode B — « extension » (idéal)** : l'APK augmente le FM-1 — il le pilote à distance, lui pousse des
  samples/presets/loops, et reflète son état (l'APK embarque **le même moteur DSP** que le firmware :
  « un même moteur des deux côtés »). Le FM-1 « sample via l'APK » : l'APK choisit le sample, le pousse,
  le FM-1 le charge et le joue. À terme : page « vue étendue » sur le téléphone (éditeur de presets,
  baie de patch avec boîtes/câbles) — voir §6.

## 1. Transport : USB-MIDI (disponible aujourd'hui, aucun changement hardware)

Le FM-1 énumère déjà en **USB-MIDI** (MIDI in/out de l'ATEK303) + USB-audio. Côté téléphone : USB host
(`android.hardware.usb`), on réclame l'interface classe MIDI (class 0x01, subclass 0x03), endpoints bulk
IN/OUT, et on parle SysEx. Aucun VID/PID nécessaire : on filtre par classe d'interface.

Trame SysEx (déjà utilisée par le platform fm1-base — `device/companion.c`, `device/backup.c`, ota.c) :

```
F0 pack7( 00 59 cmd len24 body… sum8(body) ) F7
```

- `pack7` : 8 octets de body → 7 octets 7-bit (little-endian, comme ota.c).
- ≤ 640 octets entre F0 et F7 ; une requête à la fois (répondre avant la suivante).
- 512 octets de données fichier max par écrit ; CRC-32 vérifié par le device avant validation.

BLE est une piste plus tard (`fm1.toml ble = false` aujourd'hui ; `plat_ble_*` existe) — USB d'abord.

## 2. Services platform à activer côté firmware (patch 0009)

`fm1.toml` : `companion = true` (déjà `backup = true`) → `device/companion.c` fournit :

| cmd | service | usage zuluTek |
|---|---|---|
| `0x60` | capability query | nom/version firmware, tags propres (`APP_COMPANION_EXTRAS "app=ztek"`) |
| `0x61` | **files** | liste/lecture/écriture/suppression de fichiers (CRC-32), `APP_FILE_OBJS` (objets store) + `APP_FILES` (fichiers propres, gardés dans la zone data 0x97000..0xDFFFF) |
| `0x62` | **parameters** | `APP_PARAMS` : get/set de paramètres + notification de changement → miroir d'état live (mode A) |

Le firmware déclare de plus `APP_SYSEX` pour ses commandes propres « ZTF » (voir §4).

### Fichiers exposés (APP_FILES, zone data flash — le FM-1 ne saturera pas : ~288 Ko de zone data,
plus fenêtres XDATA en option ; plafond par fichier `APP_FILE_OBJ_MAX`) :

| nom | format | contenu |
|---|---|---|
| `ZSMP00..07` | `ZSM1` + sr u32 + len u32 + fmt u8 + payload | sample slot (µ-law 8 bits ou int16 mono, rééchantillonné 44,1 kHz) — poussé depuis l'APK, joué via le ring resample du looper (slot ≤ 4096 éch.) ou stocké |
| `ZLUP` | `ZLB1` + état `ml_t` (événements, sp, len ; ring non persisté) | banque de loops MIDI (16 slots) |
| `ZPRE00..31` | blob `ZTP1` existant (`ztek_preset_export`) | presets — même format APK/FM-1, interchangeables |
| `ZMAC` | 16 slots {dst, val} | banque de macros |

Règles : un sample « long » reste sur le téléphone ; on ne pousse dans le FM-1 que des extraits courts
(µ-law 44,1 kHz ≈ 44 Ko/s → ~1-2 s max par slot selon l'espace libre). La bibliothèque complète vit
côté APK (`filesDir/ztek/samples/*.wav`).

## 3. Côté APK (app-zulutek)

- **`Fm1Usb.kt`** : USB host — détection interface MIDI, permission, `UsbDeviceConnection`, endpoints
  bulk, encode/décode pack7, requête→réponse, transferts fichier chunkés (512 o) avec progression + CRC-32.
  (Le framing host existe déjà en Python : `fm1-base/tools/fm1_companion.py` — à porter en Kotlin.)
- **`SampleLibrary`** : import WAV (Koala Sampler exporte du WAV PCM16) via SAF (`ACTION_OPEN_DOCUMENT`,
  `audio/*`), stockage `filesDir/ztek/samples/`, lecture via page SMP, export/share WAV (FileProvider),
  encode µ-law/int16 pour le push FM-1. Banque de loops : export/import `ZLB1` sur le téléphone.
- **Page LINK** (nouvelle page APK) : liste les fichiers du FM-1 (0x61), push/pull de samples, loops,
  presets, macros ; miroir des params (0x62) en mode A ; télécommande (transport, pages, notes) en mode A.

## 4. Commandes ZTF propres (APP_SYSEX, SysEx `F0 7D 5A 54 … F7` — distinct du protocole announce « SR »)

| cmd | sens | effet |
|---|---|---|
| `PING`/`PONG` | A→B, B→A | présence, nom, version, espace data libre |
| `PARAM i v` / `PARAM? i` | les deux sens | get/set d'un paramètre (0x62 suffit, ZTF pour le live rapide) |
| `PAGE p` / `PAGE?` | A→B | changement de page FM-1 (télécommande) |
| `TRIG note vel` / `OFF note` | A→B | notes jouées sur le FM-1 depuis l'APK |
| `PLAY/STOP` | A→B | transport |
| `SNAP GET` / `SNAP PUT len` | les deux sens | snapshot complet (preset courant) — mode A « communication totale » |
| `LOAD kind idx` | A→B | charge un fichier poussé (sample→ring, loop bank, preset) |

## 5. Feuille de route

1. **v1 — transfert de données (maintenant)** : patch 0009 firmware (`companion=true`, APP_FILES
   ZSMP/ZLUP/ZPRE/ZMAC + `app_file_commit` → chargement), APK : `Fm1Usb.kt` + `SampleLibrary` + page LINK
   (push/pull). Tests host : pack7 encode/décode, CRC, formats ZSM1/ZLB1/ZTP1 (round-trip).
2. **v2 — mode A « deux machines »** : APP_PARAMS + commandes ZTF live (page/transport/notes/snap),
   miroir d'état dans l'APK (même moteur des deux côtés → WYSIWYG).
3. **v3 — mode B « extension »** : éditeur de presets + baie de patch en « vue étendue » sur le téléphone
   (boîtes/câbles, comme VCV Rack sur grand écran), push des presets édités, samples à la demande.
   Le FM-1 « sample via l'APK » : l'APK pousse le sample, le FM-1 le charge dans un ring et le joue.

## 6. Contraintes et honnêteté

- **Flash FM-1** : zone data ~288 Ko (+ XDATA 16 KiB ×2, + 12 KiB en `xdata2=true`) — samples courts
  uniquement ; le reste reste sur le téléphone. Ne JAMAIS remplir la zone app (0xD4000..0xFC000).
- **Pas de streaming audio phone→FM-1** en v1 : l'USB du FM-1 est device (USB-audio IN = le FM-1
  s'enregistre chez l'hôte) ; le flux audio live phone→FM-1 demanderait un routage UAC-in dans le
  firmware — piste v3, pas promis.
- **ZuluLink** : spec confidentielle (KB privée), non décrite ici ; ZTF est un protocole minimal séparé,
  interopérable au niveau MIDI (CC/notes) avec n'importe quel hôte.
- Dépôts privés en dev ; ouverture publique plus tard en GPL-3.0 + crédits Animatek (PRISE-EN-MAIN.md).
- Android : boîte de dialogue de permission USB host à chaque branchement ; certains téléphones
  ont un port OTG limité — prévoir le fallback « export WAV sur le stockage du téléphone ».

## 7. Fichiers visés

- Firmware (patch 0009, branche `vibe/fm1-patch-link-c9ef18`) : `fm1.toml` (`companion = true`),
  `app.h` (APP_FILE_OBJS/APP_FILES/APP_PARAMS/APP_SYSEX/APP_COMPANION_EXTRAS), nouveau `ztek_link.c/h`
  (dispatch SysEx ZTF, fichiers ZSMP/ZLUP/ZPRE/ZMAC, commit→chargement), hooks `app.c` (page LINK côté
  FM-1 minimale : état du transfert), `patches/README.md`.
- APK (branche `vibe/ztf-link-c9ef18`) : `Fm1Usb.kt`, `SampleLibrary.kt`, `ZtekAudio.kt` (WAV
  encode/décode natif + banque), `MainActivity.kt` (page LINK), `LcdView.kt`, tests host.
