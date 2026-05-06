# MapSync Server

This server is written for nodejs v25. Use [nvm](https://github.com/nvm-sh/nvm) to easily switch to this version:

- `nvm use` (This will use the version specified in `.nvmrc`)

## Installation

```bash
pnpm install
```

## Commandes Utiles

### Développement

```bash
# Compiler le projet TypeScript
pnpm build

# Démarrer le serveur
pnpm start

# Démarrer le serveur en mode développement (avec compilation automatique et debugger)
pnpm start:dev
```

### Qualité du Code

```bash
# Vérifier les types TypeScript
pnpm check:types

# Vérifier le style du code
pnpm check:style

# Formater le code automatiquement
pnpm format

# Lancer les tests
pnpm test
```

## Configuration

Le serveur écoute sur le port **12312** par défaut (configurable dans `mapsync/config.json`).

### Fichiers de Configuration

- `mapsync/config.json` - Configuration principale du serveur
- `mapsync/whitelist.json` - Liste des joueurs autorisés
- `mapsync/uuid_cache.json` - Cache des UUID des joueurs

## Versions Supportées

Le serveur supporte actuellement la version du mod : **2.2.0-SNAPSHOT-1.21.1**

Pour modifier les versions supportées, éditez le fichier `src/constants.ts`.

## Dépannage

### Erreur "Unsupported version"

Si vous recevez l'erreur `Connected with unsupported version`, vérifiez que :
1. La version du mod correspond à celle dans `src/constants.ts`
2. Vous avez recompilé le serveur avec `pnpm build` après toute modification
3. Vous avez redémarré le serveur

### Port déjà utilisé

Si le port 12312 est déjà utilisé, modifiez le dans `mapsync/config.json`.
