# 📋 Cahier des Charges — Sokoul v2

**Plateforme d'automatisation média haute performance en Rust**  
*Dernière mise à jour : Février 2026*

---

## 1. Vision du Projet

Sokoul est une plateforme self-hosted de gestion média complète : recherche, téléchargement, streaming et suivi de contenus (films, séries). Elle combine un backend Rust performant, un frontend SvelteKit moderne, et un bot Telegram pour le contrôle à distance.

---

## 2. Architecture Technique

### 2.1 Stack

| Composant       | Technologie              | Rôle                              |
|----------------|--------------------------|-----------------------------------|
| Backend API     | Rust + Axum              | API REST, WebSocket, middleware   |
| Frontend        | SvelteKit 5 + Vite       | Dashboard web responsive          |
| Base de données | PostgreSQL 16 (pgvector) | Stockage persistant               |
| Cache           | Redis 7.2                | Cache API (TMDB), sessions        |
| Message Broker  | NATS JetStream           | Jobs asynchrones, pub/sub         |
| Torrents        | librqbit                 | Téléchargement BitTorrent natif   |
| Bot             | Telegram (teloxide)      | Contrôle à distance               |
| Monitoring      | Prometheus + Grafana     | Métriques et dashboards           |
| Logs            | Loki                     | Agrégation de logs                |
| VPN             | Gluetun                  | Tunnel VPN pour indexeurs         |
| Anti-bot        | FlareSolverr             | Bypass Cloudflare                 |

### 2.2 Services Docker Compose

| Service      | Image                          | Port  | Statut        |
|-------------|-------------------------------|-------|---------------|
| postgres    | pgvector/pgvector:pg16        | 5432  | ✅ Opérationnel |
| redis       | redis:7.2-alpine              | 6379  | ✅ Opérationnel |
| nats        | nats:2.10-alpine              | 4222  | ✅ Opérationnel |
| flaresolverr| flaresolverr:latest           | 8191  | ✅ Opérationnel |
| gluetun     | qmcgaw/gluetun:latest         | —     | ✅ VPN tunnel   |
| prowlarr    | linuxserver/prowlarr:latest   | 9696  | ✅ Via Gluetun  |
| jackett     | linuxserver/jackett:latest    | 9117  | ✅ Via Gluetun  |
| prometheus  | prom/prometheus:latest        | 9090  | ✅ Métriques    |
| grafana     | grafana/grafana:latest        | 3001  | ✅ Dashboards   |
| loki        | grafana/loki:latest           | 3100  | ✅ Logs         |

---

## 3. Fonctionnalités Implémentées ✅

### 3.1 Backend — Workers

| Worker     | Description                                                          | Statut |
|-----------|----------------------------------------------------------------------|--------|
| **Scout**    | Recherche parallèle via Prowlarr, Jackett, Streaming providers   | ✅ Complet |
| **Hunter**   | Téléchargement torrent via librqbit, gestion concurrence (semaphore) | ✅ Complet |
| **Oracle**   | Scoring IA des résultats via endpoint Llama (désactivable)       | ✅ Complet |
| **Sentinel** | Monitoring système (CPU, RAM, disque, DB, Redis, NATS) + alertes | ✅ Complet |

### 3.2 Backend — API REST (Axum)

**Routes protégées (API key middleware) :**

| Endpoint                               | Méthode | Description                      |
|---------------------------------------|---------|----------------------------------|
| `/search`                              | POST    | Lancer une recherche torrent     |
| `/search/:media_id`                    | GET     | Résultats de recherche           |
| `/downloads`                           | POST/GET| Démarrer/lister téléchargements  |
| `/media`                               | CRUD    | Gestion des médias locaux        |
| `/media/:id/files`                     | GET     | Fichiers associés                |
| `/media/:id/episodes`                  | GET     | Épisodes d'une série             |
| `/media/:id/stream`                    | GET     | Liens de streaming               |
| `/library`                             | POST/GET/DELETE | Gestion bibliothèque     |
| `/library/status/:tmdb_id/:media_type` | GET     | Statut dans bibliothèque         |
| `/watchlist`                           | POST/GET/DELETE | Gestion watchlist         |
| `/watch-history`                       | POST    | Mettre à jour progression        |
| `/watch-history/continue`              | GET     | Reprendre la lecture             |
| `/tasks`                               | POST/GET| Gestion des tâches background    |
| `/files/:file_id/stream`              | GET     | Streaming fichier                |
| `/streaming/direct/:type/:tmdb_id`    | GET     | Streaming direct                 |

**Routes TMDB (proxy avec cache Redis) :**

