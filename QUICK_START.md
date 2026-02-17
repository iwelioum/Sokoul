# ⚡ Sokoul v2 - Quick Start Guide

**Get up and running in 10 minutes!**

---

## 📋 Prerequisites

Before starting, ensure you have installed:

- **Docker Desktop** - https://www.docker.com/products/docker-desktop
  - Includes Docker & Docker Compose
  - Recommended: 4+ GB RAM allocated to Docker
  
- **Rust Toolchain** - https://rustup.rs/
  ```bash
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  rustup update
  ```
  
- **Git** - https://git-scm.com/
  
- **curl or Postman** - For testing API endpoints

### Verify Installation

```bash
docker --version      # Should show Docker version
docker-compose --version
cargo --version       # Should show Rust version
git --version
```

---

## 🚀 5-Minute Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/sokoul/sokoul.git
cd sokoul
```

### Step 2: Configure Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env and add your API keys (or use defaults for local testing)
# Minimum required:
#   TMDB_API_KEY=your_key_here
#   JWT_SECRET=your_secret_here
#   DATABASE_URL=postgresql://sokoul:sokoul@localhost:5432/sokoul_db
```

**Key environment variables:**
```env
# API Configuration
API_HOST=127.0.0.1
API_PORT=3000
API_LOG_LEVEL=info

# Database
DATABASE_URL=postgresql://sokoul:sokoul@localhost:5432/sokoul_db

# Cache
REDIS_URL=redis://localhost:6379

# Message Queue
NATS_URL=nats://localhost:4222

# API Keys (get free keys from these services)
TMDB_API_KEY=your_key_here
TELEGRAM_BOT_TOKEN=your_bot_token_here

# Security
JWT_SECRET=your_jwt_secret_key_min_32_chars
JWT_EXPIRY_HOURS=1

# Workers
SCOUT_WORKER_ENABLED=true
HUNTER_WORKER_ENABLED=true
ORACLE_WORKER_ENABLED=false  # Requires Llama CPU
SENTINEL_WORKER_ENABLED=true
```

### Step 3: Start Infrastructure

```bash
# Start all services (PostgreSQL, Redis, NATS, etc.)
docker-compose up -d

# Wait for services to be ready
docker-compose logs -f

# Check that all services are running
docker-compose ps
```

**Expected output:**
```
STATUS                 PORTS
Up (healthy)           0.0.0.0:5432->5432/tcp    (PostgreSQL)
Up (healthy)           0.0.0.0:6379->6379/tcp    (Redis)
Up (healthy)           0.0.0.0:4222->4222/tcp    (NATS)
```

### Step 4: Initialize Database

```bash
# Run database migrations
./init_db.sh

# Or manually with psql
psql -h localhost -U sokoul -d sokoul_db -f init.sql
```

### Step 5: Run Sokoul

```bash
# Start the Sokoul API server
cargo run

# Or in production mode
cargo build --release
./target/release/sokoul
```

**Expected output:**
```
[INFO] Sokoul v2.0.0 starting...
[INFO] Database connected
[INFO] Redis connected
[INFO] NATS connected
[INFO] API listening on http://127.0.0.1:3000
```

### Step 6: Verify It's Working

```bash
# Test API health
curl http://localhost:3000/health

# Response:
{"status":"healthy","version":"2.0.0"}
```

✅ **You're running Sokoul!** Visit `http://localhost:3000` in your browser.

---

## 🔐 Your First API Request

### 1. Register a User

```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "TestPass123"
  }'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "testuser",
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "expires_in": 3600
}
```

### 2. Save Your Token

```bash
TOKEN="eyJhbGc..."  # Copy from response above
```

### 3. Search for Media

```bash
curl -X POST http://localhost:3000/api/v1/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Inception",
    "media_type": "movie"
  }'
```

**Response (202 Accepted):**
```json
{
  "search_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "searching"
}
```

### 4. Get Search Results

```bash
SEARCH_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

curl http://localhost:3000/api/v1/search/$SEARCH_ID \
  -H "Authorization: Bearer $TOKEN"
```

**Response (after ~10 seconds):**
```json
{
  "search_id": "f47ac10b-...",
  "status": "completed",
  "results": [
    {
      "title": "Inception",
      "year": 2010,
      "rating": 8.8,
      "sources": [...]
    }
  ]
}
```

---

## 📁 Project Structure

