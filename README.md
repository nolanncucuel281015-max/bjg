# Diamond Altar (mod Fabric 1.21.11)

Un **Autel d'Infusion** (blanc, 4 piliers à bobines dorées) qui transforme ton équipement
en diamant en armes légendaires, avec une animation : l'objet flotte, tourne de plus en plus
vite, des arcs électriques partent des piliers, puis un éclair le frappe.

## Textures des objets infusés

Chaque objet infusé a sa propre texture animée (reflet de diamant qui passe + gemme violette qui pulse) :

![Textures](docs/textures.png)

## Craft de l'autel

```
[ vide ]              [ vide ]              [ vide ]
[ vide ]              [Étoile du Nether]    [ vide ]
[Bloc de netherite]   [Bloc de netherite]   [Bloc de netherite]
```

## Utilisation

1. Pose l'autel.
2. Clic droit avec un objet en diamant (casque, plastron, jambières, bottes, épée, pioche,
   hache, pelle, houe) ou une **masse**, ou une **lance en diamant** → l'objet est posé et l'infusion démarre (5 s).
3. Quand l'éclair tombe, clic droit pour récupérer ton objet **Infusé** (brillant, nom en cyan).
4. Avec un objet infusé en main : appuie sur **K** pour changer de capacité (touche modifiable
   dans Options → Commandes). L'épée se lance au **clic gauche**, les autres objets au **clic droit**.

## Capacités

| Objet | Capacités (touche K pour changer) |
|---|---|
| Épée (clic gauche) | **Tranche de la Volonté** (slash géant de 10 blocs, 30 dégâts, sans recharge) · **Lames Fantômes** (5 lames qui transpercent sur 24 blocs) · **Pas de l'Ombre** (téléportation de 10 blocs qui tranche) · **Jugement Céleste** (pilier de lumière du ciel, 40 dégâts) · **Faille du Néant** (trou noir qui aspire puis implose) |
| Pioche | **Foreuse de Diamant** (tunnel 3x3) · **Sphère de Cristal** (sphère de 5 blocs minée) · **Vision des Minerais** (les minerais brillent 10 s) · **Éruption de Cristaux** (pics de cristal sur 16 blocs) |
| Hache | **Cyclone d'Acier** · **Hache Boomerang** (part et revient) · **Bûcheron Titanesque** (abat l'arbre entier) · **Fissure** (fend le sol sur 14 blocs) |
| Pelle | **Onde Sismique** · **Sables Mouvants** (piège les ennemis 5 s) · **Excavation** (creuse 5x5x3) |
| Houe | **Moisson des Âmes** · **Lianes Étrangleuses** (immobilise les ennemis 4 s) · **Floraison** (soigne et fait pousser) |
| Masse | **Chute Météore** · **Puits de Gravité** (soulève puis écrase) · **Colère du Tonnerre** (foudre sur 10 monstres) |
| Casque | Vision nocturne + respiration aquatique + force des conduits |
| Plastron | Résistance II + résistance au feu |
| Jambières | Vitesse II + célérité II |
| Bottes | Saut II + grâce du dauphin + aucun dégât de chute |
| Armure complète | Régénération II + Force II + saturation + aura de lumière |
| Armure (plastron, **Shift + clic droit** pour lancer, **J** pour changer) | **Bouclier de Cristal** · **Ruée de Diamant** · **Nova Prismatique** · **Ailes de Diamant** · **Avatar de Diamant** · **Saut Astral** |
| Lance (clic gauche) | **Lance Comète** · **Charge Stellaire** · **Constellation** |
| Nouveautés lunaires | Épée : **Croissant Lunaire**, **Pluie d'Étoiles**, **Éclipse** · Pioche : **Rayon Stellaire** · Hache : **Orbite Lunaire** · Pelle : **Gravité Lunaire** · Houe : **Faux Céleste** · Masse : **Supernova** |

L'armure infusée est incassable et très solide : armure 6/12/9/6, robustesse +5 et +6 PV par pièce,
résistance au recul.

Quand tu portes l'armure infusée, elle a sa propre texture de diamant infusé (liserés dorés,
gemmes violettes) :

![Armure](docs/armor.png)

## Compiler

Il faut Java 25 pour compiler (le mod tourne avec Java 21+) :

```
./gradlew build
```

Le `.jar` est dans `build/libs/`. Mets-le dans `.minecraft/mods` avec **Fabric Loader** et
**Fabric API** pour Minecraft 1.21.11.

Chaque push sur GitHub compile aussi le mod automatiquement (onglet **Actions** → dernier
build → artefact `diamond-altar-mod`).
