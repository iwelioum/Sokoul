# 📊 Test Execution Guide - SOKOUL v2

Guide pratique pour exécuter les tests selon le Master Test Plan (GEMINI.md).

---

## 🚀 Quick Start

```bash
# 1. Préparation
cd C:\Users\oumba\Desktop\Sokoul\Sokoul
docker-compose down -v && docker-compose up -d
sleep 5

# 2. Vérifier l'infrastructure
docker-compose ps
redis-cli PING
nats-cli

# 3. Lancer les tests
cargo test --all
cargo test --all -- --nocapture  # Avec output
```

---

## 📋 Tests par Phase

### Phase 1: Unit Tests (< 2 min)

**Objectif:** Vérifier la logique métier isolée

```bash
# Tous les unit tests
cargo test --lib

# Spécifique à un module
cargo test --lib config:: -- --nocapture
cargo test --lib models:: -- --nocapture
cargo test --lib utils::
```

**Checklist:**
- [ ] Config validation tests passing
- [ ] Model validation tests passing
- [ ] HTTP client mock tests passing
- [ ] Input sanitization tests passing

**Location des tests:**
```
src/config_tests.rs
src/models.rs (avec #[cfg(test)])
src/client_tests.rs
src/utils/**/*_tests.rs
```

---

### Phase 2: Integration Tests (< 5 min)

**Objectif:** Tester 2+ composants ensemble (API, DB, Cache)

```bash
# Lancer Docker Compose (si pas déjà en place)
docker-compose up -d

# Integration tests
cargo test --test integration_tests_level1

# Avec logs détaillés
RUST_LOG=debug cargo test --test integration_tests_level1 -- --nocapture --test-threads=1
```

**Checklist:**
- [ ] API endpoints responding
- [ ] Database CRUD operations verified
- [ ] Cache hit/miss working
- [ ] WebSocket connection established
- [ ] Telegram command parsing working

**Location des tests:**
```
tests/integration_tests_level1.rs
src/integration_tests_level1.rs
```

---

### Phase 3: Security Tests (< 3 min)

**Objectif:** Validation des inputs et auth

```bash
# Tests de sécurité spécifiques
cargo test --test security_robustness_tests

# Ou avec output détaillé
RUST_LOG=warn cargo test --test security_robustness_tests -- --nocapture
```

**Checklist:**
- [ ] XSS prevention verified
- [ ] SQL injection patterns detected & blocked
- [ ] JWT token validation working
- [ ] Password hashing implemented
- [ ] Rate limiting active
- [ ] Secrets not in logs

**Manual Security Checks:**

```bash
# Grep pour secrets en clair
git grep -E "(password|token|secret|key).*=.*['\"]" -- src/

# Dependency audit
cargo audit

# Clutter check (logs sensibles)
cargo test --lib -- --nocapture 2>&1 | grep -i "password\|token\|secret"
```

---

### Phase 4: Performance Tests (< 5 min)

**Objectif:** Baselines de perf et détection de fuites

```bash
# Tests de performance
cargo test --test performance_concurrency_tests

# Avec profiling
RUST_LOG=info cargo test --test performance_concurrency_tests -- --nocapture --test-threads=1
```

**Checklist:**
- [ ] API response times baseline
- [ ] Database query performance
- [ ] Worker job throughput
- [ ] No memory leaks detected

**Manual Performance Testing:**

```bash
# Avec wrk (si disponible)
wrk -t4 -c100 -d30s http://localhost:3000/health

# Avec Apache Bench
ab -n 1000 -c 50 http://localhost:3000/search?query=test

# Monitor system resources
watch -n1 "docker stats sokoul-api sokoul-db"
```

---

### Phase 5: Worker & NATS Tests

**Objectif:** Fiabilité du système distribué

```bash
# Tests des workers
cargo test --lib workers:: -- --nocapture

# Tests NATS message flow
cargo test scout:: -- --nocapture
cargo test hunter:: -- --nocapture
```

**Checklist:**
- [ ] Worker idempotence verified
- [ ] Message ACK/NACK working
- [ ] Poison pill handling correct
- [ ] Backpressure working

**Manual NATS Testing:**

```bash
# Vérifier NATS server
nats-cli server info

# Publier un test message
nats-cli pub JOBS.SCOUT '{"job_id":"test-123"}'

# Vérifier consumer
nats-cli consumer info JOBS SCOUT_CONSUMER_1
```

---

### Phase 6: Chaos Testing (< 10 min)

**Objectif:** Vérifier résilience en cas de pannes

#### 6.1 Database Down

```bash
# Couper PostgreSQL
docker-compose pause sokoul-db

# Test: API should return 503
curl -i http://localhost:3000/search?query=test

# Vérifier logs
docker-compose logs sokoul-api | grep -i "database\|connection"

# Redémarrer
docker-compose unpause sokoul-db
sleep 2

# Vérifier récupération
curl -i http://localhost:3000/search?query=test
```

**Expected:**
- [ ] Immediate 503 Service Unavailable
- [ ] No stack traces exposed
- [ ] Retry-After header present
- [ ] Recovery automatic

#### 6.2 NATS Down

```bash
# Couper NATS
docker-compose stop sokoul-nats

# Test: Sync endpoints still work
curl -i http://localhost:3000/search?query=test

# Async endpoints should gracefully degrade
curl -i -X POST http://localhost:3000/downloads/start -d '{"media_id":1}'

# Redémarrer NATS
docker-compose start sokoul-nats
sleep 2

# Vérifier queue resume
docker-compose logs sokoul-api | grep -i "nats\|connected"
```

**Expected:**
- [ ] Sync API still responsive
- [ ] Async jobs queued locally or fail gracefully
- [ ] No worker crash
- [ ] Auto-reconnect on NATS recovery

