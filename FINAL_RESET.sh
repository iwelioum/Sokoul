#!/bin/bash

echo "🔥 Reset complet de la base de données..."
echo ""

echo "1️⃣ Arrêt et suppression des volumes..."
docker-compose down -v

echo ""
echo "2️⃣ Redémarrage des services..."
docker-compose up -d

echo ""
echo "3️⃣ Lancement du serveur..."
cargo run

echo ""
echo "✅ C'est fait!"
