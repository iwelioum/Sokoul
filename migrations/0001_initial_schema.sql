-- SOKOUL v2 — Schema PostgreSQL complet
-- Extensions requises
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Table des utilisateurs
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        TEXT NOT NULL UNIQUE,
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin', 'moderator')),
    avatar_url      TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table des médias (Films/Séries/Episodes)
CREATE TABLE IF NOT EXISTS media (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_type      TEXT NOT NULL CHECK (media_type IN ('movie', 'tv', 'episode')),
    title           TEXT NOT NULL,
    original_title  TEXT,
    year            INTEGER,
    tmdb_id         INTEGER,
    imdb_id         TEXT,
    overview        TEXT,
    poster_url      TEXT,
    backdrop_url    TEXT,
    genres          TEXT[],
    rating          DECIMAL(3,1),
    runtime_minutes INTEGER,
    status          TEXT DEFAULT 'unknown',
    parent_id       UUID REFERENCES media(id) ON DELETE CASCADE,
    season_number   INTEGER,
    episode_number  INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tmdb_id, media_type)
);

-- Fichiers média locaux
CREATE TABLE IF NOT EXISTS media_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    file_path       TEXT NOT NULL UNIQUE,
    file_size       BIGINT,
    codec_video     TEXT,
    codec_audio     TEXT,
    resolution      TEXT,
    quality_score   INTEGER CHECK (quality_score BETWEEN 0 AND 100),
    hash_info       TEXT,
    source          TEXT CHECK (source IN ('torrent', 'streaming', 'direct', 'local')),
    downloaded_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Résultats de recherche (cache de torrents/streams trouvés)
CREATE TABLE IF NOT EXISTS search_results (
    id              SERIAL PRIMARY KEY,
    media_id        UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL,
    title           TEXT NOT NULL,
    guid            TEXT NOT NULL,
    url             TEXT,
    magnet_link     TEXT,
    info_hash       TEXT,
    protocol        TEXT NOT NULL,
    quality         TEXT,
    size_bytes      BIGINT NOT NULL DEFAULT 0,
    seeders         INTEGER NOT NULL DEFAULT 0,
    leechers        INTEGER NOT NULL DEFAULT 0,
    score           INTEGER CHECK (score BETWEEN 0 AND 100),
    ai_validated    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ DEFAULT NOW() + INTERVAL '24 hours',
    UNIQUE (media_id, guid)
);



-- Suivi des séries
CREATE TABLE IF NOT EXISTS series_tracking (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id       UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    last_checked    TIMESTAMPTZ,
    next_episode    INTEGER,
    next_season     INTEGER,
    active          BOOLEAN DEFAULT TRUE,
    UNIQUE (series_id)
);

-- Jobs/Tasks asynchrones
CREATE TABLE IF NOT EXISTS tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'running', 'completed', 'failed', 'cancelled')),
    payload         JSONB,
    result          JSONB,
    progress        DECIMAL(5,2) DEFAULT 0,
    error           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

-- Configuration clé/valeur
CREATE TABLE IF NOT EXISTS config (
    key             TEXT PRIMARY KEY,
    value           JSONB NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Historique de visionnage
CREATE TABLE IF NOT EXISTS watch_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id        UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    watched_at      TIMESTAMPTZ DEFAULT NOW(),
    progress_seconds INTEGER DEFAULT 0,
    total_seconds   INTEGER DEFAULT 0,
    completed       BOOLEAN DEFAULT FALSE,
    UNIQUE (media_id, user_id)
);

-- Favoris utilisateur
CREATE TABLE IF NOT EXISTS favorites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id        UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, media_id)
);

-- Liste à regarder
CREATE TABLE IF NOT EXISTS watchlist (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id        UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    auto_download   BOOLEAN DEFAULT FALSE,
    quality_min     TEXT DEFAULT '1080p',
    added_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, media_id)
);