| Endpoint                    | Description                    |
|----------------------------|--------------------------------|
| `/tmdb/trending`           | Films/séries tendance          |
| `/tmdb/discover`           | Découverte avec filtres        |
| `/tmdb/search`             | Recherche TMDB                 |
| `/tmdb/movie/:id`          | Détails film                   |
| `/tmdb/tv/:id`             | Détails série                  |
| `/tmdb/tv/:id/season/:n`  | Détails saison                 |
| `/tmdb/credits/:type/:id`  | Casting                        |
| `/tmdb/videos/:type/:id`   | Bandes-annonces                |
| `/tmdb/watch-providers`    | Plateformes de visionnage      |
| `/tmdb/similar/:type/:id`  | Contenus similaires            |
| `/tmdb/person/:id`         | Infos acteur/réalisateur       |

**Routes publiques :**

| Endpoint  | Description                        |
|----------|-------------------------------------|
| `/health` | Health check (200 OK)              |
| `/ws`     | WebSocket temps réel               |

### 3.3 Backend — Providers de recherche

| Provider         | Type       | Statut           |
|-----------------|------------|------------------|
| Prowlarr        | Indexeur   | ✅ Fonctionnel    |
| Jackett         | Indexeur   | ✅ Fonctionnel    |
| Streaming       | Scraping   | ⚠️ Instable (Chromium) |
| RealDebrid      | Debrid     | ❌ Stub (`dead_code`) |

### 3.4 Backend — Base de données

**Schéma PostgreSQL (init.sql) :**

| Table           | Description                          | Statut |
|----------------|--------------------------------------|--------|
| `media`         | Films/Séries/Épisodes (UUID, TMDB)  | ✅ Complet |
| `media_files`   | Fichiers locaux (codec, qualité)     | ✅ Complet |
| `search_results`| Cache résultats torrent              | ✅ Complet |
| `tasks`         | Jobs asynchrones (statut, résultat)  | ✅ Complet |
| `favorites`     | Favoris utilisateur                  | ✅ Complet |
| `watchlist`     | Liste à regarder                     | ✅ Complet |
| `watch_history` | Historique + progression             | ✅ Complet |

### 3.5 Frontend — Pages SvelteKit

| Page                  | Route                        | Description                          |
|----------------------|------------------------------|--------------------------------------|
| Accueil              | `/`                          | Trending, continue watching, collections |
| Films                | `/films`                     | Catalogue films avec filtres         |
| Séries               | `/series`                    | Catalogue séries avec filtres        |
| Recherche            | `/search`                    | Recherche globale TMDB               |
| Bibliothèque         | `/library`                   | Favoris, watchlist, historique       |
| Détail Film          | `/movie/[tmdb_id]`           | Casting, vidéos, similaires, download|
| Détail Série         | `/tv/[tmdb_id]`              | Saisons, épisodes, download          |
| Lecteur              | `/watch/[type]/[tmdb_id]`    | Player vidéo streaming               |
| Profil Acteur        | `/person/[id]`               | Filmographie                         |

**Composants réutilisables :**
- `MediaCard` — Carte film/série cliquable
- `MediaRow` — Ligne horizontale scrollable
- `HeroCarousel` — Carrousel hero plein écran
- `VideoPlayer` — Lecteur vidéo modal
- `SearchModal` — Recherche globale (Ctrl+K)
- `Skeleton` — Placeholder de chargement

### 3.6 Telegram Bot

| Commande       | Description                    | Statut |
|---------------|--------------------------------|--------|
| `/help`        | Aide et commandes disponibles | ✅ |
| `/search`      | Rechercher un média           | ✅ |
| `/downloads`   | Téléchargements actifs        | ✅ |
| `/library`     | Contenu de la bibliothèque    | ⚠️ Partiel |
| `/status`      | État du système               | ✅ |

### 3.7 Infrastructure & Observabilité

| Fonctionnalité      | Implémentation                     | Statut |
|--------------------|------------------------------------|--------|
| Health Check        | `GET /health`                     | ✅ |
| Métriques Prometheus| `axum-prometheus` middleware      | ✅ |
| CORS                | Configurable (permissif/whitelist)| ✅ |
| Rate Limiting       | Concurrency limit configurable    | ✅ |
| Logging structuré   | `tracing` avec niveaux env        | ✅ |
| Cache Redis         | Proxy TMDB avec TTL               | ✅ |
| Auth API            | Middleware API key                 | ✅ |
| Events temps réel   | NATS pub/sub + WebSocket          | ✅ |
| Graceful Shutdown   | Signal handler (Ctrl+C / SIGTERM) | ✅ |
| Scheduler           | Nettoyage résultats expirés       | ✅ Minimal |

