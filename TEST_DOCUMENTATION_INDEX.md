# 📚 Test Documentation Index - SOKOUL v2

Index complet de tous les documents de test pour la plateforme SOKOUL.

---

## 📖 Documents Principaux

### 1. **GEMINI.md** (Master Test Plan)
**Objectif:** Plan complet de test pour architecture distribuée

**Contenu:**
- ✅ Section 1: Setup & Infrastructure
- ✅ Section 2: Unit Tests  
- ✅ Section 3: Integration Tests
- ✅ Section 4: Distributed Systems Testing
- ✅ Section 5: Security & Hardening
- ✅ Section 6: Performance & Load Testing
- ✅ Section 7: Chaos Engineering
- ✅ Section 8: Monitoring & Observability
- ✅ Section 9: CI/CD Pipeline
- ✅ Section 10: Production Validation

**Utilisé par:** QA Engineers, Test Managers, Release Teams
**Durée de lecture:** 30-45 min
**Update:** 2026-02-15 (v2.0)

---

### 2. **TEST_EXECUTION_GUIDE.md** (Hands-On Guide)
**Objectif:** Guide pratique pour exécuter les tests

**Contenu:**
- 🚀 Quick Start commands
- 📋 Tests par phase (Unit → Integration → Security → Performance → Chaos)
- 🔍 Distributed Tracing testing
- 📊 Monitoring setup
- 📝 Test Report Template
- 🔧 Troubleshooting section

**Utilisé par:** Test Engineers, QA, DevOps
**Durée de lecture:** 20 min
**Update:** 2026-02-15 (v1.0)

---

### 3. **CI_CD_TEMPLATE.md** (Pipeline Configuration)
**Objectif:** Configuration prête à l'emploi pour CI/CD

**Contenu:**
- GitHub Actions workflow complet (9 stages)
- GitLab CI configuration
- Pre-commit hooks
- Secret management
- Deployment checklist

**Utilisé par:** DevOps Engineers, Tech Leads
**Durée de lecture:** 25 min
**Update:** 2026-02-15 (v1.0)

---

## 🔗 Documents Connexes (Existants)

### Architecture & Strategy
- **SOKOUL_v2_Architecture_Complete.md** - Architecture système complète
- **FINAL_STRATEGY.md** - Stratégie de déploiement
- **OPTIMIZATIONS.md** - Optimisations appliquées

### Setup & Installation  
- **README.md** - Guide d'installation
- **init.sql** - Initialisation base de données
- **docker-compose.yml** - Infrastructure as Code

---

## 📋 Quick Navigation

### Par Role

#### 🧪 QA Engineer
1. Lire: TEST_EXECUTION_GUIDE.md (sections 1-6)
2. Exécuter: Toutes les phases de test
3. Documenter: Utiliser le Test Report Template
4. Escalader: Ouvrir GitHub issues avec tags "testing"

#### 👨‍💻 Developer
1. Lire: GEMINI.md (sections 1-2)
2. Écrire: Unit tests avant commit
3. Vérifier: Pre-commit hooks passent
4. Valider: Integration tests avant PR

#### 🔐 Security Engineer
1. Lire: GEMINI.md (section 5)
2. Vérifier: `cargo audit` clean
3. Tester: Security test cases
4. Audit: Log sanitization & secrets

#### 🚀 DevOps / SRE
1. Lire: CI_CD_TEMPLATE.md + TEST_EXECUTION_GUIDE.md
2. Configurer: GitHub Actions / GitLab CI
3. Monitor: Prometheus metrics & alerts
4. Deploy: Blue-green / Canary strategies

#### 📊 Tech Lead / Manager
1. Lire: GEMINI.md (sections 1, 9, 10)
2. Track: Test execution checklist
3. Review: Test reports & coverage
4. Release: Approve based on test results

---

## 🎯 Test Coverage Map

