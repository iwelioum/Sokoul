# Production Deployment Setup for Sokoul v2

This directory contains all production-grade deployment configurations for Sokoul v2, a high-performance Rust-based media automation platform.

## 📦 Files Included

### Core Docker Files

- **`Dockerfile.prod`** - Production-grade multi-stage Docker build
  - Stage 1: Rust backend compilation (release optimized)
  - Stage 2: SvelteKit frontend build
  - Stage 3: Minimal runtime environment (~500MB)
  - Non-root user execution (security)
  - Health check configured

- **`docker-compose.prod.yml`** - Production orchestration
  - PostgreSQL with persistent storage
  - Redis with RDB + AOF persistence
  - NATS JetStream for distributed messaging
  - Sokoul API backend (reverse-proxied)
  - Nginx reverse proxy with SSL/TLS
  - Prometheus + Grafana monitoring
  - Loki + Promtail log aggregation

### Configuration Files

- **`.env.prod.example`** - Environment template
  - Server configuration
  - Database credentials
  - API keys (TMDB, VirusTotal, Telegram, etc.)
  - Security settings
  - Monitoring configuration
  - Comprehensive comments and security warnings

- **`nginx.conf`** - Nginx reverse proxy configuration
  - SSL/TLS with modern ciphers
  - Rate limiting (API, auth, downloads)
  - WebSocket support
  - Gzip compression
  - Security headers (HSTS, CSP, etc.)
  - Health check endpoint
  - Prometheus metrics export
  - Grafana reverse proxy

- **`loki-config.yml`** - Loki log aggregation server
  - BoltDB shipper for storage
  - 30-day retention policy
  - JSON log parsing

- **`promtail-config.yml`** - Promtail log collector
  - Container log scraping
  - Service-specific labels
  - Log parsing pipelines

### Documentation

- **`DEPLOYMENT.md`** - Complete deployment guide
  - System requirements (2-4 cores, 4-8 GB RAM, 100+ GB SSD)
  - Pre-deployment checklist
  - Step-by-step setup instructions
  - SSL/TLS with Let's Encrypt
  - Database initialization & backup
  - Health monitoring & troubleshooting
  - Security hardening guide
  - Maintenance runbook

## 🚀 Quick Start

### Prerequisites

```bash
# System requirements
- Ubuntu 22.04 LTS or similar
- Docker 20.10+
- Docker Compose 2.10+
- Domain name with DNS pointing to server
- 100+ GB SSD disk space
- 4+ GB RAM (8 GB recommended)
```

### Step 1: Server Setup (5 min)

```bash
# Clone repository
git clone https://github.com/your-org/sokoul.git /opt/sokoul
cd /opt/sokoul

# Install Docker and Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### Step 2: Configuration (10 min)

```bash
# Create environment file
cp .env.prod.example .env.prod

# Generate secrets
DB_PASSWORD=$(openssl rand -base64 32)
JWT_SECRET=$(openssl rand -base64 64)
API_KEY=$(openssl rand -hex 32)
GRAFANA_PASSWORD=$(openssl rand -base64 32)

# Edit .env.prod with real values
nano .env.prod
# - Database password (paste generated value)
# - JWT secret (paste generated value)
# - TMDB API key (get from tmdb.org)
# - Grafana password (paste generated value)
# - Domain: sokoul.example.com
# - Email: admin@sokoul.example.com
```

### Step 3: SSL/TLS Setup (5 min)

```bash
# Install Certbot for Let's Encrypt
sudo apt install -y certbot

# Create certificate
sudo certbot certonly --standalone \
    -d sokoul.example.com \
    -d www.sokoul.example.com \
    --email admin@sokoul.example.com \
    --agree-tos \
    --non-interactive

# Copy to Docker volume
sudo cp /etc/letsencrypt/live/sokoul.example.com/fullchain.pem certs/
sudo cp /etc/letsencrypt/live/sokoul.example.com/privkey.pem certs/
sudo chown -R 1000:1000 certs/
```

### Step 4: Build and Deploy (15 min)

```bash
# Build production image
docker build -f Dockerfile.prod -t sokoul:latest .

# Start all services
docker-compose -f docker-compose.prod.yml up -d

# Monitor startup
sleep 30
docker-compose -f docker-compose.prod.yml ps

