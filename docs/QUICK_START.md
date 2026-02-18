# Sokoul — Guide de démarrage

---

## Prérequis

- **Docker + Docker Compose**
- **Rust** (`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`)
- **Node.js 20+** (pour le frontend et Consumet)
- Clé API TMDB — https://www.themoviedb.org/settings/api

---

## 1. Variables d'environnement

```bash
cp .env.example .env
```

Remplir au minimum :

```env
DATABASE_URL=postgresql://sokoul:sokoul@localhost:5432/sokoul_db
REDIS_URL=redis://localhost:6379
TMDB_API_KEY=votre_clé_tmdb
CONSUMET_URL=http://localhost:3002
```

---

## 2. Infrastructure Docker

```bash
docker-compose up -d

# Vérifier que tout est healthy
docker-compose ps
```

Services démarrés : PostgreSQL (5432), Redis (6379), NATS (4222), Prowlarr (9696), Prometheus (9090), Grafana (3001).

---

## 3. Base de données

```bash
# Les migrations SQLx s'appliquent au démarrage de cargo run
# Ou manuellement :
cargo install sqlx-cli
sqlx migrate run --database-url postgresql://sokoul:sokoul@localhost:5432/sokoul_db
```

---

## 4. Backend Rust

```bash
cargo run
# API disponible sur http://localhost:3000
```

---

## 5. Frontend SvelteKit

```bash
cd dashboard
npm install
npm run dev
# Frontend disponible sur http://localhost:5173
```

---

## 6. Consumet API (Streaming)

```bash
cd /path/to/consumet   # Dépôt séparé : github.com/consumet/api.consumet.org

# Installation (première fois)
npm install

# Démarrage
NODE_TLS_REJECT_UNAUTHORIZED=0 \
  TMDB_KEY=votre_clé_tmdb \
  PORT=3002 \
  node -r ts-node/register src/main.ts
```

> `NODE_TLS_REJECT_UNAUTHORIZED=0` est nécessaire pour contourner les erreurs SSL de certains providers (FlixHQ).

---

## Vérification

```bash
# Backend
curl http://localhost:3000/health

# Consumet
curl http://localhost:3002/

# Test streaming (Fight Club, TMDB ID 550)
curl "http://localhost:3000/streaming/consumet/movie/550"
```

---

## Troubleshooting

**Le backend ne démarre pas :**
```bash
# Vérifier que PostgreSQL est accessible
docker-compose logs postgres
# Vérifier les variables .env
cat .env
```

**Consumet retourne des erreurs SSL :**
Vérifier que `NODE_TLS_REJECT_UNAUTHORIZED=0` est bien défini au démarrage.

**Redis cache périmé :**
```bash
docker exec $(docker ps --filter "name=redis" -q) redis-cli FLUSHDB
```

**Recompiler après changements Rust :**
```bash
cargo build && pkill sokoul; cargo run
```
