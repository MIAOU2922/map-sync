# TODO - DistantSync

## ✅ Complété

### API Distant Horizons (Priorité Haute 🔴)
- ✅ Étudié l'API officielle de Distant Horizons via JavaDoc et exemples GitLab
- ✅ Identifié les classes principales : `DhApi`, `DhApi.Delayed`, `IDhApiTerrainDataRepo`
- ✅ Implémenté `hasLodData()` pour vérifier la présence de chunks LOD
- ✅ Implémenté `extractLodData()` pour récupérer heightmap/colors/biomes
- ✅ Implémenté `parseLodData()` pour convertir `DhApiTerrainDataPoint[][][]` en `LodChunkData`
- ✅ Ajouté initialisation lazy de `DhApi.Delayed` (terrainRepo, worldProxy, cache)
- ✅ Ajouté tracking des chunks traités pour éviter les doublons
- ⏳ **À tester** avec DH installé dans `run/mods/`

**Fichiers modifiés** :
- ✅ `src/main/java/gjum/minecraft/distantsync/mod/integration/DistantHorizonsAPI.java`

### Logique principale (Priorité Haute 🔴)
- ✅ Implémenté `checkForLodUpdates()` qui scanne les chunks LOD autour du joueur
- ✅ Scan dans un rayon de 8 chunks (configurable)
- ✅ Limite à 5 chunks/tick pour éviter les lags
- ✅ Vérifie que le chunk n'est PAS déjà chargé normalement
- ✅ Vérifie que le chunk n'a PAS déjà été traité
- ✅ Marque les chunks normaux comme "prioritaires" dans `onChunkLoad()`
- ✅ Empêche l'écrasement des chunks réels par des données LOD

**Fichiers modifiés** :
- ✅ `src/main/java/gjum/minecraft/distantsync/mod/DistantSyncMod.java`

### Build
- ✅ Compilation réussie avec Gradle
- ✅ Aucune erreur de compilation
- ✅ JAR généré : `build/libs/distantsync-mod-1.0.0-all.jar`

---

## Priorité Haute 🔴

