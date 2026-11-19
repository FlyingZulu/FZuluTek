# Cle de signature Zulu

Cle fixe du projet (PKCS12). Elle signe TOUS les APK (debug et release),
pour que les mises a jour s'installent sans desinstaller l'ancienne version.

La cle de signature (zulu.p12, alias zulu) vit dans le depot prive
FZuluFx, dossier signing/ : les builds de ce projet signent avec elle.


NE JAMAIS supprimer ni regenerer ce fichier : les mises a jour
seraient refusees par Android. Si le depot devait un jour devenir
public, regenerer d'abord une cle neuve et ne publier que celle-la.
