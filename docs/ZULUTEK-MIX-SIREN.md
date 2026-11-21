# Décision — mixeur 4 tranches unifié APK / FM-1, avec tranche SIREN

(Décision Flying Zulu, session 2026-11-21 ~06:20Z. Contexte : discussion ztek_mix — pourquoi
3 tranches sur le FM-1 vs 4 sur l'APK. Réponse : la voix est mono des deux côtés ; les 4 tranches
APK sont BASSE/LEAD/DRUMS/LOOP où BASSE et LEAD partagent le même bus voix physique.)

## Règle retenue

**Même table de mixage des deux côtés : 4 tranches + master**

| # | Tranche | Source audio |
|---|---------|--------------|
| 0 | **VOICE** | bus voix unique (mono) — BASSE et LEAD **réunis** (engine 0 = lead ACID 303, engines 1..4 = basses ; un seul rend à la fois, `AT_ENGINE`) |
| 1 | **DRUMS** | bus stéréo batterie (8 pads) |
| 2 | **LOOP** | bus stéréo looper (events + ring resample) |
| 3 | **SIREN** | sirène (osc. à phase, 4 waves) — **nouvelle tranche**, niveau = param SIREN MIX existant |

La sirène est traitée comme un module du rack à part entière : tranche de mixeur dédiée +
(APK) source de la baie `ZTEK_PSRC_SIREN` (déjà existant, routable vers FXIN/AUX1/AUX2…).

## Côté FM-1 (équipe A) — patch 0011 à écrire directement en 4 tranches

- `enum { ZLK_STRIP_VOICE = 0, ZLK_STRIP_DRUMS, ZLK_STRIP_LOOP, ZLK_STRIP_SIREN, ZLK_NSTRIP = 4 }`.
- Tranche SIREN : **K1 = vol → `zsir` `par[3]`** (c'est le param ZTF `P_SIREN_MIX`, groupe SIREN n°4,
  déjà exposé et déjà mirroré côté APK). **Aucun nouveau param ZTF** → aucune rupture du lien.
- La sirène reste **pre-FX sur le bus mono** (`zsir_render(mono, n)` inchangé) : pas de bus stéréo
  dédié — le firmware dépasse déjà la SRAM de +82 800 o (voir `docs/FM1-BUILD-FLASH-CHAIN.md` §7).
  Le routage audio post-FX de la sirène reste une option plus tard (coûtera 2×256 floats en `.pool`).
- DRUMS / LOOP gardent leurs bus stéréo post-FX actuels (`zdr_bus_*`, `zml_bus_*`).
- Persistance : niveaux de tranches dans les settings (comme `zml_gain`), pas dans les presets
  (un preset ne doit pas embarquer le mix global).
- UI page MIX : SEL = tranche/master, K1 = vol, K2 = mute (SIREN : mute = gate off), comme le design 0011 prévu.
- Ordonnancement : à écrire **après le régime SRAM** (bloquant actuel), pas avant.

## Côté APK (équipe B) — PR dédié de migration des strips

- Renuméroter : `enum { ZTEK_STRIP_VOICE = 0, ZTEK_STRIP_DRUMS, ZTEK_STRIP_LOOP, ZTEK_STRIP_SIREN, ZTEK_NSTRIP = 4 }`
  (aujourd'hui `BASSE=0, LEAD=1, DRUMS=2, LOOP=3`).
- `ZTEK_NSTRIP` reste 4 → `ZTEK_MIX_NSTRIP_PORTS` reste 52 : la géométrie de la baie ne change pas,
  seules les identités de tranches 2/3 changent (DRUMS 2→1, LOOP 3→2, SIREN = ex-LEAD 1→3).
- **Migration des données** : bump du magic des presets/kits/undo + remap `strip_par` :
  VOICE := strip LEAD (vol/mut/filtre du lead), DRUMS := ex-2, LOOP := ex-3, SIREN := nouveau
  (vol par défaut = `siren_lvl` courant). Même pattern que les bumps `OPT_MAGIC` des patches 0012/0015.
- Tranche SIREN APK : vol ↔ `ztek_siren_level` (`siren_lvl`), mute ↔ siren off ; `siren_mix`
  (pre/post-FX) reste un param de la page SIREN **et** la baie (`ZTEK_PSRC_SIREN` inchangé).
- ZTF : **ne jamais renuméroter les index de params** (le lien dispatch par index) — le groupe SIREN
  (WAVE/RATE/DEPTH/MIX) garde ses IDs des deux côtés.

## Parité lien ZTF (rappel)

Groupes de params identiques des deux côtés : VOICE(0), SEQ(1), LOOP(2), DRUM(3), SIREN(4).
La tranche SIREN du mixeur n'ajoute aucun param : c'est un habillage UI du param SIREN MIX existant.

## Pourquoi pas 5 tranches (2 voix de synthé)

La voix est mono par design (TB-303) des deux côtés ; une 2e voix de synthé coûterait RAM + CPU
dans l'ISR audio (deadline 5,8 ms/bloc) + une page UI, pour un gain nul vs la densité déjà
obtenue par DRUMS (8 pads) + LOOP (overdub) + SIREN mixés avec la voix.
