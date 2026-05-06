# DistantSync - Résumé de l'implémentation

## 🎯 Objectif

DistantSync synchronise automatiquement les données LOD (Level Of Detail) de **Distant Horizons** avec les **minimaps** (VoxelMap, JourneyMap, Xaero's).

**Principe clé** : Générer la minimap à partir des chunks LOD de Distant Horizons **uniquement si le chunk n'a pas déjà été chargé** par le joueur ou reçu via un autre moyen (mapsync-server, exploration normale).

## ✅ Fonctionnalités implémentées

### 1. Intégration avec Distant Horizons API

**Fichier** : `DistantHorizonsAPI.java`

- ✅ Détection automatique de Distant Horizons via reflection
- ✅ Initialisation de l'API `DhApi.Delayed` (terrainRepo, worldProxy)
- ✅ Création d'un cache de terrain pour améliorer les performances
- ✅ Méthode `hasLodData(ChunkPos, Level)` - Vérifie si DH a des données LOD pour un chunk
- ✅ Méthode `extractLodData(ChunkPos, Level)` - Extrait les données LOD réelles :
  - Heightmap (hauteur du terrain)
  - ColorMap (couleurs des blocs)
  - BiomeIds (identifiants de biomes)
- ✅ Tracking des chunks traités pour éviter les doublons

**API utilisée** :
```java
DhApi.Delayed.terrainRepo.getAllTerrainDataAtChunkPos(levelWrapper, chunkX, chunkZ, cache)
```

Retourne un tableau 3D : `DhApiTerrainDataPoint[x][z][columnIndex]`

Chaque point contient :
- `topYBlockPos` / `bottomYBlockPos` - Hauteur
- `blockStateWrapper` - État du bloc (pour la couleur)
- `biomeWrapper` - Biome
- `blockLightLevel` / `skyLightLevel` - Lumière

### 2. Intégration avec les Minimaps

**Fichier** : `MinimapAPI.java`

- ✅ Détection automatique de VoxelMap, JourneyMap, Xaero's via reflection
- ✅ Architecture modulaire avec 3 providers :
  - `VoxelMapProvider`
  - `JourneyMapProvider`
  - `XaerosMapProvider`
- ✅ Méthode `updateChunk(LodChunkData)` - Envoie les données aux minimaps
- 🟡 Implémentation des providers - **TODO** (besoin d'étudier les APIs des minimaps)

### 3. Logique principale

**Fichier** : `DistantSyncMod.java`

#### Event Handler : `onClientTick()`
- Initialise l'API DH dès qu'elle est disponible
- Appelle `checkForLodUpdates()` toutes les secondes

#### Event Handler : `onChunkLoad()`
- **Marque immédiatement** les chunks chargés normalement comme "traités"
- Envoie leurs données aux minimaps
- **Évite ainsi de les remplacer** par des données LOD moins précises

#### Méthode : `checkForLodUpdates()`
```java
// 1. Scanne un rayon de 8 chunks autour du joueur
// 2. Pour chaque chunk NON chargé et NON traité :
//    - Vérifie si DH a des données LOD
//    - Extrait les données
//    - Envoie aux minimaps
//    - Marque comme traité
// 3. Limite à 5 chunks par tick pour éviter les lags
```

**Résultat** : Les chunks LOD de DH remplissent la minimap **uniquement dans les zones non explorées**.

## 🔧 Structure des données

### LodChunkData
```java
public static class LodChunkData {
    ChunkPos pos;
    int[][] heightMap;  // [16][16] - Hauteur en blocs
    int[][] colorMap;   // [16][16] - Couleurs RGB
    byte[][] biomeIds;  // [16][16] - IDs de biomes
    long timestamp;     // Horodatage
}
```

### Conversion DH → LodChunkData
1. Récupère `getAllTerrainDataAtChunkPos()` de DH
2. Pour chaque colonne [x][z], prend le point le plus haut
3. Extrait `topYBlockPos`, `blockStateWrapper`, `biomeWrapper`
4. Convertit en heightmap/colormap/biomeIds

## 🚀 Utilisation

### Installation
1. Compiler : `gradlew.bat build`
2. Le JAR est dans `build/libs/distantsync-mod-1.0.0-all.jar`
3. Placer dans le dossier `mods/` de Minecraft
4. Nécessite :
   - NeoForge 21.1.228+
   - Minecraft 1.21.1
   - Distant Horizons (optionnel)
   - Au moins une minimap (VoxelMap/JourneyMap/Xaero's)

### Comportement
- ✅ Détecte automatiquement DH et les minimaps au démarrage
- ✅ Scanne les chunks LOD toutes les secondes
- ✅ N'envoie que les chunks **non chargés**
- ✅ Évite les doublons avec le tracking des chunks traités
- ✅ Limite à 5 chunks/tick pour maintenir les performances

### Logs
```
[DistantSync] Distant Horizons API detected!
[DistantSync] Distant Horizons API fully initialized with terrainRepo and worldProxy
[DistantSync] VoxelMap detected!
[DistantSync] DistantSync ready! Detected minimaps: VoxelMap
[DistantSync] Processing LOD chunk [123, 456] from Distant Horizons
[DistantSync] Processed 3 LOD chunks from Distant Horizons this tick
```

## 📋 TODO - Prochaines étapes

### Priorité Haute 🔴
1. **Implémenter VoxelMapProvider.updateChunk()**
   - Étudier l'API VoxelMap
   - Trouver les méthodes pour injecter des données de chunk
   - Convertir heightMap/colorMap en format VoxelMap

2. **Tester avec Distant Horizons réel**
   - Télécharger le mod DH
   - Générer des LODs en jeu
   - Vérifier que `extractLodData()` fonctionne correctement

### Priorité Moyenne 🟡
3. **Implémenter JourneyMapProvider et XaerosMapProvider**
4. **Améliorer getBlockColor()** - Actuellement retourne gris par défaut
5. **Gérer les changements de dimension** - Appeler `clearProcessedChunks()`

### Priorité Basse 🟢
6. **Configuration** - Rayon de scan, fréquence, chunks/tick
7. **Interface utilisateur** - Commandes pour activer/désactiver
8. **Optimisations** - Cache plus intelligent, scan spiral au lieu de carré

## 🧪 Tests nécessaires

1. ✅ Compilation - **OK**
2. ⏳ Test runtime avec DH installé
3. ⏳ Vérifier que les chunks normaux sont prioritaires
4. ⏳ Vérifier que les chunks LOD remplissent bien la minimap
5. ⏳ Tester les performances (FPS avec scan actif)

## 📚 Ressources

- [Distant Horizons API JavaDoc](https://distant-horizons-team.gitlab.io/distant-horizons/)
- [API Examples](https://gitlab.com/distant-horizons-team/distant-horizons-api-example)
- [VoxelMap GitHub](https://github.com/MamiyaOtaru/VoxelMap) - Pour étudier l'API
- [JourneyMap API](https://journeymap.info/JourneyMap_API) - Documentation officielle
- [Xaero's Minimap](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap) - Vérifier si API publique

## 🎉 Résultat attendu

Quand un joueur explore avec Distant Horizons :
1. DH génère des LODs pour les chunks distants (visible à l'horizon)
2. DistantSync détecte ces nouveaux LODs
3. DistantSync extrait les données (hauteur, couleur, biome)
4. DistantSync envoie aux minimaps
5. La minimap affiche maintenant le terrain distant, pas juste le vide !

**Chunks normalement chargés** → Prioritaires, haute qualité, jamais écrasés
**Chunks LOD de DH** → Remplissent les trous, qualité moyenne, mis à jour si le chunk se charge vraiment

