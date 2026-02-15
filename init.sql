-- SOKOUL v2 — Schema PostgreSQL complet
-- Extensions requises
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

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
    user_id         UUID NOT NULL,
    watched_at      TIMESTAMPTZ DEFAULT NOW(),
    progress_seconds INTEGER DEFAULT 0,
    total_seconds   INTEGER DEFAULT 0,
    completed       BOOLEAN DEFAULT FALSE,
    UNIQUE (media_id, user_id)
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

-- Valeurs de configuration par défaut
INSERT INTO config (key, value) VALUES
    ('quality_default', '"1080p"'),
    ('auto_download', 'true'),
    ('seed_ratio_limit', '2.0'),
    ('cache_ttl_hours', '24'),
    ('max_concurrent_downloads', '3')
ON CONFLICT (key) DO NOTHING;