### Unit Tests (Section 2)
```
├─ Configuration Validation
│  ├─ Env vars present
│  ├─ Type checking
│  └─ Fast Fail on missing secrets
├─ Business Logic
│  ├─ Utils & Helpers
│  ├─ Model Validation
│  ├─ Input Sanitization (XSS, SQLi)
│  └─ HTTP Client Mocks
└─ Database Queries
   ├─ CRUD operations
   └─ Query performance
```

### Integration Tests (Section 3)
```
├─ API REST Endpoints
│  ├─ Authentication
│  ├─ Search
│  ├─ Media Management
│  └─ Downloads
├─ WebSocket
│  ├─ Connection
│  ├─ Message Flow
│  └─ Heartbeat
└─ Telegram Bot
   ├─ Command Parsing
   └─ Delivery
```

### Distributed Systems (Section 4)
```
├─ NATS JetStream
│  ├─ Stream Creation
│  ├─ Message Durability
│  └─ Ack/Nack
├─ Worker Jobs
│  ├─ Scout Worker
│  ├─ Hunter Worker
│  ├─ Idempotence
│  └─ Poison Pill
└─ Providers
   ├─ TMDB Client
   ├─ Prowlarr
   └─ Tracker Resolution
```

### Security Tests (Section 5)
```
├─ Authentication & Auth
│  ├─ JWT Validation
│  ├─ Password Security
│  └─ RBAC
├─ Input Validation
│  ├─ XSS Prevention
│  ├─ SQL Injection
│  ├─ Command Injection
│  └─ File Upload
├─ Rate Limiting
│  ├─ Per-User
│  ├─ IP-Based
│  └─ Slowloris Protection
└─ Secrets Management
   ├─ Code Scanning
   ├─ Secret Rotation
   └─ Audit Trail
```

### Performance Tests (Section 6)
```
├─ Response Times
│  ├─ API (< 500ms)
│  ├─ Database (< 1ms)
│  └─ WebSocket (< 100ms)
├─ Load Testing
│  ├─ Concurrent Connections
│  ├─ Spike Testing
│  └─ Resource Consumption
├─ Worker Processing
│  ├─ Throughput
│  └─ CPU Usage
└─ Memory Leaks
   ├─ 24h Stability
   └─ Connection Pooling
```

### Chaos Engineering (Section 7)
```
├─ Database Failures
│  ├─ DB Down
│  ├─ Connection Pool Exhaustion
│  └─ Corruption
├─ NATS Failures
│  ├─ Server Down
│  ├─ Partial Failure
│  └─ Stream Full
├─ Redis Failures
│  ├─ Cache Miss
│  └─ Cache Rebuild
└─ Network/System
   ├─ Latency/Packet Loss
   ├─ DNS Resolution
   └─ Disk Space
```

### Monitoring (Section 8)
```
├─ Distributed Tracing
│  ├─ Correlation IDs
│  └─ Request Flow
├─ Logging
│  ├─ Log Levels
│  ├─ Sensitive Data Masking
│  └─ Structured Logging
├─ Metrics
│  ├─ API Metrics
│  ├─ Worker Metrics
│  └─ System Metrics
└─ Alerting
   ├─ Critical Alerts
   └─ Warning Alerts
```

---

## ⏱️ Test Execution Timeline

### Per Commit (< 5 min)
- Pre-commit hooks
- Format check
- Lint check
- Secret scan

### Per PR (< 10 min)
- Unit tests
- Security tests
- Integration tests (basic)

### Per Release (< 30 min)
- Full test suite
- Performance baseline
- Load testing
- Staging deployment
- Smoke tests

### Weekly (Nightly)
- Full suite + chaos tests
- Memory leak detection
- Performance regression
- Coverage report

### Pre-Production
- Staging validation (24h)
- Chaos scenario rerun
- Blue-green deploy test
- Rollback procedure

---

## 📊 Success Criteria

### Phase 1: Unit Tests
```
✅ All unit tests pass
✅ Code coverage > 80%
✅ No panics/unwraps
✅ Zero dependency vulnerabilities
```