#### 6.3 Redis Down

```bash
# Couper Redis
docker-compose stop sokoul-redis

# Test: Cache miss should fallback to DB
curl -i http://localhost:3000/media/favorites

# Latency should increase but work
time curl http://localhost:3000/media/favorites > /dev/null

# Redémarrer Redis
docker-compose start sokoul-redis

# Vérifier cache warm-up
sleep 2
time curl http://localhost:3000/media/favorites > /dev/null
```

**Expected:**
- [ ] Requests still succeed (slower)
- [ ] Cache errors logged, not exposed
- [ ] Auto-recovery when Redis restarts

#### 6.4 Graceful Shutdown

```bash
# Terminal 1: Start a long-running job
docker-compose logs -f sokoul-worker

# Terminal 2: Inject a job que prendra ~30s
curl -X POST http://localhost:3000/downloads/start \
  -H "Content-Type: application/json" \
  -d '{"media_id":1,"magnet":"..."}'

# Attendre qu'il commence

# Terminal 3: Stop gracefully
docker-compose stop --time=120 sokoul-worker

# Vérifier que le job s'est terminé correctement
docker-compose logs sokoul-worker | tail -20
```

**Expected:**
- [ ] Worker finishes job within grace period
- [ ] NATS message ACKed
- [ ] Clean shutdown, no orphaned processes

---

## 🔍 Distributed Tracing

**Vérifier la traçabilité complète d'une requête:**

```bash
# 1. Faire une requête avec curl
curl -i -H "X-Request-ID: trace-$(uuidgen)" \
  http://localhost:3000/search?query=Inception

# 2. Note le request ID (ex: trace-abc123)

# 3. Grepper tous les logs pour cet ID
docker-compose logs | grep "trace-abc123"

# 4. Vérifier la flow:
#    API log: "Received request trace-abc123"
#    NATS log: "Published message trace-abc123"
#    Worker log: "Processing trace-abc123"
#    DB log: "Query trace-abc123"
#    API log: "Returned results trace-abc123"
```

---

## 📊 Monitoring Setup

### Prometheus Metrics

```bash
# Vérifier que Prometheus scrape les métriques
curl http://localhost:9090/api/v1/query?query=sokoul_api_requests_total

# Voir les targets
curl http://localhost:9090/api/v1/targets
```

### Grafana Dashboards

```bash
# Accéder à Grafana
open http://localhost:3000  # Grafana (admin/admin par défaut)

# Importer le dashboard Sokoul
# Configuration > Data Sources > Add Prometheus (http://prometheus:9090)
# Dashboards > Import > sokoul-dashboard.json
```

---

## 📝 Test Report Template

Utiliser ce template pour documenter les test runs:

```markdown
# Test Run Report - [DATE]

## Environment
- Docker Compose version: [version]
- Rust version: [rustc --version]
- Cargo version: [cargo --version]
- OS: [Windows/Linux/MacOS]

## Phase 1: Unit Tests
- Result: ✅ PASSED / ❌ FAILED
- Duration: X min
- Coverage: X%
- Issues: [list]

## Phase 2: Integration Tests
- Result: ✅ PASSED / ❌ FAILED
- Duration: X min
- Components tested: API, DB, Cache, WebSocket, Telegram
- Issues: [list]

## Phase 3: Security Tests
- Result: ✅ PASSED / ❌ FAILED
- Duration: X min
- Auth verified: ✅
- Input validation verified: ✅
- Log sanitization verified: ✅
- Issues: [list]

## Phase 4: Performance Tests
- API latency: [ms]
- DB query time: [ms]
- Cache hit rate: [%]
- Worker throughput: [jobs/min]
- Issues: [list]

## Phase 5: Chaos Tests
- Database failure: ✅ PASSED / ❌ FAILED
- NATS failure: ✅ PASSED / ❌ FAILED
- Redis failure: ✅ PASSED / ❌ FAILED
- Graceful shutdown: ✅ PASSED / ❌ FAILED
- Issues: [list]

## Summary
- Overall: ✅ READY FOR DEPLOYMENT / ⚠️ NEEDS FIXES
- Critical issues: [list]
- Recommendations: [list]

---
**Tested by:** [name]  
**Date:** [date]  
**Duration:** [total time]
```

---

## 🔧 Troubleshooting

### Tests hang or timeout

```bash
# Augmenter le timeout
cargo test --all -- --test-threads=1 --timeout 120

# Ou run spécifique test avec verbose output
RUST_LOG=debug cargo test --lib specific_test -- --nocapture --test-threads=1
```

### Docker issues

```bash
# Reset complet
docker-compose down -v
docker-compose up -d
docker-compose logs -f

# Check disk space
docker system df

# Rebuild images
docker-compose build --no-cache
```

### Database connection issues

```bash
# Vérifier connexion DB
psql postgresql://sokoul:sokoul_password@localhost:5432/sokoul_db \
  -c "SELECT 1;"

# Réinitialiser DB
docker-compose exec sokoul-db psql -U sokoul -d sokoul_db \
  -f /docker-entrypoint-initdb.d/init.sql

# Vérifier init.sql
cat init.sql | head -50
```

### NATS issues

```bash
# Vérifier NATS server
docker-compose logs sokoul-nats | grep -i "server|listening"

# Reconnecter
docker-compose restart sokoul-nats
```

---

## 📞 Resources

- **Full Test Plan:** `GEMINI.md`
- **Architecture:** `SOKOUL_v2_Architecture_Complete.md`
- **Setup Guide:** `README.md`
- **Optimizations:** `OPTIMIZATIONS.md`

---

**Last Updated:** 2026-02-15  
**Version:** 1.0
