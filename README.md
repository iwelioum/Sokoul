# SOKOUL v2

Plateforme d'automatisation média haute performance en Rust.

## Prérequis

- Docker & Docker Compose
- Rust (cargo)
- Clés API : TMDB, Prowlarr (optionnel), Telegram (optionnel)

## Installation

1. **Démarrer l'infrastructure**
   ```bash
   docker-compose up -d
   ```
   Cela lance PostgreSQL, Redis, NATS et Prowlarr.

2. **Configuration**
   Copiez `.env.example` vers `.env` et remplissez vos clés.
   ```bash
   cp .env.example .env
   ```

3. **Lancer SOKOUL**
   ```bash
   cargo run
   ```
   Le serveur démarrera sur `http://127.0.0.1:3000`.

## Utilisation

### Via API
- `POST /search` : `{ "query": "Inception" }`
- `GET /media` : Liste la bibliothèque
- `POST /downloads` : Lance un téléchargement

### Via Telegram
- `/search Inception` : Recherche et affiche les résultats
- `/status` : Affiche l'état du système

## Architecture

- **Core** : Rust + Axum (API)
- **Workers** : NATS JetStream (Scout, Hunter)
- **DB** : PostgreSQL + Redis (Cache)