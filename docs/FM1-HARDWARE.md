# FM-1 — matériel connu et procédure avant flash

## Info matérielle enregistrée (session du jour)

- L'utilisateur possède un **M-VAVE FM-1 révision octobre 2026**.
- **ATEK303 (firmware Animatek d'origine) tourne très bien dessus** → la base CPU/SDK
  (JieLi AC791N/WL82, flash 1 Mo, écran 240×240) est compatible avec notre cible.
  Cela ne prouve **pas** que la carte flash est identique en taille/mapping : ATEK303
  n'utilise pas forcément les zones hautes de la flash que nous réservons à ZTF.

## Ce que ça change concrètement

1. **Lire l'identité exacte avant tout flash** : via USB-MIDI / companion
   (`terceros/fm1-base/tools/fm1_companion.py`, commandes 0x60 boot / info) —
   noter révision firmware usine, taille de flash détectée, ID carte.
2. **Backup complet de la flash d'origine AVANT le moindre flash** — le FM-1 n'a
   **pas de bouton recovery** ; les outils de secours sont dans
   `terceros/rescate/` (unbrick), c'est du secours, pas une procédure normale.
3. **Vérifier nos offsets ZTF contre la flash réelle de cette révision** :
   fichiers companion `0xB0000..0xD1FFF` (ZSMP/ZLUP/ZMAC, max 4064 o/fichier),
   store presets `0xD4000..`, zone libre restante `0xD2000..0xD3FFF`.
   Si la rev 2026-10 a une carte flash différente, ces zones peuvent être fausses
   → corruption. Ne jamais écrire là-dessus sans vérification.
4. **Vérifier la base ATEK303** : si la rev 2026-10 embarque un firmware usine plus
   récent que notre base `8934f53`, re-vérifier (voire rebaser) les patches
   `patches/0001..0010` avant `git apply`.

## Ordre des opérations (règle absolue)

1. **Émulateur d'abord** : build `.fwsc` → `fm1-emu` / `fm1-live` (voir
   `docs/FM1-EMULATEUR.md`) + host simulator (`X0X_FONTSET=terminus python3
   base/tools/build.py app --host`) → tests unitaires (`tools/test_ztf_codec.py`,
   tests host zlk).
2. **Ensuite** lecture identité + backup sur le vrai hardware.
3. **Enfin seulement** : flash (USB-MIDI SysEx, `docs/FM1-RESSOURCES.md`).

Jamais l'inverse. Un flash sur une mauvaise cible = brick assuré.
