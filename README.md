# Sokoul — Media Streaming Platform

Self-hosted media platform built with Rust + SvelteKit. Browse movies and TV shows via TMDB, stream via Consumet API.

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Rust (Axum) — port 3000 |
| Frontend | SvelteKit 5 — port 5173 |
| Database | PostgreSQL 16 |
| Cache | Redis 7.2 |
| Streaming | Consumet API (Node.js) — port 3002 |
| Indexer | Prowlarr — port 9696 |
| Monitoring | Prometheus (9090) + Grafana (3001) |

---

## Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start backend
cargo run

# 3. Start frontend
cd dashboard && npm run dev

# 4. Start Consumet API
cd /path/to/consumet
NODE_TLS_REJECT_UNAUTHORIZED=0 TMDB_KEY=<your_key> PORT=3002 node -r ts-node/register src/main.ts
```

Visit `http://localhost:5173`

---

## Environment

Copy `.env.example` to `.env`:

```env
DATABASE_URL=postgresql://sokoul:sokoul@localhost:5432/sokoul_db
REDIS_URL=redis://localhost:6379
TMDB_API_KEY=your_tmdb_key
CONSUMET_URL=http://localhost:3002
```

---

## Documentation

All docs are in [`docs/`](docs/):

| File | Description |
|------|-------------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Technical architecture |
| [CAHIER_DES_CHARGES.md](docs/CAHIER_DES_CHARGES.md) | Project specifications |
| [STREAMING.md](docs/STREAMING.md) | Streaming module (Consumet) |
| [API.md](docs/API.md) | API reference |
| [QUICK_START.md](docs/QUICK_START.md) | Detailed setup guide |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production deployment |
| [TESTING.md](docs/TESTING.md) | Testing guide |

---

## Docker Services

```bash
docker-compose up -d   # Start all
docker-compose ps      # Check status
docker-compose logs -f # View logs
```

| Service | Port | Description |
|---------|------|-------------|
| postgres | 5432 | Database |
| redis | 6379 | Cache |
| prowlarr | 9696 | Torrent indexer |
| prometheus | 9090 | Metrics |
| grafana | 3001 | Dashboards |
| flaresolverr | 8191 | Cloudflare bypass |
