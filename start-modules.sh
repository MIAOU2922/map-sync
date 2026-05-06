#!/bin/bash
cd mapsync-server

# Vérifier si pnpm est disponible, sinon utiliser npm
if command -v pnpm &> /dev/null; then
    PKG_MANAGER="pnpm"
else
    echo "pnpm non trouvé, utilisation de npm"
    PKG_MANAGER="npm"
fi

# Installation des dépendances si nécessaire
if [ ! -d "node_modules" ]; then
    echo "Installation des dépendances avec $PKG_MANAGER..."
    $PKG_MANAGER install
fi

# Compilation TypeScript
echo "Compilation du projet..."
$PKG_MANAGER run build

# Démarrage du serveur
echo "Démarrage du serveur MapSync..."
node -r source-map-support/register dist/main.js
