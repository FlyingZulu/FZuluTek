# FM-1 — carte de la flash 1 MiB : zones interdites en écriture

Sources vérifiées (croisées) :
- `terceros/fm1-base/bsp/docs/FLASH.md` + `include/plat.h` + `bsp/loader/loader.c`
  (fm1-base = la plateforme d'ATEK303/Felucca : **notre** contrat, pas celui du stock M-VAVE) ;
- `AL-255/FM-1-RE` `docs/architecture.md` (dump V13) ; `aroum/fm1-custom-fw`
  `REVERSE_ENGINEERING.md` (hub communautaire) ; `fm1-emu` `crates/fm1-soc/src/spiflash.rs`
  + `docs/stock-firmware.md` ; `Quixotic7/MvaveFM1Unbricker` ; `joakimeriksson/scintilla-fm1` PR #27.

## ⚠️ NE JAMAIS ÉCRIRE / EFFACER

| Zone | Contenu | Pourquoi |
|---|---|---|
| `0x00000..0x03FFF` | **Boot head** : header flash (VID/PID), SPL `uboot.boot` à `0xA0`, `isd_config.ini` à `0x38D0` (clé chip) | Bootloader + table de partition + boot vector. Écraser = brick dur |
| `0x04000..0x97000` | **Zone app** (XIP mappée à `0x02000000`) ; l'image app finit sous `0x93000` | Seul le **loader OTA** y écrit (avec ses records `0xE0000..0xFC000`). Jamais en raw depuis l'app lancée |
| `0xE0000..0xE5000` | **OTA staging** (loader) | Records de mise à jour (`AT` à 0xF06, CRC à 0xF00) |
| `0xE7000..0xE9FFF` | `FL_NEVER` : secteur UP_FM6 retiré `0xE7000`, **VM SDK à `0xE8000`** (≤ 4 KiB), **BTIF `0xE9000`** (BT/BLE MAC, record 102) | MAC BT + config VM du SDK — les perdre = BT mort / réglages perdus |
| `0xEA000..0xFBFFF` | Région **USR** (patches utilisateur stock ; `EXT_RESERVED/USER` à `0xED000`, 0x12000 ; hazard SLOOP USR4 `0xE7000..0xFAFFF`) | Le loader peut y écrire des records (`0xE0000..0xFC000`) ; éviter |
| `0xFC000..0xFEFFF` | **Globals** (paire settings Felucca `0xFC000/0xFD000`, PLAT_XDATA2) — ATEK303 y met OBJ_SETTINGS | Store de l'app |
| `0xFF000` | **`key_mac`** — secteur jamais écrit | Identité usine |

## ✅ Zones écrivables par l'app (plat.h)

| Fenêtre | Offsets | Usage |
|---|---|---|
| PLAT_XDATA0 | `0x93000..0x97000` (16 KiB) | données app au-dessus de la zone app |
| **PLAT_DATA** | **`0x97000..0xE0000`** | **zone data principale** : fichiers app + objets store |
| PLAT_XDATA1 | `0xE5000..0xFC000` | autosave `0xE5000..0xE7000` + extra data |
| PLAT_XDATA2 | `0xFC000..0xFEFFF` | opt-in (fm1.toml `xdata2`) |

Règles dans les fenêtres XDATA : garder les octets `0xF00..0xF07` de chaque secteur 4 KiB effacés
(le loader y cherche des records de mise à jour). Dans PLAT_DATA, le loader n'écrit que les
secteurs d'une région `res.bin` demandée par le package (record `RES1`) — notre package n'en a pas.

## Objets store d'ATEK303 (APP_STORE_MAP, 2 secteurs 4 KiB A/B chacun)

`0xFC000` SETTINGS · `0xDC000` PANEL · `0xD8000` PRESETS_A · `0xDA000` PRESETS_B ·
`0xD4000` PRESETS_C · `0xD6000` PRESETS_D · `0xDE000` OPTIONS · `0xDF000` ZSEQ
→ les secteurs store commencent à **`0xD4000`** (jusqu'à ~`0xE1000`) et `0xFC000`.

## Placement ZTF (patch 0010) — validé contre cette carte

- Fichiers companion en copies A/B dans la zone data : **samples `0xB0000..`, loops
  `0xC0000..`, ZMAC `0xD0000/0xD1000`** → tout tient dans `0xB0000..0xD3FFF`, **sous** les
  objets store (`0xD4000+`) et au-dessus de la fin d'une éventuelle image app V16 (`0xA8FFF`).
  Magics `ZSM1`/`ZLS1`/`ZMC1` : ne collisionnent ni avec les records store `X0X0` ni avec
  les records de mise à jour `AT` (0xF06) du loader.
- Espace libre restant : `0x97000..0xAFFFF` (~60 KiB) + `0xD2000..0xD3FFF`.
- **Patch 0013 (ZPRE/ZKIT) consomme 0 octet de flash** : fichiers companion *virtuels* —
  `ZPRE00..31` (blob `ZTP1`, 1284 o) adossés aux objets store PRESETS_A..D, `ZKIT00..02`
  (blob `ZKT1`, 400 o) adossés aux kits usine du drum engine ; écriture accumulée en RAM,
  appliquée au commit via `plat_store_save`.
- ⚠️ Ne JAMAIS déplacer les fichiers ZTF vers `0xE0000+` (OTA staging / records / VM / BTIF / USR / globals / key_mac).

## Révision matérielle octobre 2026 (info clé)

- Les FM-1 vendus **depuis l'automne 2026** embarquent une flash **UC25HQ80 (JEDEC `B3 60 14`)**
  au lieu de la Puya P25Q80 (`85 60 14`) des premiers. **Les deux font 1 MiB, mêmes commandes
  SPI NOR, même carte mémoire** — mais l'outil/flash driver doit accepter le JEDEC id
  (`fm1-base device/fm1_flash_ok.h` accepte les deux ; bug connu corrigé dans scintilla-fm1
  PR #27 : les firmwares ne connaissant que la Puya prennent les nouveaux pour « inconnus »
  et refusent l'install / ne sauvegardent qu'en RAM).
- **V16 officielle (2026-10-21)** : son image app va jusqu'à `0xA8FFF` (au-delà des
  `0x4000..0x92FFF` des V13-V15) — une restauration complète réécrit de toute façon toute
  la fenêtre app.

## Vecteurs de récupération (FM-1 briqué)

1. **Soft-brick → aucun dongle nécessaire** : si le firmware ne boot pas, la mask-ROM entre
   toute seule en mode **`WL82 UBOOT1.00`** (USB mass-storage, **VID `0x4C4A` PID `0x8057`**)
   → `jl-uboot-tool` / `MvaveFM1Unbricker` peuvent **dumper/effacer/écrire les 1 MiB** par
   simple câble USB. Astuces pour forcer : attendre 2 min (watchdog), ou 3 redémarrages
   rapides, ou maintenir OCT-/OCT+ 10 s au boot.
2. **Hard-brick (le chip bloque sans entrer en UBOOT)** : dongle **Seeed XIAO RP2040 +
   FM-1 Transporter** — il pulse la boot key matérielle `0x16EF` sur USB D+/D- à la mise
   sous tension (c'est le « Seeed » attendu).
3. Règle absolue : **dump complet des 1 MiB avant toute écriture**, et ne jamais toucher
   `0x00000..0x03FFF` ni `0xFF000` (même via jl-uboot-tool).
4. Tout firmware custom doit **garder une ré-entrée bootloader** (commandes SysEx
   d'update / combo de touches au boot) sinon le FM-1 est soft-locké contre toute future
   mise à jour MIDI — ATEK303/companion l'implémente déjà (c'est le canal de flash).
