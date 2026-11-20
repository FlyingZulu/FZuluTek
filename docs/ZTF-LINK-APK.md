# ZTF v1 — côté APK (Android) de la liaison APK ↔ FM-1

SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
Doc d'implémentation de la **v1 « ZTF »** côté APK. La conception (modes A/B, pont audio
hybride) est dans `docs/FM1-APK-LINK.md` (PR #10) ; le firmware (patch **0010**, PR #12)
impose le protocole : `patches/0010-zulutek-link.patch` (`ztek_link.c/h`). La spec ZuluLink
(réseau Zulu-to-Zulu) reste CONFIDENTIELLE et n'est pas décrite ici — ZTF est un protocole
séparé, minimal.

## 1. Transport

Un seul câble USB : **USB-MIDI** pour le contrôle/les données (SysEx), **UAC audio** pour le
pont sonore dans les deux sens (le jack du FM-1 reste la sortie d'écoute analogique).

- Détection : interface USB **classe 0x01, sous-classe 0x03** (MIDISTREAMING), endpoints
  bulk IN/OUT — pas de VID/PID. Permission USB host demandée à chaque branchement
  (`Fm1Usb.scan()` + broadcast `com.zulutek.preview.USB_PERMISSION`).
- Packetisation USB-MIDI : paquets de 4 o (CIN<<4 | câble 0) ; un SysEx est découpé en
  blocs de 3 o (CIN 0x4 = continue, 0x5/0x6/0x7 = fin à 1/2/3 o) et réassemblé en réception.
- Deux framings SysEx :
  - **companion fm1-base** (pack7) : `F0 pack7(00 59 cmd len24 body… sum8(body)) F7`,
    cmd `0x60` capability / `0x61` fichiers / `0x62` paramètres ; ≤ 640 o entre F0/F7,
    **une requête à la fois**, 512 o de data max par écriture fichier, CRC-32 vérifié au
    commit. Référence host : `fm1-base/tools/fm1_companion.py`.
  - **commandes ZTF propres** (brut, PAS de pack7) : `F0 7D 'Z' 'T' cmd … F7`
    (`zlk_sysex`) : `P` PING→PONG("zuluTek",ver,libre) · `R`/`W` param get/set (idx, u32 LE)
    · `G`/`S` page get/set · `T`/`O` note on/off · `Y` play · `L` LOAD fichier idx (applique).

## 2. Fichiers exposés par le FM-1 (0x61, zone data flash, copies A/B, 0x1000 par fichier)

| nom | index | format | contenu |
|---|---|---|---|
| `ZSMP00..07` | 0..7 | `ZSM1` | sample slot : `'ZSM1'` sr u32, len u32, fmt u8 (0=int16, 1=µ-law G.711 comme `ml_ulaw_enc`), data. Au commit → `zml_ring_import` (ring resample du looper, slot = index) |
| `ZLUP00..07` | 8..15 | `ZLS1` | blob slot looper : `'ZLS1'` pitch i8, pos u16, mode/mut/src/fx/vol u8, len u16, len_set u8, on u8, n_note u16, notes×5 {t u16, note u8, vel u8, dur u8}, n_par u16, pars×8 {t u16, key u8, pad u8, v f32}. Au commit → `zml_slot_load` |
| `ZMAC` | 16 | `ZMC1` | banque macros : `'ZMC1'` + 16 × {dst i16, val f32, used u8, pad×3} (10 o/slot). Au commit → `zmac_load` |

Presets : blob `ZTP1` existant (`ztek_preset_export`) — poussé/tiré tel quel (interchangeable
APK/FM-1 via les objets store 0x61, hors fichiers ZTF propres).

**Limite flash** : fichier ZTF max `0x1000 − 32 (PLAT_FILE_HEAD)` = **4064 o** → payload ZSM1
≤ 4051 o, soit ~2025 éch. int16 (~46 ms) ou ~4051 éch. µ-law (~92 ms) à 44,1 kHz. La
bibliothèque complète reste sur le téléphone ; on ne pousse que des extraits courts.

## 3. Fichiers APK (branche `vibe/ztf-link-apk-c9ef18`)

- `ZtfCodec.kt` — codec **pur** (aucun import Android) : pack7/unpack7, frame/parse companion,
  trames ZTF, µ-law (identique à `ml_ulaw_enc/dec`), encode/decode ZSM1/ZLS1/ZMC1, CRC-32.
  Miroir testé 1:1 en Python : `tools/test_ztf_codec.py`.
- `ZtfTransport.kt` — client au-dessus d'un `ZtfLink` brut : hello (0x60), fichiers
  (list/read/sum/begin/data/commit/delete avec reprise + CRC), params (0x62 get/set/watch,
  notifications CHANGED mises de côté), commandes ZTF (ping/page/notes/play/load/param).
  Une requête à la fois (mutex), 3 essais, timeout 1 s.
- `Fm1Usb.kt` — USB host Android : scan, permission, claim interface MIDI, `UsbMidiLink`
  (packetisation USB-MIDI + thread lecteur), façade `Fm1Usb` sur un worker single-thread
  (`ping/refreshFiles/pull/push/pushNamed/pullNamed/load/delete/hello`) avec callbacks
  `onStatus/onFiles/onProgress`.
- `SampleLibrary.kt` — bibliothèque téléphone : import WAV via SAF (`ACTION_OPEN_DOCUMENT
  audio/*`, ex. export Koala), stockage `filesDir/ztek/samples/`, décodage WAV
  (PCM 8/16/24/32 + float32), resample mono 44,1 kHz, encodage ZSM1 tronqué au max FM-1 ;
  les pulls vont dans `filesDir/ztek/pull/` (+ WAV réexporté quand c'est un ZSM1).
- `Fm1UsbAudio.kt` — pont audio hybride : `AudioRecord`/`AudioTrack` avec
  `setPreferredDevice(device UAC)` (l'API Android n'expose pas l'isochrone en host brut) :
  capture FM-1→téléphone (`usb_audio_in`/`uac_tap` côté device) et lecture
  téléphone→FM-1 (`usb_audio_out`/`uac_play` + hook `app_usb_in` → ring du looper =
  « le FM-1 sample via l'APK » en live). Tout est en try/catch : sans UAC routé, no-op propre.
- `MainActivity.kt` / `LcdView.kt` — **page LINK (17)** : long-HOME (ou cycle DLY long
  …→16→17). Onglets PRE = **FM-1** (liste des fichiers, 0x61) / **LOCAL** (samples du
  téléphone). SEL = ligne, ALG = slot ZSMP cible. Boutons : **PLAY** = push (LOCAL→
  `ZSMPxx`) / pull (FM-1→`filesDir/ztek/pull`), **SAVE** = scan+ping+ls, **ARP** = LOAD
  (applique le fichier sur le FM-1), **FX** = rm, **SEQ** = importer un WAV (SAF),
  **ENV** = pont audio on/off, **REC** = retour. `AndroidManifest.xml` : feature
  `android.hardware.usb.host` (non requis).

## 4. Tests

```
python3 tools/test_ztf_codec.py            # tests unitaires du codec (miroir Kotlin)
python3 tools/test_ztf_codec.py --with-c   # + cross-check contre le VRAI firmware patch 0010
                                           #   (harnais C compilé depuis l'arbre 0001-0010 :
                                           #    zml_slot_load/save, zmac_load/save, zml_ring_import,
                                           #    zlk_sysex PING, cp_pack7 de companion.c)
```
`--with-c` vérifie que les vecteurs Python sont **octet-identiques** après load/save par le
firmware (ZLS1, ZMC1), que le ring µ-law vit après import ZSM1, que la trame PONG est
conforme et que pack7 == `cp_pack7` de `fm1-base/device/companion.c`.

## 4b. v2 — mode A « deux machines » (branche `vibe/ztf-link-apk-c9ef18`)

La page LINK 17 gagne la **télécommande live** et le **miroir d'état** :

- **Miroir live** : à la connexion, `paramWatch(true)` (0x62) + lecture des 12 params ; un thread
  dédié (`Fm1Usb.startWatch` → `ZtfTransport.poll`) collecte les notifications `PQ_CHANGED`
  envoyées spontanément par le FM-1 → onglet **PARAMS** (PRE = onglet 0 FM-1 / 1 LOCAL / 2 PARAMS).
- **Télécommande ZTF** depuis la page LINK : K1 = `PAGE` (0..13), K2 = `TEMPO` (30..300),
  K3 = `CUTOFF`, K4 = `LOOP GAIN` (anti-rebond 1,2 s), **OCT+ = PLAY** (`Y`) ;
  **clavier = notes sur le FM-1** (blanches = 48+slot, noires = note MIDI ; `T`/`O`).

## 5. Limites v1/v2 connues

- Samples poussés très courts (~92 ms max en µ-law) — le reste vit sur le téléphone.
- Pont audio dépend du routage UAC du téléphone (certains ports OTG sont limités) ; le
  fallback est l'export WAV sur le stockage du téléphone.
- Mode A « deux machines » (miroir live des params via 0x62 watch, télécommande page/
  transport/notes) : le transport est prêt (`ZtfTransport`), le miroir UI reste à brancher.
- v3 « vue étendue » (éditeur de presets / baie de patch sur l'écran du téléphone) : plus tard.
- ⚠️ ni le patch 0010 ni les patches jumeaux `patches/fm1-base/0010-app-usb-in.patch` /
  `patches/felucca-bsp/0010-uac-in-tap.patch` n'ont encore été **buildés avec la toolchain
  JieLi** ni testés dans fm1-emu — à faire **avant tout flash** (`docs/FM1-EMULATEUR.md`).
