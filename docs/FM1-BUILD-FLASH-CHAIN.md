# Chaîne build → émulation → backup → flash (vérifiée en sandbox le 2026-11-21)

Tout ce qui suit a été exécuté et vérifié dans le sandbox, sauf la dernière étape (flash) qui
besoin d'un FM-1 physique. Les commandes sont celles de `terceros/fm1-base` (build.py) et de
`terceros/fm1-emu`.

## 1. Toolchain JieLi (pin vérifié)

`tools/toolchain.lock` épingle `jieli 20260730.1` (sha256 `a1545cdd…647bd`) depuis
`jl-update.oss-cn-shenzhen.aliyuncs.com` — le même archive est servi par `pkgman.jieliapp.com`.
Extraction à `~/.jieli/toolchain` (le lien `toolchain -> jieli-linux-toolchains-20260730.1`),
puis `~/.jieli/toolchain/pi32v2/bin/clang --version` → `clang version 4.0.1`, OK.

> Hors conteneur docker (qui monte la toolchain sur `/opt/jieli`), le driver clang ne trouve
> pas les headers newlib : il faut exporter
> `C_INCLUDE_PATH=$HOME/.jieli/jieli-linux-toolchains-20260730.1/pi32v2/include` et
> `LIBRARY_PATH=$HOME/.jieli/jieli-linux-toolchains-20260730.1/pi32v2/lib`.
> (JieLi recommande d'installer la toolchain sous `/opt/jieli` — voir le README de l'archive.)

## 2. SDK AC79 (3 fichiers pinés, retrouvés et vérifiés)

`tools/build.py` (`SDK_SHA256`) exige, au tag `AC79NN_SDK_V1.2.1_2023-12-13` (commit
`d179b4484759423312073f5fbb232501aa491047`, pin `toolchain.lock`) :

| fichier | sha256 |
|---|---|
| `cpu/wl82/tools/uboot.boot` | `4e3b4c22…976ded4` |
| `cpu/wl82/tools/cfg_tool.bin` | `27657995…010d1b` |
| `cpu/wl82/tools/cfg/eq_cfg_hw.bin` | `41167491…80ea7d` |

Gitee (`gitee.com/Jieli-Tech/fw-AC79_AIoT_SDK`) est injoignable depuis le sandbox ; le miroir
GitHub **`amitv87/fw-AC79_AIoT_SDK`** porte exactement ce tag (commit peel
`d179b448…1047`). Sparse checkout (blob:none) des 3 chemins → les 3 sha256 correspondent aux
pins. Poser `AC79_SDK` sur ce checkout.

## 3. Build du package `.fwsc`

```sh
cd atek303
JIELI_TOOLCHAIN=$HOME/.jieli/toolchain AC79_SDK=/chemin/fw-AC79_AIoT_SDK \
  sh build.sh            # -> app/build/atek303.fwsc
```

Vérifié sur l'upstream `8934f53` (sans la série de patches) : `atek303.fwsc` **616 059 o**,
identité `FM-1_33000`, build `A0F991D7`, RAM `.data+.bss` 41 388/98 304 o, pool
329 676/344 064 o — la map mémoire par défaut suffit.

## 4. Émulation (fm1-emu)

```sh
cargo build --release -p fm1-emu          # dans terceros/fm1-emu (ne pas builder tout le
                                           # workspace : fm1-live tire cpal/alsa)
./target/release/fm1-emu --fwsc ../atek303/app/build/atek303.fwsc \
    --ms 5000 --keys "0:PLAY,300:n7,600:n9,900:n12,1200:n14,1500:n7,2000:midi:903e64,2300:midi:803e64" \
    --wav /tmp/fm1.wav
```

