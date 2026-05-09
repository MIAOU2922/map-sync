# Configuration DistantSync

Le mod DistantSync peut être configuré via le fichier `config/distantsync.json` qui est créé automatiquement au premier lancement.

## Fichier de configuration

Emplacement : `config/distantsync.json`

```json
{
  "scanRadius": 256,
  "chunksPerTick": 50,
  "checkIntervalMs": 500
}
```

## Paramètres

### `scanRadius`
- **Type** : Entier
- **Valeur par défaut** : `256`
- **Description** : Distance maximale (en chunks) autour du joueur où DistantSync va chercher et rendre les chunks LOD de Distant Horizons sur la minimap.
- **Recommandation** : 
  - Valeurs plus élevées = plus de chunks sur la minimap mais plus de calculs
  - Valeurs plus basses = meilleures performances mais moins de couverture
  - Pour des performances optimales : 128-256
  - Pour une couverture maximale : 512+

### `chunksPerTick`
- **Type** : Entier
- **Valeur par défaut** : `50`
- **Description** : Nombre de chunks traités par tick de jeu. Contrôle la vitesse à laquelle les chunks LOD sont rendus sur la minimap.
- **Recommandation** :
  - Valeurs plus élevées = rendu plus rapide mais peut causer des ralentissements
  - Valeurs plus basses = meilleures performances mais rendu plus lent
  - Pour des performances optimales : 25-50
  - Pour un rendu rapide : 100+

### `checkIntervalMs`
- **Type** : Entier
- **Valeur par défaut** : `500`
- **Description** : Intervalle en millisecondes entre chaque vérification de nouveaux chunks LOD à rendre.
- **Recommandation** :
  - Valeurs plus élevées = moins de vérifications, meilleures performances
  - Valeurs plus basses = mises à jour plus fréquentes
  - Pour des performances optimales : 500-1000
  - Pour des mises à jour rapides : 250-500

## Rechargement de la configuration

La configuration est chargée au démarrage du mod. Pour appliquer des modifications :
1. Modifiez le fichier `config/distantsync.json`
2. Redémarrez Minecraft

## Exemples de configurations

### Configuration haute performance (recommandée)
```json
{
  "scanRadius": 128,
  "chunksPerTick": 30,
  "checkIntervalMs": 1000
}
```

### Configuration équilibrée (par défaut)
```json
{
  "scanRadius": 256,
  "chunksPerTick": 50,
  "checkIntervalMs": 500
}
```

### Configuration couverture maximale
```json
{
  "scanRadius": 512,
  "chunksPerTick": 100,
  "checkIntervalMs": 250
}
```

## Notes

- Si le fichier de configuration est corrompu ou manquant, les valeurs par défaut seront utilisées
- Le mod crée automatiquement le fichier de configuration avec les valeurs par défaut au premier lancement
- Les modifications prennent effet au prochain redémarrage du jeu