---

## 4. Fonctionnalités Manquantes / Incomplètes ❌

### 4.1 Priorité Haute

| Fonctionnalité              | Description                                          | Impact |
|----------------------------|------------------------------------------------------|--------|
| **Authentification utilisateur** | Pas de système de login/signup (seulement API key) | Critique — mono-utilisateur actuellement |
| **RealDebrid intégration** | Provider stubé (`#[allow(dead_code)]`)               | Élevé — fonctionnalité premium manquante |
| **Gestion multi-utilisateurs** | Pas de table `users` active ni RBAC              | Élevé — pas de personnalisation |
| **Streaming provider stable** | Dépend de Chromium init (peut échouer)            | Moyen — fallback manquant |

### 4.2 Priorité Moyenne

| Fonctionnalité                | Description                                    |
|------------------------------|------------------------------------------------|
| **Système de migrations DB** | Dossier `migrations/` vide, init.sql uniquement |
| **Tests d'intégration réels** | 19 fichiers de tests mais surtout des mocks   |
| **Metrics Collector Worker** | Défini mais jamais instancié ni publié         |
| **Scheduler enrichi**        | Seulement 1 tâche (cleanup), pas d'archivage  |
| **Validation espace disque** | Hunter télécharge sans vérifier l'espace libre |
| **Commande Telegram `/library`** | Handler incomplet (pas de connexion DB)    |
| **Page paramètres frontend** | Pas de page settings/config dans le dashboard  |

### 4.3 Priorité Basse

| Fonctionnalité              | Description                                    |
|----------------------------|------------------------------------------------|
| **Recommandations IA**      | Oracle score mais pas de recommandations user  |
| **Notifications push**      | WebSocket alertes basiques, pas de push mobile |
| **Sous-titres**             | Pas de gestion/recherche de sous-titres        |
| **Multi-langue frontend**   | Interface en français/anglais hardcodé         |
| **PWA / Mobile app**        | Dashboard web uniquement                       |
| **Backup automatique DB**   | Pas de stratégie de sauvegarde                 |

---

## 5. Variables d'Environnement

### 5.1 Requises (crash si absentes)

| Variable       | Description              |
|---------------|--------------------------|
| `DATABASE_URL` | URL PostgreSQL           |
| `TMDB_API_KEY` | Clé API TMDB (gratuite)  |

### 5.2 Optionnelles (avec défauts)

| Variable               | Défaut                                      | Description                    |
|-----------------------|---------------------------------------------|-------------------------------|
| `REDIS_URL`            | `redis://127.0.0.1:6379`                   | URL Redis                     |
| `NATS_URL`             | `nats://127.0.0.1:4222`                    | URL NATS                      |
| `PORT`                 | `3000`                                      | Port API                      |
| `API_KEY`              | —                                           | Clé d'accès API               |
| `JWT_SECRET`           | —                                           | Secret JWT                    |
| `PROWLARR_URL`         | —                                           | URL Prowlarr                  |
| `PROWLARR_API_KEY`     | —                                           | Clé Prowlarr                  |
| `JACKETT_URL`          | —                                           | URL Jackett                   |
| `JACKETT_API_KEY`      | —                                           | Clé Jackett                   |
| `FLARESOLVERR_URL`     | —                                           | URL FlareSolverr              |
| `REAL_DEBRID_API_KEY`  | —                                           | Clé Real-Debrid               |
| `TELOXIDE_TOKEN`       | —                                           | Token bot Telegram            |
| `TMDB_LANGUAGE`        | `fr-FR`                                     | Langue TMDB                   |
| `TMDB_IMAGE_BASE_URL`  | `https://image.tmdb.org/t/p/`              | Base URL images TMDB          |
| `LLAMA_ENDPOINT`       | —                                           | Endpoint IA (Oracle)          |
| `DOWNLOAD_DIR`         | `./downloads`                               | Dossier téléchargements       |
| `MAX_CONCURRENT_DL`    | `3`                                         | Downloads simultanés max      |
| `LOG_LEVEL`            | `info`                                      | Niveau de log                 |
| `RUN_MIGRATIONS`       | `false`                                     | Exécuter les migrations       |

---

## 6. Schéma Base de Données

```
media ──────────────── media_files
  │                      (1:N)
  ├── search_results   (1:N, cache torrent)
  ├── tasks            (référence implicite)
  ├── favorites        (1:N, par TMDB ID)
  ├── watchlist        (1:N, par TMDB ID)
  └── watch_history    (1:N, progression)
```

