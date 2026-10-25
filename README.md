# Diamond Altar (mod Fabric 1.21.11)

Un **Autel d'Infusion** (blanc, 4 piliers à bobines dorées) qui transforme ton équipement
en diamant en armes légendaires, avec une animation : l'objet flotte, tourne de plus en plus
vite, des arcs électriques partent des piliers, puis un éclair le frappe.

## Craft de l'autel

```
[ vide ]              [ vide ]              [ vide ]
[ vide ]              [Étoile du Nether]    [ vide ]
[Bloc de netherite]   [Bloc de netherite]   [Bloc de netherite]
```

## Utilisation

1. Pose l'autel.
2. Clic droit avec un objet en diamant (casque, plastron, jambières, bottes, épée, pioche,
   hache, pelle, houe) ou une **masse** → l'objet est posé et l'infusion démarre (5 s).
3. Quand l'éclair tombe, clic droit pour récupérer ton objet **Infusé** (brillant, nom en cyan).
4. Avec un outil infusé en main : **clic droit** pour lancer sa capacité.

## Capacités

| Objet | Capacité | Effet |
|---|---|---|
| Épée | **Tranche de la Volonté** | Un coup balayé comme le coup normal, mais en croissant géant qui s'étend jusqu'à **10 blocs**. 16 dégâts. Recharge 3 s. |
| Pioche | **Foreuse de Diamant** | Creuse un tunnel 3x3 de 8 blocs dans la direction du regard. |
| Hache | **Cyclone d'Acier** | Tourbillon de lames pendant 2 s, aspire puis éjecte les ennemis. |
| Pelle | **Onde Sismique** | Onde de choc de 8 blocs qui projette les ennemis en l'air. |
| Houe | **Moisson des Âmes** | Faux spectrale autour de toi, vol de vie, fait pousser les cultures. |
| Masse | **Chute Météore** | Tu t'envoles puis t'écrases comme une météorite (impact enflammé, sans dégâts de chute). |
| Casque | Regard de Cristal | Vision nocturne + respiration aquatique. |
| Plastron | Cœur de Diamant | Résistance. |
| Jambières | Foulée Éclair | Vitesse II. |
| Bottes | Pas Céleste | Saut II + aucun dégât de chute. |
| Armure complète | Aura de Diamant | Régénération + Force + aura de lumière. |

## Compiler

Il faut Java 25 pour compiler (le mod tourne avec Java 21+) :

```
./gradlew build
```

Le `.jar` est dans `build/libs/`. Mets-le dans `.minecraft/mods` avec **Fabric Loader** et
**Fabric API** pour Minecraft 1.21.11.

Chaque push sur GitHub compile aussi le mod automatiquement (onglet **Actions** → dernier
build → artefact `diamond-altar-mod`).