# View logs
docker-compose -f docker-compose.prod.yml logs --tail=50
```

### Step 5: Verify Deployment (5 min)

```bash
# Health checks
curl -s https://sokoul.example.com/health | jq .

# Verify all services
docker-compose -f docker-compose.prod.yml ps

# Check Grafana access
open https://sokoul.example.com/monitoring
# Username: admin
# Password: (from .env.prod)
```

## 🏗️ Architecture

### Service Stack

```
┌─────────────────────────────────────────────────────┐
│              User / Client Browser                    │
└────────────────────┬────────────────────────────────┘
                     │ https://sokoul.example.com
                     ▼
┌─────────────────────────────────────────────────────┐
│          Nginx Reverse Proxy (Port 80/443)           │
│  - SSL/TLS Termination                              │
│  - Rate Limiting                                    │
│  - Compression (gzip)                              │
└────────────────────┬────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
   ┌─────────┐  ┌──────────┐  ┌──────────┐
   │Sokoul   │  │Grafana   │  │Frontend  │
   │API      │  │(9090)    │  │(SvelteKit)
   │(3000)   │  │          │  │          │
   └────┬────┘  └──────────┘  └──────────┘
        │
    ┌───┴───────────────────────────────┐
    │   Internal Services (Docker)       │
    │                                    │
    │  ┌───────────┐  ┌──────┐          │
    │  │PostgreSQL │  │Redis │          │
    │  │(5432)     │  │(6379)│          │
    │  └───────────┘  └──────┘          │
    │                                    │
    │  ┌──────────┐  ┌─────────────┐    │
    │  │NATS      │  │Prometheus   │    │
    │  │(4222)    │  │(9090)       │    │
    │  └──────────┘  └─────────────┘    │
    │                                    │
    │  ┌────────────┐  ┌─────────────┐  │
    │  │Loki        │  │Promtail     │  │
    │  │(3100)      │  │             │  │
    │  └────────────┘  └─────────────┘  │
    │                                    │
    └────────────────────────────────────┘
```

### Data Flow

```
User Request
    ↓
Nginx (SSL/TLS termination, rate limiting)
    ↓
Sokoul API (Axum)
    ├→ PostgreSQL (data persistence)
    ├→ Redis (cache/session)
    └→ NATS (async jobs)
        └→ Workers (background processing)
    ↓
Response (JSON/HTML)
    ↓
User
```

## 📊 Monitoring & Observability

### Prometheus Metrics

Available at `https://sokoul.example.com/metrics`:

- `sokoul_api_requests_total` - Total API requests
- `sokoul_api_request_duration_seconds` - Request latency histogram
- `sokoul_api_errors_total` - Total errors by endpoint
- `sokoul_worker_jobs_total` - Worker job count
- `sokoul_db_connections_active` - Active DB connections
- `sokoul_cache_hits_total` - Cache hits
- `sokoul_cache_misses_total` - Cache misses

### Grafana Dashboards

Access: `https://sokoul.example.com/monitoring`

Pre-configured dashboards:
- System overview (CPU, memory, disk)
- API performance (latency, error rate)
- Database health (connections, queries)
- Cache performance (hit rate, evictions)
- Worker status (job queue, processing time)

### Loki Logs

Query logs by label:
```
{service="sokoul-api"}        # API logs
{service="postgres"}          # Database logs
{level="error"}               # All errors
{request_id="req-12345"}      # Single request trace
```

## 🔒 Security Features

### Out of the Box

