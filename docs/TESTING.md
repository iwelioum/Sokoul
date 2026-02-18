# Sokoul — Tests

---

## Tests Rust

Les tests sont dans `src/` (tests unitaires et d'intégration) et `tests/` (E2E).

```bash
# Tous les tests
cargo test

# Tests avec output
cargo test -- --nocapture

# Test spécifique
cargo test health_endpoint

# Tests E2E (nécessite l'infra Docker démarrée)
cargo test --test e2e_smoke_tests -- --test-threads=1 --nocapture
cargo test --test e2e_load_tests -- --test-threads=1 --nocapture
cargo test --test e2e_chaos_tests -- --test-threads=1 --nocapture
```

### Fichiers de test

| Fichier | Type | Contenu |
|---------|------|---------|
| `src/tests.rs` | Unitaire | Tests des fonctions internes |
| `src/auth_flow_tests.rs` | Intégration | Flux d'authentification |
| `src/health_checks_tests.rs` | Intégration | Endpoints /health |
| `src/rate_limiting_tests.rs` | Intégration | Rate limiting |
| `src/input_sanitization_tests.rs` | Sécurité | Validation des inputs |
| `src/security_robustness_tests.rs` | Sécurité | Robustesse sécurité |
| `src/metrics_tests.rs` | Intégration | Métriques Prometheus |
| `src/load_testing_tests.rs` | Performance | Charge concurrent |
| `src/chaos_engineering_tests.rs` | Chaos | Pannes réseau/DB |
| `tests/e2e_smoke_tests.rs` | E2E | Smoke tests post-déploiement |
| `tests/e2e_load_tests.rs` | E2E | 50-500 utilisateurs concurrent |
| `tests/e2e_chaos_tests.rs` | E2E | Scénarios de panne |

---

## Tests Frontend

```bash
cd dashboard

# Linter
npm run check

# Build de validation
npm run build
```

---

## Tests Consumet API (manuel)

```bash
# Santé
curl http://localhost:3002/

# Recherche film
curl "http://localhost:3002/movies/himovies/fight%20club"

# Info film via TMDB
curl "http://localhost:3002/meta/tmdb/info/550?type=movie&provider=HiMovies"

# Serveurs disponibles pour un épisode
curl "http://localhost:3002/movies/himovies/servers?episodeId=108013&mediaId=movie/fight-club-108013"

# Test endpoint streaming backend
curl "http://localhost:3000/streaming/consumet/movie/550"
```

---

## Tests de régression streaming

Après tout changement dans `src/clients/consumet.rs` ou `src/api/streaming.rs` :

```bash
# 1. Recompiler
cargo build

# 2. Redémarrer le backend
# (tuer l'ancien processus si nécessaire)

# 3. Vider le cache Redis
docker exec $(docker ps --filter "name=redis" -q) redis-cli FLUSHDB

# 4. Tester avec Fight Club (TMDB 550)
curl -s "http://localhost:3000/streaming/consumet/movie/550" | python3 -m json.tool

# 5. Vérifier que sources et headers sont présents
```
