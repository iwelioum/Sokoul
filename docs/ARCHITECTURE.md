# Sokoul — Architecture Technique

**Dernière mise à jour :** Février 2026

---

## Vue d'ensemble

```
┌─────────────────────────────────────────┐
│  SvelteKit Frontend (port 5173 dev)     │
│  dashboard/src/                         │
│  • Browse, Search, Watch pages          │
│  • CustomPlayer.svelte (HLS.js)         │
└────────────────────┬────────────────────┘
                     │ /api/* → proxy vers :3000
                     ▼
┌─────────────────────────────────────────┐
│  Rust Backend — Axum (port 3000)        │
│  src/                                   │
│  • TMDB metadata API                   │
│  • Streaming resolver (Consumet)        │
│  • Streaming proxy (headers Referer)    │
│  • Download management                  │
│  • Watch history / library              │
└──────┬──────────────┬───────────────────┘
       │              │
       ▼              ▼
┌────────────┐  ┌─────────────────────────┐
│ PostgreSQL │  │ Consumet API (port 3002)│
│ port 5432  │  │ Node.js + ts-node       │
│ migrations/│  │ Providers: HiMovies,    │
│ SQLx       │  │ Goku, FlixHQ, ...       │
└────────────┘  └─────────────────────────┘
       │
┌──────▼─────┐
│   Redis    │
│  port 6379 │
│  Cache API │
│  Sessions  │
└────────────┘
```

---

## Composants

### Backend Rust (`src/`)

| Module | Fichier | Description |
|--------|---------|-------------|
| Config | `config.rs` | Variables d'environnement |
| Main | `main.rs` | Setup Axum, routes, AppState |
| Models | `models.rs` | Structs partagées (StreamSource, etc.) |
| Cache | `cache.rs` | Redis client wrapper |
| Metrics | `metrics.rs` | Prometheus metrics |
| Security | `security.rs` | Middleware sécurité |

**API (`src/api/`)**

| Fichier | Endpoints |
|---------|-----------|
| `streaming.rs` | `GET /streaming/consumet/:type/:id` — résolution sources<br>`GET /streaming/proxy` — proxy m3u8 avec headers |
| `health.rs` | `GET /health` |
| autres | TMDB, library, history, search |

**Clients externes (`src/clients/`)**

| Fichier | Description |
|---------|-------------|
| `consumet.rs` | Client HTTP vers Consumet API — résout les sources HLS |
| `stream.rs` | Client IPTV live (usage secondaire) |
| `mod.rs` | Exports |

**Extracteurs (`src/extractors/`)**

Ancienne couche d'extraction (pré-Consumet). Dépréciée — remplacée par `clients/consumet.rs`.

---

### Frontend SvelteKit (`dashboard/src/`)

| Répertoire | Description |
|-----------|-------------|
| `routes/` | Pages SvelteKit |
| `routes/watch/[media_type]/[tmdb_id]/` | Page de lecture |
| `lib/components/CustomPlayer.svelte` | Lecteur HLS.js custom |
| `lib/api/client.ts` | Appels API backend |
| `lib/streaming/` | Utilitaires streaming |

**Flux de lecture :**
1. L'utilisateur clique "Regarder" sur un film/série
2. La page `watch/+page.svelte` appelle `GET /api/streaming/consumet/movie/:tmdb_id`
3. Le backend appelle Consumet → récupère les sources HLS
4. Le backend retourne les URLs + headers requis
5. `CustomPlayer.svelte` charge l'URL via HLS.js
6. Si l'URL nécessite un Referer, le player la proxifie via `/api/streaming/proxy`

---

### Infrastructure Docker (`docker-compose.yml`)

| Service | Image | Port | Rôle |
|---------|-------|------|------|
| postgres | pgvector/pgvector:pg16 | 5432 | Base de données principale |
| redis | redis:7.2-alpine | 6379 | Cache (TMDB, sessions, Consumet) |
| nats | nats:2.10-alpine | 4222 | Message broker (jobs async) |
| prowlarr | linuxserver/prowlarr | 9696 | Indexeur torrents (via Gluetun VPN) |
| gluetun | qmcgaw/gluetun | — | Tunnel VPN pour Prowlarr |
| flaresolverr | flaresolverr | 8191 | Bypass Cloudflare pour scraping |
| prometheus | prom/prometheus | 9090 | Collecte métriques |
| grafana | grafana/grafana | 3001 | Dashboards |
| loki | grafana/loki | 3100 | Agrégation logs |

**Consumet API** — non dockerisé actuellement, lancé manuellement :
```bash
cd /path/to/consumet
NODE_TLS_REJECT_UNAUTHORIZED=0 TMDB_KEY=<key> PORT=3002 node -r ts-node/register src/main.ts
```

---

### Base de données

Migrations dans `migrations/`. Schema principal dans `migrations/0001_initial_schema.sql`.

Tables clés :
- `media` — films et séries (TMDB ID, metadata)
- `watch_history` — historique de lecture par utilisateur
- `library_favorites`, `library_watchlist` — bibliothèque utilisateur
- `tasks` — jobs async (téléchargements, etc.)
- `audit_logs` — traçabilité des actions
- `users` — authentification (JWT)

---

## Flux Streaming détaillé

```
[Browser]
    │  GET /api/streaming/consumet/movie/550
    ▼
[Rust Backend — streaming.rs]
    │  GET http://localhost:3002/meta/tmdb/info/550?type=movie&provider=HiMovies
    │  → récupère episodeId depuis Consumet
    │  GET http://localhost:3002/meta/tmdb/watch?episodeId=...&id=...&provider=HiMovies
    │  → récupère sources[] + headers{}
    ▼
[Response JSON]
    {
      "sources": [{ "url": "https://...m3u8", "quality": "1080p", "headers": {"Referer": "..."} }],
      "subtitles": [...]
    }
    │
    ▼
[CustomPlayer.svelte]
    │  Si headers présents → URL proxifiée via /api/streaming/proxy?url=...&referer=...
    │  Si non → URL directe
    ▼
[HLS.js]
    │  Charge le .m3u8 → segments .ts → lecture
```

---

## État actuel des providers Consumet

| Provider | Recherche | Sources | Problème |
|----------|-----------|---------|----------|
| HiMovies | ✅ | ✅ serveurs OK | Extracteur MegaCloud/VideoStr cassé (crawlr.cc mort) |
| Goku | ✅ | ❌ | Streams nécessitent cookies de session navigateur |
| FlixHQ | ❌ | ❌ | Résultats vides (scraping bloqué) |
| SFlix | ❌ | ❌ | Résultats vides |

> **Note :** Les extracteurs Consumet dépendent de services tiers (crawlr.cc) qui ont été désactivés. C'est le blocage principal du module streaming.