- ✅ SSL/TLS with auto-renewal (Let's Encrypt)
- ✅ Non-root container execution (UID 1000)
- ✅ Rate limiting (global + per-endpoint)
- ✅ CORS protection
- ✅ Security headers (HSTS, CSP, X-Frame-Options)
- ✅ Input validation & sanitization
- ✅ Password hashing (bcrypt)
- ✅ JWT token signing
- ✅ Secrets management (environment variables)
- ✅ Firewall rules (UFW template included)
- ✅ Fail2ban integration (brute-force protection)
- ✅ Audit logging (all sensitive actions)

### Configuration Required

- [ ] Strong database password (32+ chars)
- [ ] Strong JWT secret (64+ chars base64)
- [ ] API keys rotated monthly
- [ ] SSH key-based auth only (no passwords)
- [ ] Firewall configured (ports 80/443 only)
- [ ] Backups encrypted and stored securely

## 🔧 Maintenance

### Daily

- Monitor Grafana dashboards
- Check error rates (should be < 1%)
- Verify backups completed

### Weekly

- Review security logs
- Update Docker images: `docker-compose -f docker-compose.prod.yml pull && docker-compose -f docker-compose.prod.yml up -d`
- Check certificate expiration: `certbot certificates`

### Monthly

- Test database restoration from backup
- Rotate API keys
- Audit user access

### Backup & Recovery

```bash
# Manual backup
docker-compose -f docker-compose.prod.yml exec postgres \
    pg_dump -U sokoul sokoul | gzip > backup.sql.gz

# Automated daily backup (configured in docker-compose)
# Located at: /opt/sokoul/data/backups/sokoul_backup_*.sql.gz

# Recovery
docker-compose -f docker-compose.prod.yml down
zcat backup.sql.gz | docker-compose -f docker-compose.prod.yml exec -T postgres psql -U sokoul sokoul
docker-compose -f docker-compose.prod.yml up -d
```

## 🐛 Troubleshooting

### Service won't start

```bash
# Check logs
docker-compose -f docker-compose.prod.yml logs sokoul-api

# Verify environment
grep DATABASE_URL .env.prod

# Check disk space
df -h

# Restart services
docker-compose -f docker-compose.prod.yml restart sokoul-api
```

### High CPU usage

```bash
# Monitor containers
docker stats

# Check which service
docker-compose -f docker-compose.prod.yml stats

# Scale up if needed (Kubernetes only)
```

### Database connection errors

```bash
# Verify PostgreSQL
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul -d sokoul -c "SELECT 1"

# Check connection pool
docker-compose -f docker-compose.prod.yml exec sokoul-api \
    curl -s http://localhost:9090/metrics | grep pg_pool
```

### Certificate renewal failing

```bash
# Manual renewal
sudo certbot renew --verbose

# Check logs
sudo journalctl -u certbot.timer -n 50

# Reload Nginx
docker-compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

## 📈 Performance Tuning

### PostgreSQL

Configured for ~4 GB RAM server:
```
shared_buffers=512MB        # 25% of RAM
effective_cache_size=1536MB # 75% of RAM
work_mem=16MB              # Per operation
```

Adjust for your server size (see `docker-compose.prod.yml`).

### Redis

Configured with:
- LRU eviction policy (auto-cleanup)
- RDB + AOF persistence
- 2 GB max memory
- 4 I/O threads

### Nginx

- Worker processes: auto (matches CPU cores)
- Keepalive: 65s
- Compression: gzip (levels 1-6)
- Cache: 1 week for assets, 5 min for API

## 🚨 Alerts

Configured alerts (check Grafana):

- **Critical:** API error rate > 5%
- **Critical:** Database down
- **Warning:** Disk usage > 90%
- **Warning:** Memory usage > 3 GB
- **Warning:** Unprocessed messages in NATS > 1000

## 📚 Documentation

- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Complete deployment guide
- **[../IMPLEMENTATION_COMPLETE.md](../IMPLEMENTATION_COMPLETE.md)** - Implementation details
- **[../METRICS_GUIDE.md](../METRICS_GUIDE.md)** - Metrics and monitoring
- **[../TESTING.md](../TESTING.md)** - Test plan and procedures

## 🆘 Support

For issues or questions:

1. Check logs: `docker-compose -f docker-compose.prod.yml logs`
2. Verify health: `curl https://sokoul.example.com/health`
3. Check Grafana: `https://sokoul.example.com/monitoring`
4. Review DEPLOYMENT.md troubleshooting section
5. Open GitHub issue with logs and reproduction steps

## 📝 Deployment Checklist

- [ ] System meets minimum requirements (CPU, RAM, disk)
- [ ] Domain configured and DNS pointing to server
- [ ] SSL certificate created (Let's Encrypt)
- [ ] `.env.prod` file created with all secrets
- [ ] All API keys configured
- [ ] Firewall rules set (ports 80/443 only)
- [ ] Docker and Docker Compose installed
- [ ] Production image built
- [ ] All services healthy
- [ ] Health check passing
- [ ] Grafana dashboards loading
- [ ] Backups configured and tested
- [ ] Monitoring alerts active
- [ ] Security hardening complete

---

**Version:** 2.0  
**Last Updated:** 2026-02-15  
**Maintainer:** Sokoul Team

For latest updates: https://github.com/your-org/sokoul
