#!/bin/bash
set -e

echo "🚀 Initialisation de l'environnement SOKOUL v2..."

# 1. Création des répertoires de données
echo "📂 Création des dossiers..."
mkdir -p downloads
mkdir -p grafana/dashboards
mkdir -p grafana/datasources

# 2. Vérification du fichier .env
if [ ! -f .env ]; then
    echo "⚠️  Fichier .env manquant. Copie de .env.example..."
    cp .env.example .env
    echo "✅ .env créé. Pensez à modifier vos clés API !"
else
    echo "✅ Fichier .env présent."
fi

# 3. Permissions pour Docker (si nécessaire sur Linux)
# chmod -R 777 grafana  # Parfois nécessaire selon l'user Docker

echo "✅ Setup terminé !"
echo "👉 Lancez 'docker-compose up -d' pour démarrer l'infrastructure."
echo "👉 Lancez 'cargo run' pour démarrer SOKOUL Core."