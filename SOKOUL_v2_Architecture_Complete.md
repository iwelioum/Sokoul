# SOKOUL v2 — ARCHITECTURE COMPLÈTE

## Refonte Hyper-Performance sur Orange Pi 6 Plus

---

## 0. PRÉAMBULE : Critique de la Version Gemini

Avant de construire, déconstruisons. La proposition Gemini est séduisante mais contient plusieurs erreurs techniques et choix discutables qu'il faut corriger :

**Ce que Gemini a bien vu :**
- L'approche event-driven avec message queue est la bonne architecture
- Rust comme langage principal est un excellent choix pour l'apprentissage et la performance
- L'intégration directe de librqbit évite la lourdeur d'un client torrent externe
- L'IA locale pour le filtrage intelligent des résultats est une vraie valeur ajoutée

**Ce que Gemini a mal évalué :**

1. **gRPC en interne sur une seule machine = complexité inutile.** Sur un monolithe déployé sur un SBC, les appels internes via channels Tokio (Rust) sont 100x plus rapides que gRPC. gRPC est pertinent pour la communication inter-machines, pas intra-process.

2. **Pingora comme API Gateway est overkill.** C'est le proxy de Cloudflare conçu pour des millions de requêtes/seconde distribuées. Sur un SBC qui sert 1-5 utilisateurs, un simple reverse proxy Caddy (avec auto-TLS) ou directement Axum suffit largement.

3. **DragonflyDB sur ARM = risque de compatibilité.** DragonflyDB est optimisé pour x86. Sur ARMv9, Redis 7+ avec io-threads activé est plus fiable et suffisant pour notre charge.

4. **"50 microsecondes avant accès DB" = marketing.** C'est le temps de traitement CPU pur, mais avec la sérialisation, le parsing, et l'accès réseau, une requête réaliste prend 1-5ms. C'est déjà excellent.

5. **Le Rock 5B+ est abandonné.** Tu passes sur l'Orange Pi 6 Plus qui est une génération au-dessus. Tout doit être recalculé.

6. **L'architecture Gemini ignore totalement le scraping de sites de streaming.** C'est pourtant le cœur de SOKOUL : extraire les liens m3u8/mp4 des sites de streaming, pas seulement gérer des torrents.

---

## 1. LE HARDWARE : Orange Pi 6 Plus — La Bête

L'Orange Pi 6 Plus n'est PAS un simple upgrade du Rock 5B+. C'est un changement de génération complet.

### Comparaison directe

| Spec | Rock 5B+ (RK3588) | Orange Pi 6 Plus (CIX CD8180) | Delta |
|------|-------------------|-------------------------------|-------|
| **CPU** | 4x A76 @ 2.4GHz + 4x A55 @ 1.8GHz | 4x A720 @ 2.8GHz + 4x A720 @ 2.4GHz + 4x A520 @ 1.8GHz | **+50% cœurs, +30% IPC** |
| **Architecture** | ARMv8.2 | **ARMv9.2** | Nouvelle génération |
| **GPU** | Mali-G610 MP4 | **Immortalis-G720 MC10** (Ray Tracing) | **~3x plus puissant** |
| **NPU** | 6 TOPS | **30 TOPS** (45 TOPS combinés) | **5x plus puissant** |
| **RAM** | 32GB LPDDR4X | **32/64GB LPDDR5-5500** (128-bit) | **~2x bande passante** |
| **Stockage** | 1x M.2 NVMe PCIe 3.0 | **2x M.2 NVMe PCIe Gen4** | **~4x débit total** |
| **Réseau** | 1x 2.5GbE | **2x 5GbE** | **4x bande passante** |
| **Vidéo** | 8K@60 decode | 8K@60 decode + **8K@30 encode** | Encode natif |
| **Vulkan** | 1.2 | **1.3** | Meilleur support IA |
| **Process** | 8nm | **6nm** | Plus efficace |

### Ce que ça change concrètement pour SOKOUL

- **30 TOPS NPU** : On peut faire tourner des modèles IA de 3-7B paramètres avec une vraie accélération matérielle, pas juste du CPU brut
- **2x NVMe Gen4** : Un SSD pour l'OS/apps, un SSD dédié au cache média. Lectures à 7 GB/s
- **2x 5GbE** : Séparation réseau possible — un port pour le LAN/Internet, un port dédié au streaming vers la TV
- **12 cœurs** : Le scraping parallèle avec Playwright peut utiliser les 4 cœurs A520 pendant que les 8 cœurs A720 gèrent le backend
- **Immortalis-G720** : Vulkan 1.3 = meilleur support pour llama.cpp GPU, et décodage vidéo hardware natif
- **64GB RAM option** : Assez pour faire tourner un LLM 7B quantifié + tout le stack + cache en RAM

### ⚠️ Point de vigilance critique

Le CIX CD8180 est un SoC NOUVEAU (fin 2024). Contrairement au RK3588 qui a 3 ans d'écosystème :

- **Le kernel Linux est en cours d'upstreaming** — il faut utiliser l'image Debian/Ubuntu d'Orange Pi
- **Le SDK NPU (NOE SDK) vient d'être documenté** (décembre 2025) — l'écosystème est encore jeune
- **RKNN ne fonctionne PAS** sur ce SoC — c'est un CIX, pas un Rockchip
- **llama.cpp via Vulkan devrait fonctionner** sur le GPU Immortalis-G720 (testé sur Orion O6)
- **Idle power ~16-17W** — plus élevé qu'un RK3588 (~5W), prévoir une alimentation 100W USB-C