-- Index optimisés
CREATE INDEX IF NOT EXISTS idx_media_tmdb ON media(tmdb_id);
CREATE INDEX IF NOT EXISTS idx_media_type ON media(media_type);
CREATE INDEX IF NOT EXISTS idx_media_title_trgm ON media USING gin(title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_media_parent ON media(parent_id);
CREATE INDEX IF NOT EXISTS idx_search_results_media ON search_results(media_id);
CREATE INDEX IF NOT EXISTS idx_search_results_expires ON search_results(expires_at);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_media_files_media ON media_files(media_id);
CREATE INDEX IF NOT EXISTS idx_watch_history_media ON watch_history(media_id);
CREATE INDEX IF NOT EXISTS idx_watch_history_user ON watch_history(user_id);
CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_user ON watchlist(user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- Collections (thematic universes: GoT, Breaking Bad, etc.)
CREATE TABLE IF NOT EXISTS collections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL UNIQUE,
    description     TEXT,
    category        TEXT NOT NULL,  -- 'game-of-thrones', 'breaking-bad', 'stranger-things', 'lucifer', 'harry-potter'
    api_source      TEXT NOT NULL,  -- 'got-quotes', 'breaking-bad', etc
    cover_image_url TEXT,
    backdrop_url    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Collection items (characters, quotes, images within a collection)
CREATE TABLE IF NOT EXISTS collection_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id   UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    external_id     TEXT,
    name            TEXT NOT NULL,
    description     TEXT,
    image_url       TEXT,
    item_type       TEXT,  -- 'character', 'quote', 'image'
    data_json       JSONB,  -- Store full API response (character details, quote text, etc)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_collections_category ON collections(category);
CREATE INDEX IF NOT EXISTS idx_collection_items_collection ON collection_items(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_items_type ON collection_items(item_type);

-- TV Channels
CREATE TABLE IF NOT EXISTS tv_channels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    code            TEXT UNIQUE NOT NULL,  -- 'france2', 'tpn', 'nrk1', etc
    country         TEXT,  -- 'FR', 'CZ', 'NO', etc
    logo_url        TEXT,
    category        TEXT,  -- 'news', 'sport', 'movies', 'entertainment'
    is_free         BOOLEAN DEFAULT true,
    is_active       BOOLEAN DEFAULT true,
    stream_url      TEXT,  -- Direct stream link (optional)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- TV Programs / EPG (Electronic Program Guide)
CREATE TABLE IF NOT EXISTS tv_programs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id      UUID NOT NULL REFERENCES tv_channels(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    description     TEXT,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    genre           TEXT,
    image_url       TEXT,
    rating          DECIMAL(3,1),
    external_id     TEXT,  -- For sync purposes (API-specific ID)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT program_date_range CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_tv_channels_country ON tv_channels(country);
CREATE INDEX IF NOT EXISTS idx_tv_channels_code ON tv_channels(code);
CREATE INDEX IF NOT EXISTS idx_tv_programs_channel ON tv_programs(channel_id);
CREATE INDEX IF NOT EXISTS idx_tv_programs_time ON tv_programs(channel_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_tv_programs_genre ON tv_programs(genre);

-- Security Tables
-- Audit logging for all security-relevant actions
CREATE TABLE IF NOT EXISTS audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          TEXT NOT NULL,  -- 'download_start', 'stream_watch', 'url_check', etc
    resource_type   TEXT,  -- 'torrent', 'stream_link', 'file', etc
    resource_id     TEXT,
    url             TEXT,  -- Full URL for URL-based resources
    ip_address      TEXT,
    user_agent      TEXT,
    risk_level      TEXT DEFAULT 'unknown' CHECK (risk_level IN ('safe', 'warning', 'critical', 'unknown')),
    status          TEXT DEFAULT 'logged' CHECK (status IN ('allowed', 'flagged', 'blocked', 'logged')),
    metadata        JSONB,  -- Additional context (virustotal_result, etc)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- URL reputation cache (local database cache + Redis)
CREATE TABLE IF NOT EXISTS url_reputation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url             TEXT NOT NULL UNIQUE,
    domain          TEXT,
    risk_level      TEXT NOT NULL CHECK (risk_level IN ('safe', 'warning', 'critical')),
    virustotal_result JSONB,  -- Cached VirusTotal response
    urlhaus_result  JSONB,  -- Cached URLhaus response
    malicious_count INTEGER DEFAULT 0,  -- Number of malware detection vendors
    last_checked    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,  -- Cache expiry (86400s default)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Domain whitelist (trusted domains)
CREATE TABLE IF NOT EXISTS domain_whitelist (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain          TEXT NOT NULL UNIQUE,
    added_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    reason          TEXT,  -- Why whitelisted
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Domain blacklist (blocked domains)
CREATE TABLE IF NOT EXISTS domain_blacklist (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain          TEXT NOT NULL UNIQUE,
    risk_level      TEXT NOT NULL CHECK (risk_level IN ('warning', 'critical')),
    threat_type     TEXT,  -- 'malware', 'phishing', 'scam', etc
    added_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    reason          TEXT,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_risk ON audit_logs(risk_level);
CREATE INDEX IF NOT EXISTS idx_url_reputation_domain ON url_reputation(domain);
CREATE INDEX IF NOT EXISTS idx_url_reputation_risk ON url_reputation(risk_level);
CREATE INDEX IF NOT EXISTS idx_url_reputation_expires ON url_reputation(expires_at);
CREATE INDEX IF NOT EXISTS idx_domain_whitelist_domain ON domain_whitelist(domain);
CREATE INDEX IF NOT EXISTS idx_domain_blacklist_domain ON domain_blacklist(domain);
CREATE INDEX IF NOT EXISTS idx_domain_blacklist_risk ON domain_blacklist(risk_level);

-- Valeurs de configuration par défaut
INSERT INTO config (key, value) VALUES
    ('quality_default', '"1080p"'),
    ('auto_download', 'true'),
    ('seed_ratio_limit', '2.0'),
    ('cache_ttl_hours', '24'),
    ('max_concurrent_downloads', '3')
ON CONFLICT (key) DO NOTHING;