```
sokoul/
├── src/                          # Rust source code
│   ├── main.rs                   # Application entry point
│   ├── api/                      # REST API handlers
│   │   ├── search.rs             # Search endpoints
│   │   ├── media.rs              # Media CRUD
│   │   ├── downloads.rs          # Download management
│   │   ├── streaming.rs          # Streaming endpoints
│   │   ├── auth.rs               # Authentication
│   │   └── mod.rs
│   ├── workers/                  # Async job workers
│   │   ├── scout.rs              # Search worker
│   │   ├── hunter.rs             # Download worker
│   │   ├── oracle.rs             # Scoring worker
│   │   └── sentinel.rs           # Monitoring
│   ├── db/                       # Database layer
│   ├── cache.rs                  # Redis caching
│   ├── auth.rs                   # JWT & RBAC
│   ├── security.rs               # Security middleware
│   └── models.rs                 # Data models
│
├── dashboard/                    # SvelteKit UI
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
│
├── docker-compose.yml            # Local development services
├── docker-compose.prod.yml       # Production deployment
├── Dockerfile                    # Rust API image
├── init.sql                      # Database schema
├── Cargo.toml                    # Rust dependencies
├── .env.example                  # Environment template
├── README.md                     # Project overview
├── ARCHITECTURE.md               # Technical architecture
├── API_DOCUMENTATION.md          # Complete API reference
├── PROJECT_COMPLETION_REPORT.md  # Project status
└── QUICK_START.md               # This file
```

---

## 🧪 Running Tests

### Unit Tests

```bash
# Run all tests
cargo test

# Run tests for specific module
cargo test workers::scout::

# Run tests with output
cargo test -- --nocapture

# Run integration tests
cargo test --test integration_tests_level1
```

### Test Results

```
running 50 tests
test auth_tests::test_jwt_validation ... ok
test workers_tests::test_scout_idempotence ... ok
test cache_tests::test_redis_connection ... ok
...
test result: ok. 50 passed; 0 failed
```

---

## 🔧 Making Your First Change

### Scenario: Add a New Search Filter

#### Step 1: Understand the Code

```bash
# Explore the search endpoint
code src/api/search.rs

# Look for the POST /search handler
```

#### Step 2: Add the Feature

**File:** `src/api/search.rs`

```rust
// Before
#[derive(Deserialize)]
pub struct SearchRequest {
    pub query: String,
    pub media_type: String,
}

// After - add year filter
#[derive(Deserialize)]
pub struct SearchRequest {
    pub query: String,
    pub media_type: String,
    pub year: Option<u32>,  // NEW
}
```

#### Step 3: Update the Handler

```rust
pub async fn search_handler(
    State(state): State<AppState>,
    Json(req): Json<SearchRequest>,
) -> impl IntoResponse {
    // Use req.year in search logic
    let results = search_media(&req.query, req.year, &state).await;
    Json(results)
}
```

#### Step 4: Test Your Change

```bash
# Run tests to ensure nothing broke
cargo test

# Start the server
cargo run

# Test the new filter
curl -X POST http://localhost:3000/api/v1/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Inception",
    "media_type": "movie",
    "year": 2010
  }'
```

#### Step 5: Commit Your Change

```bash
git add src/api/search.rs
git commit -m "feat: add year filter to search endpoint"
git push origin feature/year-filter
```

---

## 🐛 Debugging

### View Logs

```bash
# API logs
cargo run 2>&1 | grep -i "error\|warn"

# Docker container logs
docker-compose logs sokoul-api

# Database logs
docker-compose logs sokoul-db
```

### Check Service Status

```bash
# All services
docker-compose ps

# Individual service health
docker-compose exec sokoul-db pg_isready
docker-compose exec sokoul-redis redis-cli ping
docker-compose exec sokoul-nats nats-cli --help
```

### Access Database Directly

```bash
# Connect to PostgreSQL
docker-compose exec sokoul-db psql -U sokoul -d sokoul_db

# List tables
\dt

# Query users
SELECT * FROM users LIMIT 5;

# Exit
\q
```

### Interactive Debugging

```bash
# Use RUST_LOG for verbose output
RUST_LOG=debug cargo run

# Or in docker
docker-compose exec sokoul-api RUST_LOG=debug cargo run
```

---

## 📚 Common Commands

### Development

```bash
# Start dev server with auto-reload
cargo watch -x run

# Format code
cargo fmt

# Lint code
cargo clippy

# Build release binary
cargo build --release

# Clean build artifacts
cargo clean
```

### Docker

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs for specific service
docker-compose logs -f sokoul-api

# Rebuild container
docker-compose up -d --build

# Remove all containers and volumes
docker-compose down -v
```

### Database

```bash
# Backup database
docker-compose exec sokoul-db pg_dump -U sokoul sokoul_db > backup.sql

# Restore database
docker-compose exec sokoul-db psql -U sokoul sokoul_db < backup.sql

# Reset database
docker-compose exec sokoul-db psql -U sokoul -d sokoul_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

---

## 🚧 Troubleshooting

### "Connection refused" to database

```bash
# Check if PostgreSQL is running
docker-compose ps sokoul-db

# If not running, start it
docker-compose up -d sokoul-db

# Wait 10 seconds and try again
sleep 10
```