**Stratégie recommandée** : Commencer avec Vulkan GPU pour l'IA (mature), puis migrer vers le NPU via NOE SDK quand l'écosystème sera plus stable (Q2-Q3 2026).

---

## 2. PHILOSOPHIE : "Pragmatic Performance"

On ne cherche pas la performance théorique maximale. On cherche la **performance utile maximale** — celle qui se ressent dans l'expérience utilisateur.

### Principes fondateurs

1. **Monolithe modulaire, pas microservices.** Un seul binaire Rust avec des modules internes communiquant via channels Tokio. Zéro overhead réseau interne.

2. **Event-driven pour le découplage.** NATS JetStream pour les tâches asynchrones (scraping, download, IA). Le core ne bloque jamais.

3. **Progressive enhancement.** Le système fonctionne sans IA (mode basique), puis s'améliore quand l'IA est disponible. Pas de dépendance dure.

4. **Offline-first.** Tout fonctionne sans Internet une fois le contenu en cache. La base de données locale est la source de vérité.

5. **Observable par défaut.** Chaque composant expose des métriques. Tu vois TOUT ce qui se passe en temps réel.

---

## 3. STACK TECHNOLOGIQUE — Choix Justifiés

### Core Stack

| Composant | Technologie | Justification |
|-----------|-------------|---------------|
| **Runtime** | **Rust + Tokio** | Async natif, zero-cost abstractions, mémoire sûre. Un seul binaire ~15-25MB. |
| **Web Framework** | **Axum** | Le plus ergonomique de l'écosystème Rust. Tower middleware ecosystem. Directement Tokio-native. |
| **Database** | **PostgreSQL 16** | JSONB pour la flexibilité, full-text search intégré, extensions (pg_trgm pour fuzzy matching). Configuré pour NVMe. |
| **Cache** | **Redis 7.2** (avec io-threads) | Fiable sur ARM, compatible avec tout l'écosystème. io-threads=4 pour exploiter le multi-cœur. |
| **Message Queue** | **NATS JetStream** | Latence sub-ms, persistence, replay. Binaire unique, ARM natif, 5MB RAM. |
| **Torrent** | **Librqbit** (Rust natif) | Intégré dans le binaire. Pas de processus externe. Sequential downloading natif. |
| **Scraping** | **Playwright** (piloté par Rust via HTTP CDP) | Nécessaire pour les sites protégés par Cloudflare/JS. Chrome headless avec interception réseau. |
| **Cloudflare Bypass** | **FlareSolverr** | Résout les défis Cloudflare et hCaptcha pour un scraping fiable. |
| **IA locale** | **llama.cpp** (Vulkan GPU → futur NPU) | API OpenAI-compatible. Modèle Phi-3-mini ou Qwen2-1.5B pour le filtrage sémantique. |
| **Reverse Proxy** | **Caddy** | Auto-TLS, config simple, reverse proxy vers Axum. Optionnel si accès local uniquement. |
| **Monitoring** | **Prometheus + Grafana** | Métriques temps réel de tout le système. Dashboards pré-configurés. |
| **Logs** | **Loki** (via Grafana) | Logs centralisés, requêtables, corrélés aux métriques. |

### Frontend Stack

| Composant | Technologie | Justification |
|-----------|-------------|---------------|
| **UI Web** | **SvelteKit** | Plus léger que React, SSR natif, excellent DX. Bundle ~50KB vs ~150KB React. |
| **Real-time** | **WebSocket** (Axum natif) | Updates en temps réel : progression téléchargements, logs, statuts. |
| **Mobile** | **PWA** | Installable sur téléphone, notifications push, fonctionne offline. |

---

## 4. ARCHITECTURE DÉTAILLÉE

### Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ORANGE PI 6 PLUS                                 │
│                                                                         │
│  ┌──────────┐    ┌──────────────────────────────────────────────────┐   │
│  │  Caddy    │───▶│           SOKOUL CORE (Rust/Axum)               │   │
│  │  Proxy    │    │                                                  │   │
│  │  :443     │    │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │   │
│  └──────────┘    │  │ API      │  │ WebSocket│  │ Task         │  │   │
│                   │  │ Routes   │  │ Hub      │  │ Scheduler    │  │   │
│                   │  └────┬─────┘  └────┬─────┘  └──────┬───────┘  │   │
│                   │       │             │               │           │   │
│                   │  ┌────▼─────────────▼───────────────▼───────┐  │   │
│                   │  │          Event Bus (Tokio Channels)       │  │   │
│                   │  └──┬──────────┬──────────┬─────────────────┘  │   │
│                   │     │          │          │                     │   │
│                   └─────┼──────────┼──────────┼─────────────────────┘   │
│                         │          │          │                         │
│  ┌──────────────────────┼──────────┼──────────┼─────────────────────┐  │
│  │        NATS JetStream (Async Job Queue)                          │  │
│  └──────────┬───────────┬──────────┬──────────┬─────────────────────┘  │
│             │           │          │          │                         │
│  ┌──────────▼──┐ ┌──────▼────┐ ┌──▼───────┐ ┌▼──────────────┐        │
│  │  WORKER     │ │ WORKER    │ │ WORKER   │ │ WORKER         │        │
│  │  Scout      │ │ Hunter    │ │ Oracle   │ │ Sentinel       │        │
│  │ (Scraping)  │ │ (Torrent) │ │ (IA)     │ │ (Monitoring)   │        │
│  └──────┬──────┘ └─────┬─────┘ └────┬─────┘ └───────┬────────┘        │
│         │              │            │                │                  │
│  ┌──────▼──────┐       │     ┌──────▼──────┐  ┌─────▼──────────┐      │
│  │ Playwright  │       │     │ llama.cpp   │  │ Prometheus     │      │
│  │ (Chrome)    │       │     │ (Vulkan GPU)│  │ + Grafana      │      │
│  └─────────────┘       │     └─────────────┘  │ + Loki         │      │
│                        │                       └────────────────┘      │
│  ┌─────────────────────┼──────────────────────────────────────────┐    │
│  │              DATA LAYER                                        │    │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────────────────┐   │    │
│  │  │PostgreSQL│  │ Redis    │  │ NVMe #1: OS + Apps         │   │    │
│  │  │ (NVMe#1) │  │ (RAM)   │  │ NVMe #2: Media + Cache     │   │    │
│  │  └──────────┘  └──────────┘  └────────────────────────────┘   │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌────────────┐  ┌────────────┐                                        │
│  │ ETH0: LAN  │  │ ETH1: IPTV │  ← Dual 5GbE séparation réseau       │
│  │ (Internet) │  │ (Streaming)│                                        │
│  └────────────┘  └────────────┘                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### A. SOKOUL Core — Le Cerveau

Un seul binaire Rust compilé. Pas de conteneur, pas de VM. Direct sur le métal.

**Modules internes :**

- **API Router** : Routes REST (compatible OpenAPI) pour le frontend et les clients externes. Axum avec extractors typés.
- **WebSocket Hub** : Broadcast en temps réel vers tous les clients connectés. Progression des downloads, logs, changements d'état.
- **Task Scheduler** : Planification des tâches récurrentes (mise à jour des métadonnées, nettoyage cache, health checks).
- **State Manager** : Source de vérité en mémoire, synchronisée avec PostgreSQL. Pattern CQRS simplifié.

**Communication interne** : Tokio `mpsc` et `broadcast` channels. Zéro sérialisation, zéro copie. Un message interne prend ~50 nanosecondes.

### B. Les Workers — Les Muscles

Chaque worker est un consumer NATS qui tourne dans son propre thread pool Tokio.

#### Worker "Scout" — Scraping & Recherche

Le cœur de SOKOUL. Ce worker sait trouver du contenu sur Internet.

**Capacités :**
    - Scraping de sites de streaming (extraction m3u8/mp4 via interception réseau CDP, **avec FlareSolverr pour bypasser Cloudflare**)
    - Recherche via API Prowlarr/Jackett (indexers torrent, **avec FlareSolverr si besoin**)- Recherche directe TMDB/OMDB pour les métadonnées
- Scraping de sous-titres (OpenSubtitles, Addic7ed)

**Architecture interne :**
```
Scout Worker
├── ProviderRegistry (liste dynamique de sources)
│   ├── StreamingProvider (Playwright + CDP intercept)
│   ├── TorrentProvider (Prowlarr API)
│   ├── DirectLinkProvider (cyberlockers, hosters)
│   └── SubtitleProvider (OpenSubtitles API)
├── ResultAggregator (merge + deduplicate)
├── QualityScorer (résolution, codec, taille → score 0-100)
└── CacheLayer (Redis — résultats mis en cache 24h)
```

**Optimisation Playwright :**
- Interception réseau bas niveau via CDP pour capturer les requêtes m3u8 sans charger les images/pubs
- Pool de 2-3 contextes browser réutilisables (pas de nouveau launch à chaque requête)
- Blocage des domaines publicitaires via `page.route()` → gain de temps ~80%
- Exécution sur les 4 cœurs A520 (basse conso) pendant que les A720 gèrent le reste

#### Worker "Hunter" — Gestion Torrent

**Capacités :**
- Téléchargement torrent intégré via librqbit (Rust natif)
- Sequential downloading (priorité début de fichier pour le streaming)
- Gestion intelligente de la bande passante
- Seeding automatique avec ratio configurable

**Optimisation :**
- Zero-copy networking : les données vont du socket au disque via `sendfile`/`splice`
- Pre-allocation des fichiers sur NVMe #2
- Monitoring par pair : vitesse, progression, santé du swarm

#### Worker "Oracle" — Intelligence Artificielle

**Capacités :**
- Validation sémantique des résultats de recherche (le titre correspond-il à la demande ?)
- Détection des fakes/cam-rips via analyse du nom de fichier
- Recommandations personnalisées basées sur l'historique
- Extraction d'entités (acteur, réalisateur, genre) depuis du texte brut
- Résumés automatiques de films/séries

**Stack IA :**
```
Oracle Worker
├── llama.cpp server (API OpenAI-compatible)
│   ├── Modèle primaire : Qwen2.5-3B-Instruct (Q4_K_M) — ~2GB RAM
│   │   └── Vulkan GPU (Immortalis-G720)
│   └── Modèle futur : migration NPU via CIX NOE SDK (Q2 2026)
├── Structured Output (JSON via GBNF grammar)
├── Embedding Engine (all-MiniLM-L6-v2 quantifié)
│   └── Pour la recherche sémantique dans la bibliothèque locale
└── Fallback : regex + heuristiques si LLM indisponible
```

**Pourquoi Qwen2.5-3B et pas Llama-3-8B ?**
- 3B paramètres = ~2GB RAM en Q4, laisse de la marge pour tout le reste
- Suffisant pour du classification/extraction/validation (pas besoin de génération créative)
- Inference ~10-15 tokens/s via Vulkan sur Immortalis-G720 = acceptable pour notre usage
- Llama-3-8B prendrait ~5GB et serait 2-3x plus lent sans gain significatif pour nos tâches

#### Worker "Sentinel" — Observabilité Totale

C'est le worker qui te donne la "visibilité absolue" que tu demandes.

**Capacités :**
- Export métriques vers Prometheus (CPU, RAM, disque, réseau par composant)
- Collecte des logs structurés vers Loki
- Health checks de tous les services (PostgreSQL, Redis, NATS, llama.cpp, Playwright)
- Alertes (Telegram bot) en cas de problème
- Dashboard Grafana pré-configuré

**Métriques exposées :**
```
sokoul_search_duration_seconds        — Temps de recherche par provider
sokoul_download_speed_bytes           — Vitesse de téléchargement en temps réel
sokoul_download_progress_ratio        — Progression 0.0 → 1.0
sokoul_stream_buffer_seconds          — Buffer disponible avant lecture
sokoul_ai_inference_duration_seconds  — Temps de réponse du LLM
sokoul_scraping_success_rate          — Taux de succès par site
sokoul_cache_hit_ratio                — Efficacité du cache Redis
sokoul_system_cpu_usage               — Usage CPU par cœur
sokoul_system_memory_usage            — Usage RAM par composant
sokoul_system_disk_io                 — I/O par NVMe
sokoul_system_network_throughput      — Débit par interface réseau
sokoul_nats_queue_depth               — Taille des queues NATS
sokoul_torrent_peers_connected        — Nombre de pairs par torrent
sokoul_torrent_seed_ratio             — Ratio de seed
sokoul_playwright_pool_usage          — Utilisation du pool de browsers
```

---

## 5. FONCTIONNALITÉS ÉTENDUES — Au-delà du Média Center

SOKOUL n'est pas un simple lecteur. C'est une plateforme d'automatisation média intelligente.

### 5.1 Recherche Unifiée Multi-Sources

Une seule requête → recherche simultanée sur :
- Sites de streaming (scraping Playwright)
- Indexers torrent (via Prowlarr)
- Liens directs (hosters comme 1fichier, Rapidgator)
- Cache local (PostgreSQL + Redis)

Résultats fusionnés, dédupliqués, et scorés par qualité.

### 5.2 Streaming Intelligent

- **Torrent streaming** : Téléchargement séquentiel avec librqbit. Dès que 2% est bufferisé, le stream démarre.
- **Direct streaming** : Proxy des liens m3u8/mp4 via Axum (masque l'IP source, ajoute le range-request).
- **Transcoding adaptatif** : Si le fichier n'est pas compatible avec la TV, transcoding à la volée via FFmpeg avec accélération hardware VPU (8K@60 decode natif sur CIX CD8180).
- **Sous-titres automatiques** : Recherche et synchronisation automatique des sous-titres (OpenSubtitles + embedded).

### 5.3 Bibliothèque & Métadonnées

- Scan automatique des fichiers média locaux
- Enrichissement TMDB/OMDB (poster, synopsis, casting, note)
- Organisation automatique des fichiers (renommage, dossiers par série/saison)
- Recherche full-text dans la bibliothèque (PostgreSQL pg_trgm)
- Recherche sémantique via embeddings locaux ("films comme Inception" → trouve des thrillers sci-fi similaires)

### 5.4 Automatisation & Scheduling

- **Suivi de séries** : Détection automatique de nouveaux épisodes, téléchargement programmé
- **Watchlist** : Ajout d'un film → SOKOUL surveille sa disponibilité et télécharge dès qu'il sort
- **Règles de qualité** : "Je veux du 1080p minimum, HEVC préféré, pas de cam-rip"
- **Nettoyage automatique** : Suppression des fichiers regardés après X jours (configurable)
- **Backup métadonnées** : Export/import de la bibliothèque (JSON)

### 5.5 Interface Telegram Bot (héritage SOKOUL v1)

Le bot Telegram reste le moyen le plus rapide d'interagir :
- `/search Dune 2` → résultats avec boutons inline
- `/download` → lance le téléchargement du résultat sélectionné
- `/status` → progression en temps réel
- `/library` → parcourir la bibliothèque
- `/recommend` → recommandations IA basées sur l'historique
- Notifications push quand un téléchargement est terminé ou un nouvel épisode est disponible

### 5.6 Interface Web (Dashboard)

Dashboard SvelteKit accessible depuis n'importe quel appareil :

- **Home** : Films/séries récents, en cours de téléchargement, recommandations
- **Search** : Recherche unifiée avec filtres (qualité, source, langue)
- **Library** : Vue grille/liste de toute la bibliothèque avec métadonnées
- **Downloads** : Progression temps réel, vitesse, ETA, graphiques
- **Player** : Lecteur vidéo intégré avec sous-titres et sélection de piste audio
- **Settings** : Configuration des providers, qualité par défaut, scheduling
- **System** : Métriques système embarquées (mini-Grafana)
- **Logs** : Vue temps réel des logs (WebSocket, défilement façon terminal)

### 5.7 DLNA/UPnP & Chromecast

- Serveur DLNA pour diffuser vers les Smart TVs directement
- Support Chromecast pour caster depuis le dashboard web
- Découverte automatique des appareils sur le réseau local

### 5.8 VPN & Sécurité

- Client WireGuard intégré (optionnel) pour anonymiser le trafic torrent
- Split tunneling : seul le trafic torrent passe par le VPN, le reste en direct
- Chiffrement de la base de données locale (optionnel)
- Authentification sur l'interface web (JWT)

---

## 6. MODÈLE DE DONNÉES

### Schéma PostgreSQL principal

```sql
-- Contenu média
CREATE TABLE media (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_type      TEXT NOT NULL CHECK (media_type IN ('movie', 'series', 'episode')),
    title           TEXT NOT NULL,
    original_title  TEXT,
    year            INTEGER,
    tmdb_id         INTEGER UNIQUE,
    imdb_id         TEXT UNIQUE,
    overview        TEXT,
    poster_url      TEXT,
    backdrop_url    TEXT,
    genres          TEXT[],
    rating          DECIMAL(3,1),
    runtime_minutes INTEGER,
    status          TEXT DEFAULT 'unknown',
    parent_id       UUID REFERENCES media(id),  -- episode → series
    season_number   INTEGER,
    episode_number  INTEGER,
    embedding       vector(384),  -- pgvector pour recherche sémantique
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Fichiers média locaux
CREATE TABLE media_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID REFERENCES media(id) ON DELETE CASCADE,
    file_path       TEXT NOT NULL UNIQUE,
    file_size       BIGINT,
    codec_video     TEXT,
    codec_audio     TEXT,
    resolution      TEXT,
    quality_score   INTEGER CHECK (quality_score BETWEEN 0 AND 100),
    hash_info       TEXT,  -- info hash torrent
    source          TEXT,  -- 'torrent', 'streaming', 'direct', 'local'
    downloaded_at   TIMESTAMPTZ DEFAULT now()
);

-- Résultats de recherche (cache)
CREATE TABLE search_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query           TEXT NOT NULL,
    provider        TEXT NOT NULL,
    title           TEXT NOT NULL,
    url             TEXT,
    magnet_link     TEXT,
    quality         TEXT,
    size_bytes      BIGINT,
    seeders         INTEGER,
    leechers        INTEGER,
    score           INTEGER CHECK (score BETWEEN 0 AND 100),
    ai_validated    BOOLEAN DEFAULT FALSE,
    cached_at       TIMESTAMPTZ DEFAULT now(),
    expires_at      TIMESTAMPTZ DEFAULT now() + INTERVAL '24 hours'
);

-- Historique utilisateur
CREATE TABLE watch_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID REFERENCES media(id),
    watched_at      TIMESTAMPTZ DEFAULT now(),
    progress        DECIMAL(5,2) DEFAULT 0,  -- pourcentage regardé
    completed       BOOLEAN DEFAULT FALSE
);

-- Watchlist
CREATE TABLE watchlist (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID REFERENCES media(id),
    added_at        TIMESTAMPTZ DEFAULT now(),
    auto_download   BOOLEAN DEFAULT TRUE,
    quality_min     TEXT DEFAULT '1080p',
    notified        BOOLEAN DEFAULT FALSE
);

-- Suivi séries
CREATE TABLE series_tracking (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id       UUID REFERENCES media(id),
    last_checked    TIMESTAMPTZ,
    next_episode    INTEGER,
    next_season     INTEGER,
    active          BOOLEAN DEFAULT TRUE
);

-- Jobs/Tasks
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       TEXT NOT NULL,
    status          TEXT DEFAULT 'pending',
    payload         JSONB,
    result          JSONB,
    progress        DECIMAL(5,2) DEFAULT 0,
    error           TEXT,
    created_at      TIMESTAMPTZ DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

-- Configuration
CREATE TABLE config (
    key             TEXT PRIMARY KEY,
    value           JSONB NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Index optimisés
CREATE INDEX idx_media_tmdb ON media(tmdb_id);
CREATE INDEX idx_media_type ON media(media_type);
CREATE INDEX idx_media_title_trgm ON media USING gin(title gin_trgm_ops);
CREATE INDEX idx_media_embedding ON media USING ivfflat(embedding vector_cosine_ops);
CREATE INDEX idx_search_results_query ON search_results(query);
CREATE INDEX idx_search_results_expires ON search_results(expires_at);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_media_files_media ON media_files(media_id);
```

---

## 7. FLUX DE DONNÉES — Cas d'usage concret

### Scénario : "Dune 2" demandé via Telegram

```
TEMPS    ACTION
─────    ──────
  0ms    User envoie "/search Dune 2" sur Telegram
 50ms    Core reçoit le message, vérifie le cache Redis
         → CACHE MISS
100ms    Core publie evt.search.requested dans NATS
         Payload: { query: "Dune 2", type: "movie", quality: "1080p+" }

150ms    Scout Worker attrape l'événement
         → Lance 4 recherches PARALLÈLES :
           1. TMDB API → identifie "Dune: Part Two (2024)" tmdb_id=693134
           2. Prowlarr → 15 résultats torrent
           3. Streaming Provider A → 3 liens m3u8
           4. Streaming Provider B → 2 liens mp4

800ms    Scout agrège les résultats (20 résultats bruts)
         → Publie evt.search.results dans NATS

850ms    Oracle Worker attrape les résultats
         → Envoie au LLM local :
           "Parmi ces résultats, lesquels correspondent à
            'Dune: Part Two (2024)' et pas 'Dune (1984)' ?"
         → LLM analyse les titres, tailles, années

1.5s     Oracle retourne les résultats validés + scorés
         → Publie evt.search.validated dans NATS

1.6s     Core reçoit les résultats validés
         → Cache dans Redis (TTL 24h)
         → Sauvegarde dans PostgreSQL
         → Push WebSocket vers le dashboard
         → Envoie les résultats au bot Telegram avec boutons inline

1.7s     User voit les résultats sur Telegram :
         🎬 Dune: Part Two (2024)
         ├── [TORRENT] 1080p HEVC - 4.2GB - 150 seeds ⭐95
         ├── [STREAM]  1080p H264 - Direct Play          ⭐88
         ├── [TORRENT] 2160p HDR  - 18GB - 45 seeds      ⭐82
         └── [STREAM]  720p  H264 - Backup                ⭐60

         User clique sur le premier résultat

2.0s     Core publie evt.download.requested dans NATS

2.1s     Hunter Worker lance le téléchargement torrent
         → librqbit : mode séquentiel (début du fichier en premier)
         → Pre-alloc sur NVMe #2

2.1s+    Sentinel Worker commence le monitoring
         → Métriques de download poussées chaque seconde
         → WebSocket broadcast vers le dashboard
         → Messages Telegram de progression

~45s     2% du fichier bufferisé (~85MB sur 4.2GB)
         → Core génère le lien de stream
         → Notification Telegram : "🎬 Dune 2 prêt à regarder !"
         → Dashboard affiche le bouton Play

~3min    Téléchargement complet en arrière-plan
         → Métadonnées TMDB enrichies et sauvegardées
         → Sous-titres téléchargés automatiquement
         → Fichier renommé et rangé dans la bibliothèque
         → Disponible en DLNA sur la Smart TV
```

---

## 8. STRATÉGIE HARDWARE — Double NVMe & Double 5GbE

### Configuration NVMe

| SSD | Contenu | Taille recommandée |
|-----|---------|-------------------|
| **NVMe #1** | OS (Debian), PostgreSQL, Redis dump, Apps, Docker images | 256GB - 512GB |
| **NVMe #2** | Fichiers média, cache torrent, fichiers temporaires scraping | 1TB - 2TB |

**Pourquoi séparer ?**
- Les I/O de la DB et des apps n'interfèrent pas avec les écritures torrent massives
- Si le NVMe #2 tombe en panne, le système reste fonctionnel
- Le NVMe #2 peut être facilement upgradé/remplacé

### Configuration Réseau

| Interface | Rôle | Configuration |
|-----------|------|---------------|
| **ETH0** | Internet + LAN | DHCP ou IP fixe sur le réseau principal. Tout le trafic Internet passe par là. |
| **ETH1** | Streaming dédié | IP fixe sur un sous-réseau dédié (ex: 10.0.1.0/24). La TV et les appareils de lecture s'y connectent. |

**Avantage** : Le streaming 4K vers la TV (25-50 Mbps) ne consomme pas la bande passante du port Internet. Les téléchargements torrent saturent ETH0 sans affecter la lecture.

---

## 9. PLAN DE DÉVELOPPEMENT — Sprints Réalistes

### Phase 0 : Setup (1-2 jours)

**Objectif** : Environnement de développement fonctionnel.

- [ ] Installer Debian Bookworm sur l'Orange Pi 6 Plus (image officielle)
- [ ] Configurer SSH, réseau (double NVMe si disponible)
- [ ] Installer Rust (rustup), Docker, docker-compose
- [ ] Créer le repo Git SOKOUL v2
- [ ] Structure du projet Cargo workspace

```
sokoul/
├── Cargo.toml              (workspace)
├── docker-compose.yml
├── sokoul-core/            (binaire principal)
│   ├── Cargo.toml
│   └── src/
│       ├── main.rs
│       ├── api/            (routes Axum)
│       ├── db/             (queries PostgreSQL via sqlx)
│       ├── cache/          (client Redis)
│       ├── events/         (NATS publisher/subscriber)
│       ├── workers/        (Scout, Hunter, Oracle, Sentinel)
│       ├── models/         (structs Rust)
│       └── config/         (configuration)
├── sokoul-web/             (frontend SvelteKit)
├── sokoul-bot/             (Telegram bot — peut être dans core)
├── migrations/             (SQL migrations)
├── grafana/                (dashboards JSON)
└── scripts/                (setup, deploy)
```

### Sprint 1 : Iron Foundation (1-2 semaines)

**Objectif** : API fonctionnelle avec base de données.

- [ ] Docker Compose : PostgreSQL 16 + Redis 7 + NATS
- [ ] Axum server avec health check endpoint
- [ ] SQLx migrations (schéma complet)
- [ ] CRUD basique pour media, search_results
- [ ] Tests d'intégration
- [ ] **Résultat** : `curl http://localhost:3000/health` → `{"status": "ok"}`

### Sprint 2 : Neural Link (1-2 semaines)

**Objectif** : IA locale fonctionnelle.

- [ ] Installer llama.cpp compilé avec `-DGGML_VULKAN=1` pour Immortalis-G720
- [ ] Télécharger Qwen2.5-3B-Instruct (Q4_K_M)
- [ ] API wrapper Rust pour appeler llama.cpp server
- [ ] Prompt engineering pour validation de résultats de recherche
- [ ] Structured output via GBNF grammar
- [ ] Benchmark : mesurer tokens/s, latence, RAM usage
- [ ] **Résultat** : Le LLM valide correctement "Dune: Part Two (2024)" vs "Dune (1984)"

### Sprint 3 : The Scavenger (2-3 semaines)

**Objectif** : Recherche et téléchargement fonctionnels.

- [ ] Intégration Prowlarr API (recherche torrent)
- [ ] Intégration TMDB API (métadonnées)
- [ ] Intégration librqbit (téléchargement torrent natif Rust)
- [ ] Playwright setup pour scraping streaming
- [ ] Pipeline complet : recherche → validation IA → téléchargement
- [ ] NATS event flow complet
- [ ] **Résultat** : Rechercher "Dune 2" → résultats validés → téléchargement lancé

### Sprint 4 : Telegram Reborn (1 semaine)

**Objectif** : Bot Telegram fonctionnel.

- [ ] teloxide (Rust) ou grammY (si plus simple via HTTP)
- [ ] Commandes : /search, /download, /status, /library
- [ ] Boutons inline pour la sélection de résultats
- [ ] Notifications de progression
- [ ] **Résultat** : Interaction complète via Telegram

### Sprint 5 : The Dashboard (2-3 semaines)

**Objectif** : Interface web complète.

- [ ] SvelteKit app avec routing
- [ ] Pages : Home, Search, Library, Downloads, Player, Settings
- [ ] WebSocket pour les updates temps réel
- [ ] Lecteur vidéo intégré (Video.js ou Plyr)
- [ ] PWA manifest pour installation mobile
- [ ] **Résultat** : Dashboard fonctionnel accessible depuis le navigateur

### Sprint 6 : Sentinel Mode (1 semaine)

**Objectif** : Observabilité complète.

- [ ] Prometheus metrics export depuis Axum
- [ ] Grafana dashboards pré-configurés
- [ ] Loki pour les logs structurés
- [ ] Alertes Telegram (disk full, service down, etc.)
- [ ] **Résultat** : Visibilité absolue sur tout le système

### Sprint 7 : Polish & Extend (ongoing)

- [ ] DLNA/UPnP server
- [ ] Chromecast support
- [ ] WireGuard VPN intégré
- [ ] Recherche sémantique (pgvector + embeddings)
- [ ] Suivi automatique des séries
- [ ] Watchlist avec auto-download
- [ ] Transcoding hardware via VPU
- [ ] Migration NPU (quand CIX NOE SDK sera mature)

---

## 10. CONFIGURATION DOCKER COMPOSE

```yaml
version: "3.9"

services:
  postgres:
    image: pgvector/pgvector:pg16
    restart: unless-stopped
    environment:
      POSTGRES_DB: sokoul
      POSTGRES_USER: sokoul
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    command: >
      postgres
        -c shared_buffers=2GB
        -c effective_cache_size=6GB
        -c work_mem=64MB
        -c maintenance_work_mem=512MB
        -c random_page_cost=1.1
        -c effective_io_concurrency=200
        -c max_worker_processes=12
        -c max_parallel_workers_per_gather=4
        -c max_parallel_workers=8
        -c wal_buffers=64MB
        -c checkpoint_completion_target=0.9

  redis:
    image: redis:7.2-alpine
    restart: unless-stopped
    command: >
      redis-server
        --maxmemory 2gb
        --maxmemory-policy allkeys-lru
        --io-threads 4
        --io-threads-do-reads yes
        --save 300 100
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"

  nats:
    image: nats:2.10-alpine
    restart: unless-stopped
    command: >
      -js
      -sd /data
      -m 8222
    volumes:
      - nats_data:/data
    ports:
      - "4222:4222"
      - "8222:8222"  # monitoring

  flaresolverr:
    image: ghcr.io/flaresolverr/flaresolverr:latest
    container_name: flaresolverr
    restart: unless-stopped
    environment:
      LOG_LEVEL: info # Adjust to "debug" for more verbose logging
    ports:
      - "8191:8191"

  prometheus:
    image: prom/prometheus:latest
    restart: unless-stopped
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prom_data:/prometheus
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    restart: unless-stopped
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
    ports:
      - "3001:3000"

  loki:
    image: grafana/loki:latest
    restart: unless-stopped
    volumes:
      - loki_data:/loki
    ports:
      - "3100:3100"

  # SOKOUL Core tourne en natif, pas dans Docker
  # llama.cpp tourne en natif pour accéder au GPU

volumes:
  pg_data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /mnt/nvme1/docker/postgres  # NVMe #1
  redis_data:
  nats_data:
  prom_data:
  grafana_data:
  loki_data:
```

**Note** : SOKOUL Core et llama.cpp tournent en **natif** (pas dans Docker) pour accéder directement au GPU/NPU et éviter l'overhead de virtualisation.

---

## 11. ESTIMATION DES RESSOURCES

### Utilisation RAM (config 32GB)

| Composant | RAM estimée |
|-----------|-------------|
| OS + System | ~1 GB |
| PostgreSQL | ~3 GB (shared_buffers + cache) |
| Redis | ~2 GB (maxmemory) |
| NATS JetStream | ~200 MB |
| SOKOUL Core (Rust) | ~50-100 MB |
| Playwright (2 contextes) | ~500 MB |
| llama.cpp (Qwen2.5-3B Q4) | ~2.5 GB |
| Prometheus + Grafana + Loki | ~500 MB |
| **Total actif** | **~10 GB** |
| **Disponible pour cache OS/fichiers** | **~22 GB** |

Avec 32GB, tu as une marge confortable. Les 22GB restants servent de page cache Linux, accélérant les lectures disque fréquentes.

Avec 64GB, tu pourrais monter à un modèle 7B et augmenter le cache PostgreSQL.

### Utilisation CPU (12 cœurs)

| Composant | Cœurs ciblés | Usage typique |
|-----------|-------------|---------------|
| SOKOUL Core | A720 (big) x2 | < 5% idle, pics à 30% |
| PostgreSQL | A720 (big) x2 | < 10% idle, pics à 50% |
| Redis | A720 (mid) x1 | < 2% |
| NATS | A720 (mid) x1 | < 1% |
| Playwright | A520 (little) x2-4 | 30-80% pendant le scraping |
| llama.cpp | A720 (big+mid) x4-8 | 90%+ pendant l'inférence |
| Monitoring | A520 (little) x1 | < 2% |

L'inférence IA est le seul moment où le CPU est vraiment sollicité. Le reste du temps, le système est quasi-idle.

---

## 12. KPI & OBJECTIFS DE PERFORMANCE

| Métrique | Objectif | Mesure |
|----------|---------|---------|
| Temps de recherche (cache hit) | < 5 ms | Redis → API → Client |
| Temps de recherche (cache miss) | < 3 s | Scraping parallèle + IA validation |
| Démarrage du stream (torrent) | < 60 s | Temps entre clic et lecture |
| Démarrage du stream (direct) | < 2 s | Proxy m3u8/mp4 |
| Inférence IA (validation) | < 2 s | Prompt → JSON structuré |
| Uptime | > 99.5% | Monitoring Prometheus |
| Dashboard latence | < 100 ms | Time to interactive |
| WebSocket latency | < 50 ms | Event → affichage client |

---

## 13. ROADMAP LONG TERME

### Q1 2026 — Foundation
- Sprints 0-3 : Core fonctionnel avec recherche + téléchargement + IA

### Q2 2026 — Experience
- Sprints 4-6 : Bot Telegram + Dashboard + Monitoring
- Début exploration NPU via CIX NOE SDK

### Q3 2026 — Intelligence
- Recommandations personnalisées
- Recherche sémantique
- Suivi automatique des séries
- Migration partielle vers NPU

### Q4 2026 — Ecosystem
- DLNA/UPnP + Chromecast
- VPN intégré
- Multi-utilisateur (profils)
- API publique pour intégrations tierces

---

## 14. VERDICT FINAL

### Ce que Gemini avait de bien et qu'on garde
- Rust comme langage principal
- Architecture event-driven
- librqbit pour les torrents
- IA locale pour le filtrage

### Ce qu'on a corrigé
- Pas de gRPC interne (Tokio channels)
- Pas de Pingora (Caddy ou Axum direct)
- Redis au lieu de DragonflyDB (fiabilité ARM)
- Qwen2.5-3B au lieu de Llama-3-8B (ratio perf/RAM optimal)
- Adaptation complète pour le CIX CD8180 (pas de RKNN)

### Ce qu'on a ajouté
- Scraping de sites de streaming (le vrai cœur de SOKOUL)
- Observabilité complète (Prometheus + Grafana + Loki)
- Bot Telegram (héritage v1)
- DLNA/Chromecast
- Recherche sémantique (pgvector)
- Architecture NVMe double + réseau double
- Plan de développement réaliste avec sprints concrets
- Estimation détaillée des ressources
- KPI mesurables

### Ce que ça va t'apporter professionnellement
1. **Rust** — Le langage le plus demandé en backend haute performance
2. **Architecture event-driven** — Pattern utilisé chez Netflix, Uber, Spotify
3. **Edge AI** — Compétence rare : déployer de l'IA sur du hardware contraint
4. **Observabilité** — Prometheus/Grafana est le standard industrie
5. **System design** — Concevoir un système complet de A à Z
6. **DevOps** — Docker, CI/CD, monitoring, alerting

---

*Document généré le 11 février 2026 — SOKOUL v2 Hyper-Performance Edition*
*Hardware cible : Orange Pi 6 Plus (CIX CD8180, 32-64GB, Dual NVMe, Dual 5GbE)*
