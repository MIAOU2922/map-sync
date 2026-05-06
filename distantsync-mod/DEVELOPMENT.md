# DistantSync - Guide de Développement

## Architecture Actuelle

### Structure des Packages

```
gjum.minecraft.distantsync.mod/
├── DistantSyncMod.java          # Point d'entrée principal du mod
├── integration/
│   ├── DistantHorizonsAPI.java  # API pour Distant Horizons (réflexion)
│   └── MinimapAPI.java          # API pour les minimaps (réflexion)
└── [anciens fichiers à nettoyer]
```

### Fonctionnement

1. **Détection** : Au démarrage, le mod détecte Distant Horizons et les minimaps via réflexion
2. **Événements** : Le mod écoute les événements de chargement de chunks
3. **Fallback** : Sans DH, utilise les chunks Minecraft normaux pour mettre à jour les minimaps
4. **Mise à jour** : Toutes les secondes, vérifie s'il y a de nouveaux chunks LOD à synchroniser

## Ce qui est implémenté ✅

- [x] Détection automatique de Distant Horizons
- [x] Détection automatique des minimaps (VoxelMap, JourneyMap, Xaero's)
- [x] Architecture avec réflexion (pas de dépendances dures)
- [x] Gestion des événements de chunks
- [x] Système de suivi des chunks traités
- [x] Conversion de chunks Minecraft en format LOD (fallback)
- [x] Build fonctionnel

## Ce qui reste à faire 🚧

### 1. API Distant Horizons

**Fichier** : `DistantHorizonsAPI.java`

#### Objectifs
- Comprendre l'API réelle de Distant Horizons
- Extraire les données LOD (heightmap, colors, biomes)
- Écouter les événements de mise à jour LOD

#### Ressources
- GitLab DH : https://gitlab.com/distant-horizons-team/DistantHorizons
- Télécharger le mod DH et décompiler pour étudier l'API
- Chercher dans leur code source : `com.seibel.distanthorizons.api.*`

#### Méthodes à implémenter

```java
// Dans DistantHorizonsAPI.java

/**
 * Check if LOD data exists for a chunk
 */
public boolean hasLodData(ChunkPos pos, Level level) {
    // TODO: Utiliser l'API DH pour vérifier
    // Exemple hypothétique:
    // return DhApi.Delayed.worldProxy.getSinglePlayerLevel()
    //     .getLevelWrapper(level)
    //     .getLodData(pos.x, pos.z) != null;
}

/**
 * Extract LOD data from DH
 */
public LodChunkData extractLodData(ChunkPos pos, Level level) {
    // TODO: Récupérer les données LOD réelles
    // 1. Obtenir le LodDataView de DH
    // 2. Extraire heightmap (16x16 ou selon la résolution LOD)
    // 3. Extraire colormap
    // 4. Extraire biomes
    // 5. Créer et retourner LodChunkData
}
```

#### Comment tester
1. Installer Distant Horizons dans le client de test
2. Ajouter des logs pour voir quelles classes/méthodes DH expose
3. Utiliser le debugger pour explorer les objets DH

### 2. Intégrations Minimaps

**Fichier** : `MinimapAPI.java`

#### VoxelMap

**Classe cible** : `com.mamiyaotaru.voxelmap.*`

Chercher dans le code de VoxelMap :
- Comment sont stockées les régions/chunks
- API pour mettre à jour les tiles
- Format de données attendu (couleurs, hauteurs)

```java
// Dans VoxelMapProvider.updateChunk()
public void updateChunk(DistantHorizonsAPI.LodChunkData lodData) {
    // Exemple hypothétique:
    // 1. Obtenir le CachedRegion de VoxelMap
    // 2. Pour chaque position dans le chunk:
    //    - Mettre à jour la couleur
    //    - Mettre à jour la hauteur
    //    - Marquer comme modifié
    // 3. Déclencher le re-render
}
```

#### JourneyMap

**Classe cible** : `journeymap.client.api.*`

JourneyMap a une API publique documentée :
- `IClientAPI.getClientApi()`
- Méthodes pour créer des overlays
- Possibilité de mettre à jour programmatiquement

```java
// Dans JourneyMapProvider.updateChunk()
public void updateChunk(DistantHorizonsAPI.LodChunkData lodData) {
    // JourneyMap API example:
    // IClientAPI jmAPI = IClientAPI.INSTANCE.getClientApi();
    // Pour chaque position, créer/mettre à jour des markers ou polygons
}
```

#### Xaero's World Map

**Classe cible** : `xaero.map.*`

Xaero's est plus fermé, pourrait nécessiter :
- Mixins pour injecter dans leur système de render
- Modification directe de leurs fichiers de région
- Ou trouver une API cachée

```java
// Dans XaerosMapProvider.updateChunk()
public void updateChunk(DistantHorizonsAPI.LodChunkData lodData) {
    // Possiblement besoin de mixins
    // Ou accès direct aux fichiers de cache
}
```

### 3. Optimisations

- [ ] **Batch updates** : Grouper plusieurs chunks avant mise à jour
- [ ] **Threading** : Traiter les chunks LOD dans un thread séparé
- [ ] **Cache** : Éviter de retraiter les mêmes chunks
- [ ] **Distance check** : Ne synchroniser que les chunks proches du joueur
- [ ] **Config** : Ajouter des options (rayon de sync, fréquence, etc.)

### 4. Fichiers à nettoyer

Ces anciens fichiers ne sont plus utilisés et peuvent être supprimés :
- `DistantHorizonsIntegration.java` (remplacé par `integration/DistantHorizonsAPI.java`)
- `MinimapIntegration.java` (remplacé par `integration/MinimapAPI.java`)
- `LodChunkData.java` (maintenant dans `DistantHorizonsAPI.LodChunkData`)

## Commandes de Développement

```bash
# Compiler
gradlew.bat build

# Lancer le client de test
gradlew.bat runClient

# Nettoyer
gradlew.bat clean

# Générer les sources pour IDE
gradlew.bat genSources
```

## Debug et Tests

### Installer les dépendances dans le client de test

1. Télécharger les mods :
   - [Distant Horizons](https://modrinth.com/mod/distanthorizons)
   - [VoxelMap](https://modrinth.com/mod/voxelmap-updated) ou
   - [JourneyMap](https://modrinth.com/mod/journeymap) ou
   - [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map)

2. Placer dans `run/mods/`

3. Lancer avec `gradlew.bat runClient`

### Activer les logs détaillés

Dans le code, changer les `LOGGER.debug()` en `LOGGER.info()` pour voir plus de détails.

### Points de breakpoint utiles

- `DistantSyncMod.onClientTick()` - Vérifie la boucle principale
- `DistantHorizonsAPI.detectDistantHorizons()` - Vérifie la détection de DH
- `MinimapAPI.detectMinimaps()` - Vérifie la détection des minimaps
- `onChunkLoad()` - Vérifie la réception des chunks

## Prochaines Étapes Recommandées

1. **Phase 1 : Recherche**
   - Télécharger et décompiler Distant Horizons
   - Explorer leur API dans `com.seibel.distanthorizons.api.*`
   - Noter les classes et méthodes importantes

2. **Phase 2 : Implémentation DH**
   - Implémenter `hasLodData()`
   - Implémenter `extractLodData()`
   - Tester avec des logs

3. **Phase 3 : Implémentation Minimaps**
   - Commencer par VoxelMap (généralement le plus simple)
   - Puis JourneyMap (API publique)
   - Enfin Xaero's (plus complexe)

4. **Phase 4 : Optimisation et Polish**
   - Ajouter la configuration
   - Optimiser les performances
   - Améliorer l'UX

## Ressources

- **Distant Horizons** : https://gitlab.com/distant-horizons-team
- **VoxelMap** : https://github.com/MamiyaOtaru/VoxelMap
- **JourneyMap API** : Documentation dans leur JAR
- **NeoForge Events** : https://docs.neoforged.net/docs/events/
- **Mixin Tutorial** : https://github.com/SpongePowered/Mixin/wiki

## Contact / Support

Si besoin d'aide :
- Discuter avec la communauté Distant Horizons
- Regarder comment d'autres mods intègrent avec les minimaps
- Étudier le code source de mapsync-mod pour inspiration