### "Port already in use"

```bash
# Find process using port 3000
lsof -i :3000

# Kill the process
kill -9 <PID>

# Or use a different port
API_PORT=3001 cargo run
```

### "Authentication failed" for database

```bash
# Check .env file has correct credentials
cat .env | grep DATABASE_URL

# Should be: postgresql://sokoul:sokoul@localhost:5432/sokoul_db
```

### WebSocket connection fails

```bash
# Ensure server is running with WebSocket support
curl -i http://localhost:3000/health

# Should see: HTTP/1.1 200 OK

# Test WebSocket endpoint
wscat -c ws://localhost:3000/ws
```

### Tests failing

```bash
# Ensure all services are running
docker-compose ps

# Run tests with full output
cargo test -- --nocapture --test-threads=1

# Run specific test
cargo test auth_tests::test_login -- --nocapture
```

---

## 📖 Next Steps

### For Developers

1. **Explore the codebase:**
   ```bash
   code src/
   ```

2. **Read the architecture:**
   ```bash
   cat ARCHITECTURE.md
   ```

3. **Check API endpoints:**
   ```bash
   cat API_DOCUMENTATION.md
   ```

4. **Start modifying:**
   - Pick a small issue from GitHub Issues
   - Create a feature branch
   - Make changes
   - Write tests
   - Submit PR

### For Deployment

1. **Review deployment guide:**
   ```bash
   cat PRODUCTION_SETUP_COMPLETE.md
   ```

2. **Configure production environment:**
   ```bash
   cp .env.example .env.prod
   # Edit .env.prod with production values
   ```

3. **Deploy with Docker:**
   ```bash
   docker-compose -f docker-compose.prod.yml up -d
   ```

### For Testing

1. **Run test suite:**
   ```bash
   cargo test
   ```

2. **Load testing:**
   ```bash
   # Use wrk or k6
   wrk -t4 -c100 -d30s http://localhost:3000/api/v1/media
   ```

3. **Integration tests:**
   ```bash
   cargo test --test integration_tests_level1 -- --nocapture
   ```

---

## 🆘 Getting Help

### Documentation
- 📖 [Project Completion Report](PROJECT_COMPLETION_REPORT.md) - Overview & status
- 🏗️ [Architecture](ARCHITECTURE.md) - Technical deep-dive
- 📚 [API Docs](API_DOCUMENTATION.md) - Complete endpoint reference
- 🧪 [Testing Guide](TESTING.md) - Test procedures

### Community & Support
- 🐙 GitHub Issues: Report bugs, ask questions
- 💬 Discussions: Feature requests, ideas
- 📧 Email: support@sokoul.dev

### Debugging Resources
- 🔍 Logs: `docker-compose logs sokoul-api`
- 📊 Metrics: http://localhost:9090 (Prometheus)
- 📈 Dashboards: http://localhost:3001 (Grafana)

---

## 🎯 Quick Reference

| Task | Command |
|------|---------|
| Start dev environment | `docker-compose up -d && cargo run` |
| Stop everything | `docker-compose down` |
| Run tests | `cargo test` |
| Format code | `cargo fmt` |
| Check for issues | `cargo clippy` |
| View logs | `docker-compose logs -f` |
| Access database | `docker-compose exec sokoul-db psql` |
| Rebuild containers | `docker-compose up -d --build` |
| See API | `curl http://localhost:3000/health` |

---

## ✅ Verification Checklist

After setup, verify everything works:

- [ ] Docker services running: `docker-compose ps` shows all Up
- [ ] API responding: `curl http://localhost:3000/health` returns 200
- [ ] Database initialized: `psql` shows tables
- [ ] Server started: `cargo run` completes without errors
- [ ] Can register: `curl POST /auth/register` succeeds
- [ ] Can search: `curl POST /search` returns results
- [ ] Tests pass: `cargo test` shows all green
- [ ] Dashboard loads: Browser opens http://localhost:5173
- [ ] Metrics available: `curl http://localhost:9090` shows Prometheus

---

## 🎓 Learning Path

**Week 1: Basics**
- [ ] Complete this Quick Start
- [ ] Run all tests successfully
- [ ] Make one small code change
- [ ] Read ARCHITECTURE.md

**Week 2: Development**
- [ ] Explore API endpoints (API_DOCUMENTATION.md)
- [ ] Add a new endpoint or feature
- [ ] Write tests for your changes
- [ ] Submit a pull request

**Week 3: Advanced**
- [ ] Understand worker architecture
- [ ] Deploy to staging environment
- [ ] Monitor with Prometheus/Grafana
- [ ] Participate in code reviews

---

**Last Updated:** February 2026  
**Ready to develop?** 🚀 Start with `docker-compose up -d && cargo run`
