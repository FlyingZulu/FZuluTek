# PRISE EN MAIN — ZuluTEK (lire avant de travailler)

Note de passation à lire en premier. Confidentialité : ZuluTEK reste privé jusqu'à
sortie officielle. À la publication : licence GPL-3.0 + crédits Animatek.

## État du dépôt (19/11/2026)

FZuluTek est un SQUELETTE SANS CODE SOURCE (build.gradle, docs Zulu génériques,
AndroidManifest). Aucune ligne de ZuluTEK n'existe encore. La base de savoir-faire
Zulu vit dans le dépôt privé **FZuluFx** (Kotlin + C++/JNI : ZuluLink.kt, ZuluControls.kt,
ZuluPad.kt, audio_engine.cpp, dsp/). Le périmètre du ZuluTEK v0 n'est défini nulle
part : LE DÉFINIR AVEC FLYING ZULU AVANT D'ÉCRIRE DU CODE.

## Sources ATEK303 (publiques, GPL-3.0)

- https://github.com/animatek/FM1-Animatek (firmwares + recherche plateforme, releases v1.1/v1.2)
- https://animatek.github.io/FM1-Animatek/ (site officiel, émulateur navigateur)
- https://fm1-editor.com/firmware/ (catalogue firmwares FM-1, révisions matérielles)
- Communauté : r/MVaveFM1. Vérifier la révision du FM-1 de Flying Zulu avant le bas niveau.

## GitHub — opérations impossibles via l'assistant (ne pas insister, ne pas proposer de réglage)

Erreur « Resource not accessible by integration » = limite de l'application Vibe
(les droits sont fixés par Mistral, PAS réglables dans GitHub). Le compte est déjà
en « All repositories ». Concernés :

- créer un dépôt, supprimer, changer la visibilité, gérer les collaborateurs :
  c'est Flying Zulu qui agit depuis son navigateur (il travaille uniquement depuis
  un smartphone Android).
- pousser des BINAIRES (APK, polices, images) : contenu corrompu via le connecteur
  (vérifié par SHA). Alternative : template repository (copie intégrale, commit unique).
- limites de débit en rafale : réessayer 3-4 fois, ne jamais masquer les erreurs.

## Règles du projet

1. ZuluLink (protocole réseau Zulu) est CONFIDENTIEL : sa spécification vit dans la
   base de connaissance privée de Flying Zulu (Vibe), pas ici. Ne pas la publier.
2. Clé de signature signing/zulu.p12 (dans FZuluFx) : NE JAMAIS supprimer ni régénérer
   (les mises à jour Android seraient refusées). Statu quo du mot de passe : assumé
   (sideload, pas de Play Store). Si ce dépôt devient public un jour : générer une
   clé neuve d'abord, et ne publier que celle-là.
3. Crédits et paternité : Flying Zulu partout (.inscrit dans LICENSE/CREDITS).
4. Dérivé d'ATEK303 (GPL-3.0) : distribuer impose de publier le code sous GPL-3.0
   avec crédits Animatek. Développer en privé est en règle.
5. **Le mode d'emploi / la Bible est la source de vérité** (docs/ZULUTEK.md, docs/MODE_D_EMPLOI.md,
   docs/ZULUTEK-MIDI-LOOPER.md « bible » @ a07f753, pages Notion BIBLE). Une fois qu'un comportement
   est décrit là-bas et validé avec Flying Zulu, c'est exactement ça qu'on implémente — sans
   réinventer, sans « simplifier ». Toute question sur un protocole/process : on cite d'abord le doc
   (chemin + lignes), puis on dit si le code diverge. Si on doute ou si le code dérive trop du doc :
   on se réfère au mode d'emploi / à la Bible, ou on pose la question / on fait un point avec
   Flying Zulu. Un mode d'emploi et une machine qui ne correspondent pas, c'est un bug.

## Ordre suggéré pour démarrer

1. Définir le périmètre ZuluTEK v0 avec Flying Zulu (matériel, cible, jalon 1).
2. Étudier animatek/FM1-Animatek + le code de FZuluFx.
3. Mettre ZuluLink.kt (FZuluFx) à niveau de la spec v0.4.1 (repère immédiat).
4. Documenter chaque décision dans ce dépôt : docs/ (il manque un document de conception ZuluTEK).
