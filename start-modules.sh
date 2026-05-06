#!/bin/bash
echo "[MapSync] Démarrage du serveur Node.js"

# Ignorer les modules PHP
export SKIP_NGINX=true
export SKIP_PHP=true

cd mapsync-server || exit 1

# Vérifier Node.js
if ! command -v node &> /dev/null; then
    echo "[MapSync] ERREUR: Node.js n'est pas installé"
    exit 1
fi

# Vérifier si pnpm est disponible, sinon utiliser npm
if command -v pnpm &> /dev/null; then
    PKG_MANAGER="pnpm"
    echo "[MapSync] Utilisation de pnpm"
else
    PKG_MANAGER="npm"
    echo "[MapSync] Utilisation de npm"
fi

# Installation des dépendances
if [ ! -d "node_modules" ]; then
    echo "[MapSync] Installation des dépendances..."
    $PKG_MANAGER install || exit 1
fi

# Compilation TypeScript
echo "[MapSync] Compilation du projet..."
$PKG_MANAGER run build || exit 1

# Démarrage du serveur
echo "[MapSync] Lancement du serveur sur le port 12312..."
node -r source-map-support/register dist/main.js