### Phase 2: Integration Tests
```
✅ API endpoints respond correctly
✅ Database operations verified
✅ WebSocket connections stable
✅ Telegram bot working
```

### Phase 3: Security Tests
```
✅ Auth/Authz verified
✅ Input validation complete
✅ No secrets in logs
✅ Rate limiting active
```

### Phase 4: Performance Tests
```
✅ API latency < 2s
✅ DB queries < 100ms
✅ Worker throughput sufficient
✅ Memory stable (< 500MB)
```

### Phase 5: Chaos Tests
```
✅ Graceful degradation on failures
✅ Auto-recovery verified
✅ No data loss
✅ Clean logs
```

### Phase 6: Production
```
✅ All components healthy
✅ Metrics flowing
✅ Alerts configured
✅ Rollback plan ready
```

---

## 🔍 Common Issues & Solutions

### Tests Timeout
→ **Solution:** Increase timeout in CI/CD, check for hanging services

### Database Connection Fails
→ **Solution:** Reset DB with `docker-compose down -v && docker-compose up -d`

### Tests Hang on Shutdown
→ **Solution:** Ensure graceful shutdown implemented, check SIGTERM handling

### Memory Leaks in Workers
→ **Solution:** Profile with `cargo flamegraph`, check for circular references

### Flaky Tests
→ **Solution:** Add explicit waits, use test containers, increase timeout

---

## 📞 Support & Escalation

**For questions about:**
- **Test strategy** → Review GEMINI.md section 1-10
- **How to run tests** → See TEST_EXECUTION_GUIDE.md
- **CI/CD setup** → See CI_CD_TEMPLATE.md
- **Test failures** → Troubleshooting section in TEST_EXECUTION_GUIDE.md
- **Architecture** → Review SOKOUL_v2_Architecture_Complete.md

**Report issues:**
1. Open GitHub issue with `testing` label
2. Include test logs with `--nocapture`
3. Provide `RUST_LOG=debug` output
4. Reference section in GEMINI.md

---

## 📈 Metrics Dashboard

Track test metrics via Prometheus/Grafana:

```
sokoul_test_suite_duration_seconds  # Total test execution time
sokoul_test_pass_rate_percent       # % of passing tests
sokoul_test_coverage_percent        # Code coverage
sokoul_ci_build_duration_seconds    # CI pipeline duration
sokoul_deployment_success_rate      # Successful deployments
```

---

## 🎓 Learning Path

**New to SOKOUL Testing?**

1. **Day 1:** Read this index + GEMINI.md overview (sections 1, 2, 3)
2. **Day 2:** Read TEST_EXECUTION_GUIDE.md, run Quick Start
3. **Day 3:** Run Phase 1-2 tests locally
4. **Day 4:** Deep dive into relevant section (Security/Performance/etc)
5. **Day 5:** Set up CI/CD from CI_CD_TEMPLATE.md

---

## 🚀 Ready Checklists

### Pre-Development
- [ ] GEMINI.md reviewed (sections 2)
- [ ] Test patterns understood
- [ ] Development environment set up
- [ ] Unit test template copied

### Pre-PR
- [ ] All unit tests pass locally
- [ ] Pre-commit hooks pass
- [ ] No secrets committed
- [ ] Code reviewed by peer
- [ ] Integration tests pass in CI

### Pre-Release
- [ ] All tests in CI pipeline pass
- [ ] Code coverage > 80%
- [ ] Performance baselines acceptable
- [ ] Security audit passed
- [ ] Staging environment validated
- [ ] Rollback plan reviewed

### Post-Deployment
- [ ] Production smoke tests pass
- [ ] Metrics flowing to Prometheus
- [ ] Alerts configured
- [ ] Logs aggregated
- [ ] Team notified

---

**Last Updated:** 2026-02-15  
**Documentation Version:** 1.0  
**Sokoul Version:** v2.0  
**Maintained by:** QA & DevOps Team