### 1. Test Runtime avec Distant Horizons
**Action immédiate nécessaire !**
- [ ] Télécharger Distant Horizons mod depuis [CurseForge](https://www.curseforge.com/minecraft/mc-mods/distant-horizons)
- [ ] Placer dans `distantsync-mod/run/mods/`
- [ ] Télécharger VoxelMap ou autre minimap
- [ ] Lancer avec `gradlew.bat runClient`
- [ ] Vérifier dans les logs :
  - `[DistantSync] Distant Horizons API detected!`
  - `[DistantSync] Distant Horizons API fully initialized!`
  - `[DistantSync] Processing LOD chunk...`
- [ ] Observer si la minimap se remplit avec les données LOD

**Résultat attendu** : Confirmer que l'extraction des données DH fonctionne réellement.

### 2. Intégration VoxelMap
**Dépend du test #1**
- [ ] Une fois DH confirmé fonctionnel, étudier le code de VoxelMap
- [ ] Trouver comment accéder aux `CachedRegion` ou équivalent
- [ ] Implémenter `VoxelMapProvider.updateChunk()` :
  ```java
  public void updateChunk(LodChunkData data) {
      // Convertir heightMap/colorMap en format VoxelMap
      // Injecter dans la carte
  }
  ```
- [ ] Tester visuellement : les chunks LOD apparaissent sur la minimap

**Fichiers à modifier** :
- `src/main/java/gjum/minecraft/distantsync/mod/integration/MinimapAPI.java` (classe `VoxelMapProvider`)

---

## Priorité Moyenne 🟡

### 3. Intégration JourneyMap
- [ ] Étudier l'API publique de JourneyMap ([Documentation](https://journeymap.info/JourneyMap_API))
- [ ] Utiliser `IClientAPI` pour les mises à jour
- [ ] Implémenter `JourneyMapProvider.updateChunk()`
- [ ] Tester avec JourneyMap installé

**Fichiers à modifier** :
- `src/main/java/gjum/minecraft/distantsync/mod/integration/MinimapAPI.java` (classe `JourneyMapProvider`)

### 4. Intégration Xaero's Map
- [ ] Étudier le code de Xaero's World Map
- [ ] Trouver l'API (ou créer des mixins si nécessaire)
- [ ] Implémenter `XaerosMapProvider.updateChunk()`
- [ ] Tester avec Xaero's installé

**Fichiers à modifier** :
- `src/main/java/gjum/minecraft/distantsync/mod/integration/MinimapAPI.java` (classe `XaerosMapProvider`)
- Possiblement : nouveaux fichiers mixin si nécessaire

### 5. Améliorer getBlockColor()
**Actuellement retourne gris par défaut**
- [ ] Implémenter la conversion BlockState → Couleur RGB
- [ ] Utiliser les textures ou BiomeColors de Minecraft
- [ ] Gérer les cas spéciaux (eau, lave, herbe avec teinture de biome)

**Fichiers à modifier** :
- `src/main/java/gjum/minecraft/distantsync/mod/integration/DistantHorizonsAPI.java` (méthode `LodChunkData.getBlockColor()`)

### 6. Gestion des dimensions
- [ ] Détecter les changements de dimension (event `PlayerChangedDimensionEvent`)
- [ ] Appeler `clearProcessedChunks()` lors du changement
- [ ] Tester Overworld → Nether → End
- [ ] Vérifier que les chunks ne se mélangent pas entre dimensions

---

## Priorité Basse 🟢

### 7. Configuration
- [ ] Créer fichier `distantsync.toml` ou `distantsync.json`
- [ ] Options à ajouter :
  ```toml
  [distantsync]
  enabled = true
  scan_radius = 8  # chunks
  check_interval_ms = 1000
  max_chunks_per_tick = 5
  log_level = "INFO"  # DEBUG, INFO, WARN, ERROR
  
  [minimaps]
  voxelmap = true
  journeymap = true
  xaeros = true
  ```
- [ ] Charger la config au démarrage
- [ ] Utiliser les valeurs dans le code

### 8. Optimisations
- [ ] **Scan en spirale** au lieu de carré (plus naturel, commence proche du joueur)
- [ ] **Batch updates** : grouper plusieurs chunks avant mise à jour minimap
- [ ] **Thread séparé** pour le traitement LOD (ne pas bloquer le thread principal)
- [ ] **Cache intelligent** avec TTL (Time To Live)
- [ ] **Adapter la fréquence** selon FPS (réduire si < 30 FPS)

### 9. Interface Utilisateur
- [ ] Commande `/distantsync status` → Affiche état (DH détecté, minimaps actives, chunks traités)
- [ ] Commande `/distantsync reload` → Recharge la config
- [ ] Commande `/distantsync clear` → Nettoie le cache de chunks traités
- [ ] Écran de configuration in-game (GUI Forge/NeoForge)
- [ ] Statistiques : chunks/s, chunks totaux, minimap utilisée

### 10. Tests et Debug
- [ ] Créer des tests unitaires pour `parseLodData()`
- [ ] Tester avec plusieurs minimaps simultanément
- [ ] Tester performances avec 1000+ chunks LOD
- [ ] Profiler avec JProfiler ou VisualVM
- [ ] Tester le comportement avec lag réseau (si intégration serveur future)

---

## Nettoyage 🧹

### 11. Supprimer fichiers obsolètes
**Ces fichiers ont été remplacés par la nouvelle architecture**
- [ ] `src/main/java/gjum/minecraft/distantsync/mod/DistantHorizonsIntegration.java`
- [ ] `src/main/java/gjum/minecraft/distantsync/mod/MinimapIntegration.java`
- [ ] `src/main/java/gjum/minecraft/distantsync/mod/LodChunkData.java`

**Vérifier qu'aucun code ne les référence avant suppression !**

### 12. Documentation et Code Quality
- [ ] Améliorer JavaDoc pour toutes les méthodes publiques
- [ ] Ajouter des commentaires explicatifs dans les sections complexes
- [ ] Formatter le code avec un style cohérent
- [ ] Ajouter des exemples de logs dans DEVELOPMENT.md
- [ ] Créer CHANGELOG.md

### 13. README et Guide
- [ ] Ajouter des **screenshots** montrant :
  - Minimap avant (vide)
  - Minimap après (remplie avec LODs)
  - Comparaison chunk réel vs chunk LOD
- [ ] Ajouter section **Installation détaillée**
- [ ] Ajouter section **FAQ** (Pourquoi ma minimap ne se remplit pas ? etc.)
- [ ] Ajouter section **Compatibilité** (versions testées)

---

## Features Futures 💡

### 14. Intégration avec mapsync-server
**Synchroniser les données entre joueurs**
- [ ] Envoyer les chunks LOD au serveur mapsync
- [ ] Recevoir les chunks d'autres joueurs
- [ ] Prioriser : Chunk réel > Chunk joueur > Chunk LOD
- [ ] Compresser les données avant envoi

### 15. Support Multi-Dimension
- [ ] Gérer Nether, End, dimensions custom
- [ ] Séparer les caches par dimension
- [ ] Éviter les conflits de ChunkPos entre dimensions

### 16. Export/Import
- [ ] Exporter la carte complète en PNG
- [ ] Importer des données de carte externes
- [ ] Format de fichier standardisé

### 17. Mode Serveur
**Optionnel : Générer des LODs côté serveur**
- [ ] Plugin Bukkit/Spigot/Paper
- [ ] Envoyer les LODs pré-calculés aux clients
- [ ] Réduire la charge client

---

## Prochaines Actions Recommandées

1. **✅ FAIT** - Implémenter l'extraction DH
2. **🔴 URGENT** - Tester avec DH réel (#1)
3. **🔴 URGENT** - Implémenter VoxelMap (#2)
4. **🟡 Important** - Améliorer getBlockColor (#5)
5. **🟢 Optionnel** - Ajouter configuration (#7)

**État actuel** : Prêt pour les tests runtime ! 🚀