Résultat vérifié : rc=0, 5000 ms émulés, 49 257 interruptions, 840 demi-buffers audio
(215 040 échantillons), LCD 52 frames, WAV 44,1 kHz stéréo **non muet** (max 23 554,
RMS 2 551, pas d'écrêtage). Noms de touches : boutons (`PLAY`, `REC`, …), `k0..k40`,
`notes n0..n26` / `C4`…, `T:midi:HEX`, `T:enc0:+3`, `T:pot:512`.

## 5. Backup / restore (FM-1 branché en USB)

Le firmware ATEK303 a `backup = true`, magic **AT64** (`APP_BACKUP_MAGIC`) :

```sh
python3 terceros/fm1-base/tools/fm1_backup.py backup  fm1-backup-AT64.bin --magic AT64   # avant tout flash
python3 terceros/fm1-base/tools/fm1_backup.py info    fm1-backup-AT64.bin
python3 terceros/fm1-base/tools/fm1_backup.py restore fm1-backup-AT64.bin --yes            # restauration
```

Le backup couvre `APP_BACKUP_OBJS` (settings + presets A..D), jamais l'étalonnage du panel.
**Toujours dumper la flash complète en plus** (programmateur JTAG/SWD, voir
`docs/FM1-HARDWARE.md`) : c'est le seul secours si la machine ne répond plus.

## 6. Flash du .fwsc (FM-1 branché en USB, mode normal)

```sh
python3 terceros/fm1-base/tools/fm1_install.py --info                 # identité courante
python3 terceros/fm1-base/tools/fm1_install.py app/build/atek303.fwsc --yes
```

OTA en 2 étapes (le firmware tournant démarre le loader, le loader écrit la flash, reprise
sur coupure) ; la FM-1 demande confirmation à l'écran. `--classic` force l'ancien protocole.

## 7. Bloquant actuel : le fit SRAM de la série de patches

Avec les patches 0001..0018 appliqués, le build firmware **compile et link sans erreur**
(0 symbole manquant — patch 0018) mais **ne tient pas en SRAM** :

- map par défaut : `RAM` dépassé de **100 276 o**, `POOL` de **10 044 o** ;
- `ram_layout = "packed"` + `cache_ram_ways = 7` (budget max ~503 Ko) : `SRAM` dépassé de
  **82 800 o**.

Gros postes (mesurés) : `.bss` 223 Ko dont `ztek_drum.o` 113 Ko (`static drum_t d`),
`unity.o` 50 Ko, `loader.o` 31 Ko, `ztek_loop.o` 28 Ko (`static ml_t ml`) ; `.pool` 346 Ko
dont `dsp_fx.o` 280 Ko, `unity.o` 74 Ko. Total ~594 Ko.

### Chiffrage par levier (mesuré 2026-11-21, structs compilés en host)

| Levier | RAM gagnée | CPU (ISR audio) | Notes |
|---|---|---|---|
| `DR_NPADS` 8→7 | **~9,5 Ko** (1× `dr_voice_t` 96 o + `dr_pad_t` 116 o + `bass_t` 9 304 o) | nul en pratique (les voix muettes sont sautées, `v->t<0 → continue`) | insuffisant seul |
| `bass_t.ks[2304]` → `ks[1024]` en ECO (Karplus-Strong PLUK, `bass_engines.h:25`) | **~41 Ko** (5,2 Ko/pad × 8) | légère baisse (ring plus court) | meilleur levier drum |
| Mode ECO poly 8→5 (déjà dans patch 0009, vol de voix `drum_engine.c:262`) | 0 o | **borne le pire cas à 5 voix** | levier CPU, pas RAM |
| `DR_FM1_ECO` ring delay 16384→8192 (déjà actif via `ztek_drum_fm1.c`) | ~32 Ko | ring 2× plus court | déjà en place |
| `ML_RS_MAX` 4096→2048 (looper) | ~4 Ko | — | `ztek_loop.c` |
| `ram_layout="packed"` + `cache_ram_ways=7` | +28 Ko de budget | — | map, pas une coupe |
| `.pool` `dsp_fx.o` 280 Ko (lignes FX) | à sizabler | — | plus gros poste |

Ordre de grandeur : drum_t non-ECO = 144 148 o (`fx` 67 732 o dont `dly[16384]` 64 Ko ;
avec ECO `dly[8192]` → ~34 Ko, d'où les 113 Ko mesurés en `.bss`). Total patché ≈ 594 Ko
(.bss 223 Ko + .pool 346 Ko + ram_text/data) pour ~503 Ko dispo → **régime ≥ 83 Ko**.

Pistes pour le régime (≥ 83 Ko, décision équipe A) : généraliser le mode ECO
(`DR_FM1_ECO`-style) à tous les moteurs, passer les gros buffers en `.pool`
(`PLAT_POOL`) ou `.cache_ram`, réduire `ML_RS_MAX` / `DR_NPADS` en mode ECO.

## 8. Ordre recommandé avant le premier flash réel

1. `git apply` 0001..0018 sur clone neuf de `8934f53` → build host OK → tests host sim
   (7/9 scénarios OK ; `options`/`presets` = régressions connues, voir PR #33).
2. Build firmware `.fwsc` (quand le fit SRAM sera réglé) → `fm1-emu` avec script de touches →
   WAV non muet, pas de NaN.
3. Sur le FM-1 : `--info`, backup AT64 + dump flash complet, puis `fm1_install.py`,
   puis re-vérifier l'identité et rejouer les scénarios clés.
