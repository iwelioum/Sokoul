# 🛡️ Master Test Plan : Architecture Distribuée & Résilience

**Sokoul v2** - Plateforme d'automatisation média haute performance en Rust.

Ce document détaille la stratégie de test complète pour l'application distribué (NATS JetStream, Workers, API Axum, PostgreSQL, Redis, Telegram Bot). L'objectif est de couvrir :
- ✅ La logique métier (business logic)
- ✅ Les modes de défaillance (chaos engineering)
- ✅ La sécurité opérationnelle (security & hardening)
- ✅ La performance et scalabilité
- ✅ La traçabilité distribuée (observability)

---

## 📋 Table des Matières

1. [Setup & Infrastructure](#-1-setup--infrastructure)
2. [Unit Tests](#-2-unit-tests)
3. [Integration Tests](#-3-integration-tests)
4. [Distributed Systems Testing](#-4-distributed-systems-testing)
5. [Security & Hardening](#-5-security--hardening)
6. [Performance & Load Testing](#-6-performance--load-testing)
7. [Chaos Engineering](#-7-chaos-engineering)
8. [Monitoring & Observability](#-8-monitoring--observability)
9. [CI/CD Pipeline](#-9-cicd-pipeline)
10. [Production Validation](#-10-production-validation)

---

## 🏗️ 1. Setup & Infrastructure

*Préparation de l'environnement de test (< 5min)*

### 1.1 Docker Compose Validation

* [ ] **Services démarrage :**
  - [ ] PostgreSQL répond sur `localhost:5432` avec les bonnes credentials
  - [ ] NATS JetStream répond sur `localhost:4222`
  - [ ] Redis répond sur `localhost:6379`
  - [ ] Prowlarr (optionnel) répond sur `http://localhost:9696`
  
```bash
# Vérification
docker-compose ps
docker-compose logs
curl -s http://localhost:4222/healthz
redis-cli PING
psql -h localhost -U sokoul -d sokoul_db -c "SELECT version();"
```

### 1.2 Database Initialization

* [ ] **init.sql exécution :** Crée toutes les tables requises
  - [ ] Table `users` avec auth fields
  - [ ] Table `media` (films, séries) avec métadonnées
  - [ ] Table `torrents` avec status tracking
  - [ ] Table `downloads` avec progress
  - [ ] Table `favorites` et `watch_history`
  - [ ] Table `jobs` (async worker jobs)
  - [ ] Table `logs` (audit trail)

* [ ] **Contraintes intégrité :** 
  - [ ] Foreign keys correctement définies
  - [ ] Indexes sur colonnes critiques (user_id, media_id, status)
  - [ ] Constraints `NOT NULL` sur champs obligatoires
  - [ ] Uniques constraints (email, username, torrent_hash)

* [ ] **Migrations (optionnel):** Si utilisation du système de migrations
  - [ ] `20240101000000_init.sql` s'exécute sans erreur
  - [ ] Idempotence : rejouer la même migration = pas d'erreur
  - [ ] Rollback non-bloquant si migration échoue

### 1.3 Environment Variables

* [ ] **Variables critiques présentes :**
  - [ ] `DATABASE_URL` (PostgreSQL)
  - [ ] `REDIS_URL` (Redis cache)
  - [ ] `NATS_URL` (JetStream)
  - [ ] `TMDB_API_KEY` (TMDB provider)
  - [ ] `TELEGRAM_BOT_TOKEN` (Telegram bot)
  - [ ] `JWT_SECRET` (Auth token signing)

* [ ] **Fast Fail :** Si variable manquante → service crash immédiatement avec message clair

```rust
// src/main.rs - Au démarrage
let db_url = env::var("DATABASE_URL")
    .expect("❌ DATABASE_URL manquante!");
```

---

## 🧱 2. Unit Tests

*Tests rapides (< 1min) executables à chaque commit*

### 2.1 Configuration & Validation

* [ ] **Config Parsing :**
  - [ ] `.env.example` parse sans erreur
  - [ ] Typage strict : `page: u32`, `limit: u32` (pas de strings)
  - [ ] Nombres valides : `limit > 0 && limit <= 100`
  - [ ] URLs valides : `TMDB_API_KEY` est non-vide, `NATS_URL` est URL valide

* [ ] **Secrets Sanitization (Static Analysis) :**
  - [ ] Grep pour détecta `println!("{:?}", env::vars())` ou `logger.debug(secrets)`
  - [ ] Aucun token/clé API loggé en clair
  - [ ] Credentials masquées dans les logs (`***hidden***`)

* [ ] **Dependency Audit :**
  - [ ] `cargo audit` passe (0 CVE)
  - [ ] Dépendances à jour : `cargo outdated | grep -v "^" ` (aucun outdated critique)
  - [ ] License compliance : vérifier licences incompatibles

### 2.2 Logique Métier (Unit Tests)

**Location:** `src/**/*_tests.rs` ou `tests/unit/`

* [ ] **Utils & Helpers :**
  - [ ] Formatage dates : `2026-02-15T15:28:37Z` ↔ Unix timestamp
  - [ ] Parsing fichier torrent (`.torrent` binary format)
  - [ ] Calcul taille fichier : bytes → KB/MB/GB readable format
  - [ ] Slug generation : "Inception 2010" → "inception-2010"

* [ ] **Model Validation :**
  - [ ] `User` : email valide, username 3-32 chars, password >= 8 chars
  - [ ] `Media` : title non-vide, tmdb_id >= 0, genres non-vides
  - [ ] `Torrent` : magnet link ou info_hash valide, peers >= 0
  - [ ] `Job` : status dans enum `[Pending, Running, Completed, Failed]`

* [ ] **Clients HTTP (Mockés) :**
  - [ ] TMDB Provider : mockresponse 200 (film), 404 (not found), 429 (rate limit)
  - [ ] Prowlarr : mock torrent search résultats
  - [ ] Telegram API : mock sendMessage success/failure
  - [ ] Erreurs parsing : réponse HTML au lieu de JSON ne crash pas

```rust
// Exemple mock
#[test]
fn test_tmdb_client_404_handled() {
    let client = MockTmdbClient::with_response(404, "Not Found");
    let result = client.search("InvalidTitle");
    assert!(result.is_err());
}
```

* [ ] **Input Sanitization :**
  - [ ] XSS : `<script>alert('xss')</script>` → chaîne échappée ou rejetée
  - [ ] SQL Injection patterns détectés : `'; DROP TABLE users; --`
  - [ ] JSON parsing : payload malformé rejeté
  - [ ] File upload : vérifier type MIME, taille max

### 2.3 Database Queries (Unit + Integration)

* [ ] **Basic CRUD :**
  - [ ] CREATE user → récupéré correctement
  - [ ] READ par ID → correct
  - [ ] UPDATE champ → persiste
  - [ ] DELETE → suppression en cascade (FK)

* [ ] **Query Performance :**
  - [ ] Index utilisé sur `SELECT by user_id` (< 1ms)
  - [ ] N+1 queries détectées et fixées
  - [ ] Prepared statements utilisés (pas de string concat)

---

## 🔌 3. Integration Tests

*Tests intermédiaires (< 30sec chacun) qui touchent 2+ composants*

### 3.1 API REST Endpoints

**Location:** `tests/api/` ou `src/integration_tests_level1.rs`

* [ ] **Authentication Flow :**
  - [ ] POST `/auth/register` → `201 Created` + token JWT
  - [ ] POST `/auth/login` → `200 OK` + refresh token
  - [ ] GET `/user/profile` sans token → `401 Unauthorized`
  - [ ] GET `/user/profile` token expiré → `401 Unauthorized`
  - [ ] GET `/user/profile` token valide → `200 OK` + user data

* [ ] **Search Endpoints :**
  - [ ] GET `/search?query=Inception` → `200 OK` + résultats TMDB
  - [ ] GET `/search?query=` (vide) → `400 Bad Request`
  - [ ] GET `/search?query=InvalidChar<script>` → sanitisé, pas crash

* [ ] **Media Management :**
  - [ ] POST `/media/favorite` → DB insertion
  - [ ] GET `/media/favorites` → liste correcte
  - [ ] DELETE `/media/favorite/{id}` → suppression
  - [ ] GET `/watch-history` → order by timestamp DESC

* [ ] **Download Flow :**
  - [ ] POST `/downloads/start` → job créé, `202 Accepted`
  - [ ] GET `/downloads/{id}` → status = `Pending|Running|Completed|Failed`
  - [ ] WebSocket upgrade `/ws` → connected avec heartbeat

### 3.2 WebSocket Lifecycle

* [ ] **Connection :**
  - [ ] TCP handshake réussi
  - [ ] Auth via JWT token dans query string
  - [ ] Invalid token → connection rejected
  - [ ] Rate limiting (max 100 connections par user)

* [ ] **Message Flow :**
  - [ ] Client envoie → serveur reçoit
  - [ ] Server envoie → client reçoit
  - [ ] Broadcast message → tous les clients reçoivent
  - [ ] Private message → destinataire seul reçoit

* [ ] **Heartbeat (Ping/Pong) :**
  - [ ] Server envoie PING tous les 30s
  - [ ] Client répond PONG
  - [ ] Pas de PONG → connexion fermée après 60s

* [ ] **Reconnection :**
  - [ ] Client se déconnecte et reconnecte
  - [ ] État récupéré ou envoyé (current status + recent messages)
  - [ ] Pas de perte de messages critique

### 3.3 Telegram Bot Integration

* [ ] **Command Parsing :**
  - [ ] `/search Inception` → affiche résultats
  - [ ] `/status` → affiche état système
  - [ ] `/mylist` → affiche favorites
  - [ ] `/help` → affiche commandes disponibles
  - [ ] `/unknown_cmd` → "Commande inconnue"

* [ ] **Message Delivery :**
  - [ ] Message texte reçu → réponse envoyée < 2s
  - [ ] Telegram API timeout → retry 3x avec backoff
  - [ ] Telegram API erreur 400 → log et abandon (ne retry pas)

* [ ] **User Context :**
  - [ ] Telegram user_id lié au user Sokoul
  - [ ] Même commande de 2 users → réponses indépendantes
  - [ ] Suppression user → Telegram commands arrêtées

---

## ⚙️ 4. Distributed Systems Testing

*Le cœur du système : NATS JetStream, Workers, Async jobs*

### 4.1 NATS JetStream Reliability

* [ ] **Stream Creation :**
  - [ ] Stream `JOBS` créé avec retention policy
  - [ ] Stream `LOGS` créé pour audit trail
  - [ ] Consumer groups créés pour workers

* [ ] **Message Durability :**
  - [ ] Message envoyé → persiste sur disk (pas juste en mémoire)
  - [ ] NATS redémarrage → messages récupérés
  - [ ] Rétention policy respectée (ex: 7 jours)

* [ ] **Acknowledement (Ack/Nack) :**
  - [ ] Worker reçoit message → envoie ACK
  - [ ] Message ACK → retiré de la queue
  - [ ] Worker crashe avant ACK → message rédelivered
  - [ ] Après 3 redeliveries → message en DLQ (Dead Letter Queue)

### 4.2 Worker Jobs - Idempotence & Reliability

**Location:** `src/workers/`

* [ ] **Scout Worker (Search/Indexing) :**
  - [ ] Job reçu avec `job_id` unique
  - [ ] DB insertion avec `ON CONFLICT (job_id) DO NOTHING` (idempotent)
  - [ ] Même job réenvoyé 2x → seule 1 insertion
  - [ ] Erreur API externe → retry avec exponential backoff (2s, 4s, 8s)
  - [ ] Après 3 echecs → status = `Failed`, user notifié

* [ ] **Hunter Worker (Torrent Downloading) :**
  - [ ] Reçoit job avec media_id + magnet link
  - [ ] Commence téléchargement via librqbit
  - [ ] Mises à jour progress tous les 5s → DB
  - [ ] Interruption réseau → pause puis resume
  - [ ] Seed récompense (capped à 24h) → Seedbox delay

* [ ] **Poison Pill (Messages Toxiques) :**
  - [ ] Message avec payload corrompu JSON
  - [ ] Worker parser → error, log, NACK
  - [ ] Message redelivered 3x → envoie à DLQ
  - [ ] DLQ ne crash pas, juste logged

* [ ] **Backpressure & Queue Overflow :**
  - [ ] Injecter 10,000 jobs dans NATS
  - [ ] Workers traiter lentement (throttle)
  - [ ] Mémoire stable (pas d'explosion)
  - [ ] Timeout sur clients n'augmente pas infiniment

### 4.3 Provider Resilience (External APIs)

* [ ] **TMDB Client :**
  - [ ] Réponse HTML au lieu de JSON → log error, pas crash
  - [ ] Rate limit 429 → respecte header `Retry-After`
  - [ ] Timeout 30s → abandon avec error, retry plus tard

* [ ] **Prowlarr Integration :**
  - [ ] Search torrent → parse résultats correctement
  - [ ] Pagination loop : mock retourne toujours "next page"
  - [ ] Worker a limite de sécurité : max 50 pages
  - [ ] Broken pagination → log warning, retourne partial results

* [ ] **Torrent Tracker Resolution :**
  - [ ] DNS failure → worker retry
  - [ ] Slowloris (réponse très lente 59s) → timeout < 30s
  - [ ] Tracker down → worker retry avec backoff
  - [ ] Trop de retries → abort, status = `Failed`

### 4.4 Message Contract & Versioning

* [ ] **Message Schema Validation :**
  - [ ] Toutes les messages NATS ont `job_id`, `timestamp`, `user_id`
  - [ ] Payloads validés avec `serde` JSON schema
  - [ ] Champs inconnus ignorés (forward compatibility)
  - [ ] Champs manquants rejetés (required validation)

* [ ] **Version Compatibility :**
  - [ ] Worker v2 reçoit message v1 → parse correctement
  - [ ] Worker v1 reçoit message v2 → ignore champs extra
  - [ ] Schema breaking change → test déploiement bleu-vert

---

## 🔐 5. Security & Hardening

*Protection contre les vulnérabilités courantes et attaques*

### 5.1 Authentication & Authorization

* [ ] **JWT Tokens :**
  - [ ] Token signé avec `JWT_SECRET` (256-bit minimum)
  - [ ] Expiration : 1h pour access token, 7j pour refresh
  - [ ] Token expiré rejeté (401)
  - [ ] Signature invalide rejetée (401)
  - [ ] Token sans user_id rejeté
  - [ ] Refresh token rotation implémentée

* [ ] **Password Security :**
  - [ ] Hash avec `bcrypt` ou `argon2` (pas plaintext)
  - [ ] Minimum 8 caractères
  - [ ] Password reset link expire après 15min
  - [ ] Rate limiting : max 5 reset attempts / hour / email

* [ ] **Role-Based Access Control (RBAC) :**
  - [ ] User ne peut accéder que ses propres data
  - [ ] Admin peut voir toutes les stats
  - [ ] Moderator peut voir logs
  - [ ] Test : user1 tente accéder user2 downloads → 403 Forbidden

### 5.2 Input Validation & Sanitization

* [ ] **XSS Prevention :**
  - [ ] Tous les inputs HTML-escaped avant DB
  - [ ] JavaScript blocks exécutés jamais
  - [ ] Test : POST avec `<img src=x onerror=alert(1)>` → safe

* [ ] **SQL Injection :**
  - [ ] Toutes les queries utilisent parameterized queries (sqlx bindings)
  - [ ] Pas de string concatenation SQL
  - [ ] Test : `query: "'; DROP TABLE users; --"` → safe

* [ ] **Command Injection :**
  - [ ] Pas d'appel `shell/bash` avec user input
  - [ ] Torrent handling avec librqbit (pas `system()`)

* [ ] **File Upload Security :**
  - [ ] MIME type validé (image/jpeg, image/png seulement)
  - [ ] Taille max 10MB
  - [ ] Filename sanitisé (pas de `../`, pas d'extensions dangereuses)
  - [ ] Stored in isolated directory hors webroot

### 5.3 API Rate Limiting & DDoS Protection

* [ ] **Per-User Rate Limiting :**
  - [ ] Authentified users : 100 req/min
  - [ ] Unauthentified : 10 req/min
  - [ ] Search endpoint : 30 req/min
  - [ ] Download endpoint : 5 concurrent per user

* [ ] **IP-Based Rate Limiting :**
  - [ ] 1000 req/min per IP → 429 Too Many Requests
  - [ ] Whitelist pour trusted services
  - [ ] Test : hammer endpoint → gets rate limited

* [ ] **Slowloris Protection :**
  - [ ] Header timeout : 5s
  - [ ] Body read timeout : 10s
  - [ ] Connection timeout : 30s
  - [ ] Request size max : 1MB

### 5.4 Secrets & Credentials Management

* [ ] **No Secrets in Code :**
  - [ ] Grep check : `git grep -E "(password|token|secret).*=.*['\"]"` → 0 matches
  - [ ] `.env` fichier gitignored
  - [ ] `.env.example` sans valeurs réelles

* [ ] **Secret Rotation :**
  - [ ] Can change `JWT_SECRET` without redeploying
  - [ ] Can change `DB_PASSWORD` (graceful reconnection)
  - [ ] API keys rotatable (old + new keys work temporarily)

* [ ] **Audit Trail :**
  - [ ] Tous les sensitive actions loggés : login, config change, deletion
  - [ ] Log inclut user_id, timestamp, IP, action
  - [ ] Logs non-modifiable après écriture

---

## 🚀 6. Performance & Load Testing

*Vérifier que l'app scale correctement sous charge*

### 6.1 Baseline Performance

**Tool:** `wrk`, `ab` (Apache Bench), ou `k6`

* [ ] **API Response Times (< load) :**
  - [ ] GET `/search?query=Inception` : < 500ms (cached) ou < 2s (TMDB API call)
  - [ ] GET `/media/favorites` : < 100ms
  - [ ] POST `/downloads/start` : < 200ms (job created, async processing)
  - [ ] WebSocket message roundtrip : < 100ms

* [ ] **Database Query Performance :**
  - [ ] SELECT user by ID : < 1ms
  - [ ] SELECT user media (with pagination) : < 50ms
  - [ ] INSERT new download : < 10ms
  - [ ] UPDATE download progress : < 10ms

### 6.2 Load Testing (Sustained Traffic)

```bash
# Example avec wrk
wrk -t4 -c100 -d30s --script=post.lua http://localhost:3000/search
```

* [ ] **Concurrent Connections :**
  - [ ] 100 users concurrent → API responds
  - [ ] 1000 users concurrent → API slower but no errors
  - [ ] 10000 users concurrent → degraded mode, circuit breaker active

* [ ] **Spike Testing :**
  - [ ] Baseline 50 req/s → sudden jump to 500 req/s
  - [ ] System handles spike (< 10% error rate)
  - [ ] Recovery back to baseline after spike

* [ ] **Resource Consumption :**
  - [ ] CPU : < 80% under load
  - [ ] Memory : < 1GB (Rust is efficient)
  - [ ] Disk I/O : < 80% utilization
  - [ ] Network bandwidth : adequate for concurrent streams

### 6.3 Worker Job Processing

* [ ] **Throughput :**
  - [ ] 100 search jobs injected → all processed < 30s
  - [ ] 1000 download jobs → worker queue stable
  - [ ] Peak load 10,000 jobs → backlog handled gracefully

* [ ] **Worker CPU Usage :**
  - [ ] Single worker : 1 CPU core used
  - [ ] 4 worker instances : 4 cores (linear scaling)
  - [ ] No CPU spike on job pickup

### 6.4 Memory & Leak Testing

* [ ] **Long-Running Stability :**
  - [ ] Run workers 24h at 50% load
  - [ ] Monitor RSS memory every 5min
  - [ ] No memory growth > 5% baseline
  - [ ] No cumulative leak observed

* [ ] **Connection Pooling :**
  - [ ] DB connection pool size = 10
  - [ ] Redis connection pool reused
  - [ ] NATS connection shared
  - [ ] No connection leaks detected

---

## 💥 7. Chaos Engineering

*Simulation de pannes réalistes pour valider résilience*

### 7.1 Database Failures

```bash
# Couper PostgreSQL
docker-compose pause sokoul-db
sleep 10
docker-compose unpause sokoul-db
```

* [ ] **DB Down - API Behavior :**
  - [ ] GET `/search` → `503 Service Unavailable` (clean error)
  - [ ] No stack trace exposed to client
  - [ ] Retry-After header présent
  - [ ] Health check endpoint returns unhealthy

* [ ] **DB Down - Worker Behavior :**
  - [ ] Workers detect DB connection failure
  - [ ] Stop processing new jobs (pause queue)
  - [ ] Retry connection with exponential backoff
  - [ ] When DB recovers → resume processing automatically

* [ ] **Connection Pool Exhaustion :**
  - [ ] 10 queries long-running + 10 more queries queued
  - [ ] New queries wait up to 30s for a connection
  - [ ] After timeout → return error, not hang
  - [ ] Pool recovers after long queries complete

* [ ] **DB Corruption (Simulated) :**
  - [ ] Invalid data inserted directly in DB
  - [ ] Application detects type mismatch → log & skip
  - [ ] Doesn't crash, degraded mode
  - [ ] Admin notified to investigate

### 7.2 NATS Failures

```bash
# Couper NATS
docker-compose stop sokoul-nats
sleep 10
docker-compose start sokoul-nats
```

* [ ] **NATS Down - API Behavior :**
  - [ ] Sync endpoints (search, auth) still work (no NATS dependency)
  - [ ] Async endpoints (downloads) queued locally or return 503
  - [ ] User not blocked from using API

* [ ] **NATS Down - Worker Behavior :**
  - [ ] Workers detect NATS unavailable
  - [ ] Stop consuming messages (graceful)
  - [ ] Retry NATS connection periodically
  - [ ] When NATS recovers → automatically reconnect

* [ ] **Partial NATS Failure :**
  - [ ] Message publish succeeds but subscriber offline
  - [ ] Message persisted in NATS
  - [ ] Subscriber reconnects → catches up on messages
  - [ ] No message loss

* [ ] **NATS Stream Full :**
  - [ ] Retention policy 7 days reached
  - [ ] Oldest messages pruned automatically
  - [ ] New messages still enqueued
  - [ ] No crash, degraded mode

### 7.3 Redis Cache Failures

```bash
# Couper Redis
docker-compose stop sokoul-redis
```

* [ ] **Cache Miss - Graceful Degradation :**
  - [ ] GET with no cache → fetch from DB (slower but works)
  - [ ] Cache errors not exposed to user
  - [ ] Log cache error, continue

* [ ] **Cache Rebuild :**
  - [ ] Redis recovers
  - [ ] Application detects
  - [ ] Warm up cache on next queries
  - [ ] Performance gradually improves

### 7.4 Network Partition (Simulated)

* [ ] **Network Latency Increased :**
  - [ ] Introduce 500ms latency to API calls
  - [ ] Requests still complete (within timeout)
  - [ ] No timeout errors if possible

* [ ] **Packet Loss :**
  - [ ] Introduce 5% packet loss
  - [ ] TCP retransmission handles it
  - [ ] Application remains stable

* [ ] **DNS Resolution Failure :**
  - [ ] Hostname resolve fails (DNS server down)
  - [ ] Worker cannot connect to tracker
  - [ ] Exponential backoff retry implemented
  - [ ] User sees "Tracker unreachable, retrying..." not crash

### 7.5 Disk Space Exhaustion

* [ ] **Logs Directory 100% Full :**
  - [ ] Log writes fail (no space)
  - [ ] Application continues (no crash)
  - [ ] Alert sent to monitoring
  - [ ] When space freed → logging resumes

* [ ] **Download Directory Full :**
  - [ ] Torrent download starts but disk full
  - [ ] Worker detects, stops gracefully
  - [ ] Status = `Failed` with reason "Disk full"
  - [ ] Resources cleanup properly

### 7.6 Graceful Shutdown (SIGTERM)

* [ ] **Long Job Running :**
  - [ ] Worker processing torrent (30s job)
  - [ ] Send SIGTERM to worker container
  - [ ] Worker finishes current job (grace period 60s)
  - [ ] Acknowledges NATS message properly
  - [ ] Exits cleanly without leaving DB inconsistent

* [ ] **Active WebSocket Connections :**
  - [ ] Clients connected via WebSocket
  - [ ] Server receives SIGTERM
  - [ ] Server sends close frame to all clients
  - [ ] Clients receive code 1001 (Going Away)
  - [ ] Server exits after grace period

* [ ] **Health Check Awareness :**
  - [ ] During shutdown, health endpoint returns unhealthy
  - [ ] Load balancer removes instance from rotation
  - [ ] In-flight requests are allowed to complete
  - [ ] New requests rejected with 503

---

## 🔍 8. Monitoring & Observability

*Traçabilité distribuée et métriques critiques*

### 8.1 Distributed Tracing (Correlation IDs)

* [ ] **Request Tracing Flow :**
  1. Client sends `X-Request-ID: uuid`
  2. API receives → propagates to NATS message
  3. Worker receives → includes in all logs
  4. Worker writes to DB → includes in INSERT
  5. Response includes `X-Request-ID` header

* [ ] **Log Aggregation Test :**
  - [ ] Inject request with ID `req-12345`
  - [ ] Grep logs across ALL containers for `req-12345`
  - [ ] Can reconstruct complete flow : API → NATS → Worker → DB

* [ ] **End-to-End Trace :**
  ```
  [API]      POST /search?query=Inception (req-12345, t=0ms)
  [NATS]     Publish SEARCH_JOB (req-12345, t=5ms)
  [Scout]    Received SEARCH_JOB (req-12345, t=10ms)
  [Scout]    Call TMDB API (req-12345, t=15ms)
  [Scout]    Persist results (req-12345, t=100ms)
  [Cache]    Update Redis (req-12345, t=105ms)
  [API]      Return results (req-12345, t=110ms)
  ```

### 8.2 Logging Strategy

* [ ] **Log Levels :**
  - [ ] ERROR : failures, data loss, security issues
  - [ ] WARN : retries, degraded mode, missing config
  - [ ] INFO : component startup, important events
  - [ ] DEBUG : detailed flow (disabled in prod)
  - [ ] TRACE : every function call (disabled in prod)

* [ ] **Sensitive Data Masking :**
  - [ ] Passwords logged as `***`
  - [ ] API keys logged as `sk_...***`
  - [ ] JWT tokens logged first 10 chars only
  - [ ] Test : grep logs for secrets → 0 matches

* [ ] **Structured Logging :**
  - [ ] JSON format for easy parsing
  - [ ] Fields : timestamp, level, service, request_id, user_id, message
  - [ ] Tools can parse and aggregate

### 8.3 Metrics & Health Checks

* [ ] **Prometheus Metrics Exposed :**
  - [ ] `sokoul_api_requests_total` (counter)
  - [ ] `sokoul_api_request_duration_seconds` (histogram)
  - [ ] `sokoul_worker_jobs_total` (counter)
  - [ ] `sokoul_worker_job_duration_seconds` (histogram)
  - [ ] `sokoul_db_connections_active` (gauge)
  - [ ] `sokoul_nats_messages_processed` (counter)
  - [ ] `sokoul_cache_hits_total` (counter)
  - [ ] `sokoul_cache_misses_total` (counter)

* [ ] **Health Check Endpoints :**
  - [ ] GET `/health` → 200 if all systems OK
  - [ ] GET `/health/deep` → checks DB, Redis, NATS
  - [ ] Includes status of each component
  - [ ] Docker health check uses `/health`

### 8.4 Alerting Rules

* [ ] **Critical Alerts :**
  - [ ] API error rate > 5% → page on-call
  - [ ] Worker job failure rate > 10% → page on-call
  - [ ] DB connection pool exhausted → page on-call
  - [ ] Disk usage > 90% → alert

* [ ] **Warning Alerts :**
  - [ ] API latency p95 > 2s → investigate
  - [ ] Worker job latency p95 > 30s → check load
  - [ ] Memory usage > 500MB → monitor
  - [ ] Unprocessed messages in NATS > 1000 → add workers

---

## 🔄 9. CI/CD Pipeline

*Automatisation de tests & déploiement sécurisé*

### 9.1 Pre-Commit Hooks

```bash
# .git/hooks/pre-commit
```

* [ ] **Format & Lint :**
  - [ ] `cargo fmt --check` → must pass
  - [ ] `cargo clippy -- -D warnings` → 0 warnings
  - [ ] Exit 1 if check fails → commit blocked

* [ ] **Secret Scanning :**
  - [ ] `truffleHog` or `detect-secrets` scans for credentials
  - [ ] Block commit if secrets detected

### 9.2 CI Pipeline (GitHub Actions / GitLab CI)

**Trigger:** Push to any branch

```yaml
# .github/workflows/ci.yml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: dtolnay/rust-toolchain@stable
      - name: Lint
        run: cargo clippy -- -D warnings
      - name: Test
        run: cargo test --all
      - name: Dependency Audit
        run: cargo audit
```

* [ ] **Build :**
  - [ ] `cargo build --release` succeeds
  - [ ] Binary size < 100MB (Rust is efficient)
  - [ ] Build time < 5min

* [ ] **Unit Tests :**
  - [ ] `cargo test --lib` runs all unit tests
  - [ ] All tests pass
  - [ ] Code coverage reported

* [ ] **Integration Tests :**
  - [ ] Spin up Docker Compose
  - [ ] `cargo test --test '*'` runs integration tests
  - [ ] All pass
  - [ ] Teardown containers

* [ ] **Security Checks :**
  - [ ] `cargo audit` → 0 CVEs
  - [ ] Secret detection → 0 leaks
  - [ ] SAST (Static Analysis) → review findings

* [ ] **Artifact Generation :**
  - [ ] Binary uploaded as artifact
  - [ ] Docker image built and pushed to registry
  - [ ] Version tagged correctly

### 9.3 Pre-Deployment (Staging)

**Trigger:** Push to `main` branch

* [ ] **Smoke Tests on Staging :**
  - [ ] Deploy to staging environment
  - [ ] Run E2E test suite
  - [ ] Verify all critical paths work
  - [ ] Check performance metrics

* [ ] **Load Test on Staging :**
  - [ ] Run 5-minute load test
  - [ ] Verify scaling behavior
  - [ ] Check resource consumption

### 9.4 Deployment to Production

**Trigger:** Release tag `v*`

* [ ] **Blue-Green Deployment :**
  - [ ] Deploy new version to "green" (old still on "blue")
  - [ ] Smoke tests on green
  - [ ] Switch traffic to green
  - [ ] Keep blue running for 1h rollback window

* [ ] **Canary Deployment (Optional):**
  - [ ] Route 10% traffic to new version
  - [ ] Monitor error rate
  - [ ] If OK → 50% → 100%
  - [ ] If errors spike → automatic rollback

* [ ] **Post-Deployment :**
  - [ ] Verify all services healthy
  - [ ] Check logs for errors
  - [ ] Monitor metrics for 15min
  - [ ] Notify team of successful deployment

---

## ✅ 10. Production Validation

*Tests après déploiement en production*

### 10.1 Post-Deployment Smoke Tests

* [ ] **API Availability :**
  - [ ] GET `/health` → 200 OK from production
  - [ ] GET `/search?query=test` → returns results (or cached)
  - [ ] POST `/auth/login` → works with test account
  - [ ] WebSocket `/ws` → can connect

* [ ] **Database Connectivity :**
  - [ ] Write test data → verified in DB
  - [ ] Read data back → correct values
  - [ ] User can login → works with new schema

* [ ] **Worker Processing :**
  - [ ] Inject test job in NATS
  - [ ] Worker picks it up (check logs)
  - [ ] Job completes successfully
  - [ ] Result visible in API

### 10.2 Regression Testing

* [ ] **Critical User Paths :**
  - [ ] Sign up → login → search → add favorite → view watchlist
  - [ ] Download flow → check progress → stream media
  - [ ] Telegram bot → `/search` → `/status`
  - [ ] WebSocket subscription → receive updates

### 10.3 Monitoring & Alerting Active

* [ ] **Dashboard Running :**
  - [ ] Prometheus scraping metrics
  - [ ] Grafana dashboards display live data
  - [ ] Alert rules configured and active
  - [ ] On-call team notified of critical conditions

* [ ] **Log Aggregation :**
  - [ ] ELK / Loki collecting logs from all services
  - [ ] Can search logs by request_id
  - [ ] Retention policy active (e.g., 30 days)

### 10.4 Rollback Plan

* [ ] **Rollback Procedure :**
  - [ ] If major issue detected → switch traffic back to blue
  - [ ] OR revert Docker image to previous tag
  - [ ] OR redeploy previous release tag
  - [ ] Runbook documented and tested

---

## 📚 Test Execution Checklist

Use this checklist to track test campaign:

```
Week 1 - Unit & Config Tests
- [ ] All unit tests passing
- [ ] Lint & format checks passing
- [ ] Dependency audit clean
- [ ] Config validation working

Week 2 - Integration Tests
- [ ] API endpoints tested
- [ ] Database CRUD operations verified
- [ ] WebSocket lifecycle tested
- [ ] Telegram bot commands working

Week 3 - Distributed Systems
- [ ] NATS message flow verified
- [ ] Worker idempotence tested
- [ ] Provider resilience validated
- [ ] Message contract versioning OK

Week 4 - Security & Performance
- [ ] Auth/authz tests passing
- [ ] Input validation & sanitization verified
- [ ] Performance baselines established
- [ ] Load testing completed

Week 5 - Chaos & Resilience
- [ ] Database failure scenarios tested
- [ ] NATS failures handled gracefully
- [ ] Network partition tests passed
- [ ] Graceful shutdown validated

Week 6 - Staging & Production
- [ ] All tests pass on staging
- [ ] Monitoring & alerting active
- [ ] Deployment procedure tested
- [ ] Rollback procedure documented
```

---

## 🛠️ Tools & Frameworks

**Testing Stack:**
- Unit Tests: `cargo test` (built-in)
- Integration Tests: `testcontainers-rs` (Docker containers)
- HTTP Testing: `reqwest` + mocks
- Load Testing: `wrk`, `k6`, or Apache `ab`
- Chaos: Docker compose pause/stop
- Monitoring: Prometheus + Grafana + Loki

**CI/CD:**
- GitHub Actions (or GitLab CI)
- Artifact Registry (Docker Hub / ECR)
- Deployment: `docker-compose` or Kubernetes

---

## 📞 Questions & Escalation

**Not Sure About:**
- [ ] Check architecture docs (`SOKOUL_v2_Architecture_Complete.md`)
- [ ] Review code in `src/`
- [ ] Run specific test with `cargo test --test <name> -- --nocapture`

**Issues Found:**
- [ ] Open GitHub issue with tag `testing`
- [ ] Include error logs & reproduction steps
- [ ] For security issues → confidential report

---

**Last Updated:** 2026-02-15  
**Version:** 2.0  
**Maintenance:** @sokoul-team