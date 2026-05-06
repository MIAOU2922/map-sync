# DistantSync

Un mod Minecraft qui synchronise les chunks LOD de Distant Horizons avec les minimaps pour une visibilité étendue du terrain.

## Description

DistantSync fait le pont entre [Distant Horizons](https://gitlab.com/distant-horizons-team) (qui génère du terrain au-delà de la distance de rendu normale) et les mods de minimap populaires. Il surveille les données LOD de Distant Horizons et les pousse vers les mods de minimap compatibles.

## Fonctionnalités

- ✅ Détection automatique de Distant Horizons
- ✅ Support multi-minimap (VoxelMap, JourneyMap, Xaero's World Map)
- ✅ Architecture modulaire avec réflexion (pas de dépendances dures)
- ✅ Gestion des événements de chunks Minecraft
- ✅ Système de cache pour éviter les doublons
- 🚧 Extraction de données LOD depuis Distant Horizons (à implémenter)
- 🚧 Synchronisation réelle vers les minimaps (à implémenter)
- 🚧 Optimisations de performance (batch updates, threading)

**Note** : Le mod est actuellement en phase de développement initial. La structure de base est en place, mais l'intégration réelle avec les APIs de Distant Horizons et des minimaps reste à implémenter.

## Prérequis

- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.228+
- **Java**: 21
- **Mods requis**:
  - Distant Horizons 2.x
  - Au moins un mod de minimap supporté:
    - VoxelMap Updated
    - JourneyMap
    - Xaero's World Map

## Installation

1. Installez [NeoForge](https://neoforged.net/) pour Minecraft 1.21.1
2. Installez [Distant Horizons](https://modrinth.com/mod/distanthorizons)
3. Installez votre minimap préférée (VoxelMap, JourneyMap, ou Xaero's)
4. Placez `DistantSync-*.jar` dans votre dossier `mods`
5. Lancez Minecraft

## Développement

### Prérequis de développement

- Java Development Kit (JDK) 21
- Gradle (inclus via wrapper)

### Commandes de build

```bash
# Compiler le mod
./gradlew build

# Lancer le client de test
./gradlew runClient

# Générer les sources
./gradlew genSources
```

### Structure du projet

```
src/main/java/gjum/minecraft/distantsync/mod/
├── DistantSyncMod.java              # Classe principale du mod
└── integration/
    ├── DistantHorizonsAPI.java      # API pour Distant Horizons (réflexion)
    └── MinimapAPI.java              # API pour les minimaps (réflexion)
```

**Pour les développeurs** : Consultez [DEVELOPMENT.md](DEVELOPMENT.md) pour un guide détaillé sur comment continuer le développement.

## Comment ça marche

1. **Détection**: Au démarrage, DistantSync détecte Distant Horizons et les mods de minimap installés
2. **Surveillance**: Le mod surveille les mises à jour de chunks LOD de Distant Horizons
3. **Conversion**: Les données LOD sont converties au format requis par la minimap active
4. **Synchronisation**: Les chunks sont poussés vers la minimap en temps réel

## API Distant Horizons

Ce mod utilise l'API publique de Distant Horizons pour accéder aux données LOD. Documentation de référence:
- [Distant Horizons GitLab](https://gitlab.com/distant-horizons-team)
- [DH API Documentation](https://gitlab.com/distant-horizons-team/DistantHorizons/-/wikis/API)

## Compatibilité

### Minimaps supportées

| Minimap | Status | Notes |
|---------|--------|-------|
| VoxelMap Updated | 🚧 En cours | API en cours d'intégration |
| JourneyMap | 🚧 En cours | API en cours d'intégration |
| Xaero's World Map | 🚧 En cours | API en cours d'intégration |

### Versions Minecraft

| Version | Support |
|---------|---------|
| 1.21.1 | ✅ Supporté |
| Autres | ❌ Non supporté actuellement |

## Problèmes connus

- L'intégration avec les APIs des minimaps est en cours de développement
- Les chunks LOD ne sont pas encore effectivement synchronisés (structure de base en place)

## Contribution

Les contributions sont les bienvenues ! N'hésitez pas à:
- Signaler des bugs via les [Issues](https://github.com/CivPlatform/map-sync/issues)
- Proposer des améliorations
- Soumettre des Pull Requests

## Licence

GPL-3.0 - Voir le fichier LICENSE pour plus de détails

## Auteurs

- Gjum - Développeur principal

## Remerciements

- L'équipe Distant Horizons pour leur excellent mod
- Les développeurs des mods de minimap pour leurs APIs
- La communauté CivPlatform
