# PHILOSOPHIE ZULU

Le contrat de conception du projet. Toute nouvelle fonction, tout écran, tout réglage
doit se vérifier contre cette page. (Gravé le 15/11/2026, règle de Flying Zulu.)

## Trois espaces, jamais mélangés

**1. La zone de jeu (LIVE)**

- On conduit la voiture de sport, on ne règle pas le moteur en même temps.
- Un geste de jeu peut demander **2-3 secondes de réflexion**, pas plus.
- Jamais d'action complexe pendant le jeu : les actions complexes se font
  posé, délibérément ("ah là je fais un truc, tac, je vais rentrer là, je règle").
- Et comme les boucles tournent derrière, on peut se permettre d'aller chercher
  un réglage le temps d'une boucle — mais pas plus.

**2. La couche profonde (MOTEUR)**

- On peut entrer dans le moteur quand on veut : rerouter, reassigner, personnaliser finement.
- Rien n'est fermé : si l'utilisateur veut creuser, il peut.
- Mais on n'y entre que volontairement : jamais la couche profonde ne s'impose
  pendant le jeu, jamais un écran de réglage ne bloque la scène.
- **Budget temporel : 2-3 minutes maximum** pour retrouver ce qu'on cherche
  et l'ajuster. Jamais 10 minutes. Si un réglage prend trop longtemps à atteindre,
  c'est le design qui est en cause, pas l'utilisateur.

**3. L'atelier (CRÉATION DE PATCHES)**

- Ici, au contraire, **on a le droit de prendre son temps** : construire un patch
  complexe, empiler des routages, chercher un son pendant un quart d'heure.
  C'est un espace protégé, sans budget temporel.
- Mais tout ce qui y est construit doit pouvoir **se ranger proprement** :
  - **Sauvegarde nommée** du patch (nom libre, date).
  - **Rubriques utilisateur poussées** : catégories personnelles, étiquettes
    couleurs, et une recherche/filtrage qui retrouve un patch en quelques secondes.
  - **Grosse poignée = retrouver vite** : le classement vaut d'abord par sa
    capacité de retrieval, pas par son esthétique.
- **La promotion, geste final de l'atelier** : quand un patch est abouti, on se dit
  "ça, c'est complet, je veux le fixer" → **il devient un nouvel effet utilisateur**.
  Il quitte l'atelier et vient s'installer **pouf, dans les slots FX, rangé, prêt
  à jouer**, au même titre qu'un effet d'usine : tap pour l'activer, EDIT pour
  l'ouvrir, assignable aux axes/LFO/ARP/snapshots comme les autres.
- Le chemin complet : **construire → régler → "c'est complet" → fixer → rangé
  dans les effets → rejouable en un tap au live.** L'atelier nourrit le jeu.

## Les règles qui en découlent

1. **Les modules communiquent au maximum entre eux** (ZuluLink) : l'ensemble est
   plus riche que la somme des appareils, mais chaque module garde sa zone de jeu propre.
2. **Dans une app, on peut rerouter n'importe quoi** : axes, effets, banques, modulateurs,
   destinations. La flexibilité est un droit, pas un péché.
3. **Toute personnalisation profonde a une porte claire** (EDIT, page de tirettes,
   MENU) et une sortie claire : on y va, on règle, on revient au jeu.
4. **Les défauts sont toujours jouables** : un utilisateur qui ne touche jamais à la
   couche profonde doit avoir un instrument complet et vivant.
5. **Le live est le juge de paix** : en cas de doute entre deux designs, celui qui
   respecte les budgets (2-3 s par geste de jeu, 2-3 min par plongée moteur) gagne.
6. **Rien de construit ne se perd** : tout état abouti peut être nommé, rangé,
   retrouvé, et promu au rang d'objet jouable (effet utilisateur, snapshot, preset,
   scène). L'instrument grandit avec son propriétaire.