**Extensions PostgreSQL :** `uuid-ossp`, `pg_trgm` (recherche fuzzy)

---

## 7. Flux Métier Principaux

### 7.1 Recherche et Téléchargement

```
Utilisateur → [Frontend/Telegram] → POST /search
  → API crée Task → NATS publie "search.request"
  → Scout Worker reçoit → interroge Prowlarr/Jackett
  → Résultats stockés en DB → WebSocket notifie le frontend
  → Utilisateur sélectionne → POST /downloads
  → API crée Task → NATS publie "download.request"
  → Hunter Worker reçoit → librqbit télécharge
  → Progression mise à jour → WebSocket notifie en temps réel
  → Terminé → media_file créé → notification envoyée
```

### 7.2 Navigation et Streaming

```
Utilisateur → Dashboard → Browse/Search TMDB
  → Sélectionne un film/série → Page détails (cast, vidéos, similaires)
  → Bouton "Regarder" → Player vidéo avec liens streaming
  → Progression sauvegardée → "Reprendre" sur page d'accueil
```

### 7.3 Gestion Bibliothèque

```
Utilisateur → Ajoute aux favoris / watchlist
  → Visible dans /library (3 onglets)
  → Historique de visionnage tracké automatiquement
  → "Reprendre" affiché sur la page d'accueil
```

---

## 8. Contraintes Non-Fonctionnelles

| Contrainte          | Cible                                     |
|--------------------|-------------------------------------------|
| Latence API         | < 500ms (cached), < 2s (TMDB call)       |
| Concurrence         | 100+ users simultanés                     |
| Mémoire             | < 500MB RSS (Rust)                        |
| Démarrage           | < 5s (cold start)                         |
| Disponibilité       | Health check + graceful shutdown          |
| Sécurité            | API key auth, parameterized queries       |
| Observabilité       | Prometheus metrics, structured logs       |

---

## 9. Structure du Projet (Après nettoyage)

```
Sokoul/
├── src/                    # Code Rust backend
│   ├── main.rs             # Point d'entrée, router, workers
│   ├── config.rs           # Configuration (env vars)
│   ├── api/                # Handlers REST (search, media, downloads...)
│   ├── clients/            # Clients HTTP externes (TMDB, FlareSolverr)
│   ├── db/                 # Opérations base de données (sqlx)
│   ├── providers/          # Providers de recherche (Prowlarr, Jackett...)
│   ├── telegram/           # Bot Telegram (teloxide)
│   ├── utils/              # Utilitaires partagés
│   └── workers/            # Workers asynchrones (Scout, Hunter, Oracle, Sentinel)
├── dashboard/              # Frontend SvelteKit
│   ├── src/
│   │   ├── routes/         # Pages (/, /films, /series, /search, /library, /movie, /tv, /watch, /person)
│   │   └── lib/
│   │       ├── api/        # Client API TypeScript
│   │       └── components/ # Composants réutilisables
│   ├── static/             # Assets statiques
│   └── package.json        # Dépendances frontend
├── scripts/                # Scripts d'automatisation
│   └── release.sh          # Script de release
├── Logo/                   # Logo SVG
├── Cargo.toml              # Config Rust
├── Cargo.lock              # Lock Rust
├── docker-compose.yml      # Orchestration services
├── Dockerfile              # Build image Sokoul
├── init.sql                # Schéma initial PostgreSQL
├── prometheus.yml          # Config Prometheus
├── setup.sh                # Script setup initial
├── init_db.sh              # Script init DB
├── .env.example            # Template variables d'environnement
├── .gitignore              # Fichiers ignorés
├── README.md               # Documentation principale
└── CAHIER_DES_CHARGES.md   # Ce document
```

---

## 10. Prochaines Étapes (Upgrade)

1. **Authentification** — Système login/signup avec JWT, multi-utilisateurs
2. **RealDebrid** — Finaliser l'intégration du provider debrid
3. **Migrations DB** — Mettre en place un système de migrations (sqlx migrate)
4. **Tests robustes** — Tests d'intégration avec vrais services (testcontainers)
5. **Streaming stable** — Fallback si Chromium échoue
6. **Page settings** — Configuration depuis le frontend
7. **Sous-titres** — Recherche et intégration OpenSubtitles
8. **CI/CD** — Pipeline GitHub Actions complet
9. **Documentation API** — Swagger/OpenAPI auto-généré
10. **PWA** — Support mobile (service worker, manifest)

---

*Document généré automatiquement — Sokoul v2*
