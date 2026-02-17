# Sokoul v2 - Production Deployment Guide

**Version:** 2.0  
**Last Updated:** 2026-02-15  
**Status:** Production-Ready

---

## Table of Contents

1. [System Requirements](#system-requirements)
2. [Pre-Deployment Checklist](#pre-deployment-checklist)
3. [Infrastructure Setup](#infrastructure-setup)
4. [SSL/TLS Configuration](#ssltls-configuration)
5. [Database Setup](#database-setup)
6. [Application Deployment](#application-deployment)
7. [Monitoring & Alerting](#monitoring--alerting)
8. [Backup & Recovery](#backup--recovery)
9. [Health Monitoring](#health-monitoring)
10. [Troubleshooting](#troubleshooting)
11. [Security Hardening](#security-hardening)

---

## System Requirements

### Minimum Production Server

| Resource | Requirement | Recommendation |
|----------|-------------|-----------------|
| **CPU** | 2 cores | 4 cores (2x scalability) |
| **RAM** | 4 GB | 8 GB (buffer for spikes) |
| **Disk** | 100 GB SSD | 500 GB SSD (downloads + logs) |
| **Network** | 100 Mbps | 1 Gbps (future-proof) |
| **OS** | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| **Docker** | 20.10+ | 24.0+ (latest stable) |
| **Docker Compose** | 2.10+ | 2.20+ (latest stable) |

### Disk Space Breakdown

```
/          100 GB SSD
├── OS           10 GB
├── Docker       20 GB (images, containers)
├── Postgres      50 GB (database, backup)
├── Redis         10 GB (cache)
├── Logs          10 GB (retention: 30 days)
└── Downloads    reserved (user configurable)
```

### Network & Ports

| Port | Service | Internal | External |
|------|---------|----------|----------|
| **80** | HTTP | ❌ | ✅ (redirect to 443) |
| **443** | HTTPS | ❌ | ✅ (main entry) |
| **3000** | Sokoul API | ✅ | ❌ (reverse proxy only) |
| **5432** | PostgreSQL | ✅ | ❌ (localhost only) |
| **6379** | Redis | ✅ | ❌ (localhost only) |
| **4222** | NATS | ✅ | ❌ (localhost only) |
| **9090** | Prometheus | ✅ | ❌ (localhost only) |
| **3001** | Grafana | ✅ | ❌ (reverse proxy only) |

---

## Pre-Deployment Checklist

### 1. Security Audit ✅

- [ ] Generate strong passwords & secrets:
  ```bash
  # Database password (32 chars)
  openssl rand -base64 32
  
  # JWT Secret (64 chars base64)
  openssl rand -base64 64
  
  # API Key
  openssl rand -hex 32
  
  # Grafana password
  openssl rand -base64 32
  ```

- [ ] Create `.env.prod` from `.env.prod.example`
  ```bash
  cp .env.prod.example .env.prod
  nano .env.prod
  # Edit ALL CHANGE_ME values
  ```

- [ ] Verify no secrets in git history:
  ```bash
  git log -p --all -S "password" -- .env* | head -50
  ```

- [ ] Test environment variables:
  ```bash
  source .env.prod
  echo "DB_PASSWORD=***" # Should not print actual value
  ```

### 2. Domain & DNS Setup ✅

- [ ] Domain registered and pointed to server
  ```bash
  # Test DNS resolution
  nslookup sokoul.example.com
  dig sokoul.example.com +short
  ```

- [ ] SSL certificate ready (Let's Encrypt or CA)
  ```bash
  # For Let's Encrypt (automated)
  certbot certonly --standalone -d sokoul.example.com
  ```

- [ ] Firewall rules configured:
  ```bash
  # Allow HTTP/HTTPS
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  
  # Deny other external ports
  sudo ufw deny 5432/tcp
  sudo ufw deny 6379/tcp
  sudo ufw deny 4222/tcp
  ```

### 3. API Keys & Integrations ✅

- [ ] TMDB API key obtained (https://www.themoviedb.org/settings/api)
- [ ] VirusTotal API key obtained (https://www.virustotal.com/gui/sign-in)
- [ ] Telegram bot token obtained (if enabled) - @BotFather
- [ ] Email/SMTP configured (Gmail app password or SMTP service)
- [ ] Optional: Prowlarr/Jackett configured for torrent indexing

### 4. Docker & Images ✅

- [ ] Docker installed:
  ```bash
  docker --version
  docker run hello-world  # Test
  ```

- [ ] Docker Compose installed:
  ```bash
  docker-compose --version  # Should be 2.10+
  ```

- [ ] Docker daemon running:
  ```bash
  sudo systemctl status docker
  sudo systemctl enable docker  # Enable on boot
  ```

- [ ] Build production image:
  ```bash
  docker build -f Dockerfile.prod -t sokoul:latest .
  docker images | grep sokoul  # Verify
  ```

- [ ] Image size < 500MB:
  ```bash
  docker images sokoul --format "{{.Size}}"
  ```

### 5. Storage & Volumes ✅

- [ ] Create volume directories:
  ```bash
  sudo mkdir -p /var/lib/sokoul/{postgres,redis,nats,downloads,logs}
  sudo chown -R 1000:1000 /var/lib/sokoul/
  ```

- [ ] Verify disk space:
  ```bash
  df -h /var/lib/sokoul/
  # Should show 100+ GB free
  ```

- [ ] Setup backup location (NFS, S3, or external):
  ```bash
  # Example: S3 bucket for backups
  aws s3 ls s3://sokoul-backups/
  ```

### 6. Monitoring & Observability ✅

- [ ] Prometheus configured
- [ ] Grafana admin password set (change default!)
- [ ] Alerting rules configured
- [ ] Log aggregation ready (Loki + Promtail)

### 7. Backup & Disaster Recovery ✅

- [ ] Backup location accessible
- [ ] Database backup script tested
- [ ] Recovery procedure documented
- [ ] Tested restore from backup (critical!)

---

## Infrastructure Setup

### 1. Server Preparation

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install utilities
sudo apt install -y \
  curl \
  wget \
  git \
  htop \
  iotop \
  certbot \
  python3-certbot-nginx

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify installation
docker --version
docker-compose --version
```

### 2. Create Project Directory

```bash
# Create project structure
sudo mkdir -p /opt/sokoul/{certs,nginx,monitoring,backups}
cd /opt/sokoul

# Clone repository (or download files)
git clone https://github.com/your-org/sokoul.git .

# Create environment
cp .env.prod.example .env.prod
# ⚠️ Edit .env.prod with real values!

# Set permissions
sudo chown -R $USER:$USER /opt/sokoul
chmod 600 .env.prod  # Secrets file
```

### 3. Configure Nginx Reverse Proxy

Create `nginx.conf`:

```bash
cat > /opt/sokoul/nginx.conf << 'EOF'
events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    upstream sokoul {
        server sokoul-api:3000;
    }

    upstream grafana {
        server sokoul-grafana:3000;
    }

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=general:10m rate=100r/m;
    limit_req_zone $binary_remote_addr zone=download:10m rate=10r/m;

    # Redirect HTTP to HTTPS
    server {
        listen 80;
        server_name sokoul.example.com www.sokoul.example.com;
        return 301 https://$server_name$request_uri;
    }

    # HTTPS main server
    server {
        listen 443 ssl http2;
        server_name sokoul.example.com www.sokoul.example.com;

        ssl_certificate /etc/nginx/certs/fullchain.pem;
        ssl_certificate_key /etc/nginx/certs/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;
        ssl_session_cache shared:SSL:10m;
        ssl_session_timeout 10m;

        # HSTS (Strict-Transport-Security)
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "DENY" always;
        add_header X-XSS-Protection "1; mode=block" always;

        # Compression
        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
        gzip_min_length 1000;

        # Health check endpoint (no rate limit)
        location /health {
            proxy_pass http://sokoul;
            access_log off;
        }

        # API endpoints (with rate limiting)
        location /api/ {
            limit_req zone=general burst=20 nodelay;
            proxy_pass http://sokoul;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 60s;
            proxy_send_timeout 60s;
            proxy_read_timeout 60s;
        }

        # Download endpoints (stricter rate limit)
        location /api/downloads/ {
            limit_req zone=download burst=5 nodelay;
            proxy_pass http://sokoul;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }

        # WebSocket support
        location /ws {
            proxy_pass http://sokoul;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Frontend assets (cache 1 week)
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            proxy_pass http://sokoul;
            proxy_cache_valid 200 7d;
            expires 7d;
            add_header Cache-Control "public, immutable";
        }

        # Grafana (reverse proxy)
        location /monitoring {
            proxy_pass http://grafana/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Default location
        location / {
            proxy_pass http://sokoul;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
EOF
```

---

## SSL/TLS Configuration

### Using Let's Encrypt (Recommended - Free & Automatic)

```bash
# Install Certbot
sudo apt install -y certbot python3-certbot-nginx

# Create certificate (before starting services)
sudo certbot certonly --standalone \
    -d sokoul.example.com \
    -d www.sokoul.example.com \
    --email admin@sokoul.example.com \
    --agree-tos \
    --non-interactive

# Copy certificates to Docker volume
sudo cp /etc/letsencrypt/live/sokoul.example.com/fullchain.pem /opt/sokoul/certs/
sudo cp /etc/letsencrypt/live/sokoul.example.com/privkey.pem /opt/sokoul/certs/
sudo chown -R 1000:1000 /opt/sokoul/certs/

# Auto-renewal (certbot runs daily)
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer

# Test renewal
sudo certbot renew --dry-run

# Set up auto-reload in Docker
cat > /opt/sokoul/certbot-renewal.sh << 'SCRIPT'
#!/bin/bash
# Auto-reload Nginx after certificate renewal
docker-compose -f docker-compose.prod.yml exec -T nginx nginx -s reload
SCRIPT

chmod +x /opt/sokoul/certbot-renewal.sh
```

### Manual SSL Certificate Upload

If using external CA certificate:

```bash
# Copy certificate files to /opt/sokoul/certs/
sudo cp your-cert.pem /opt/sokoul/certs/fullchain.pem
sudo cp your-key.pem /opt/sokoul/certs/privkey.pem
sudo chmod 600 /opt/sokoul/certs/*.pem
sudo chown 1000:1000 /opt/sokoul/certs/*.pem
```

---

## Database Setup

### 1. Initialize PostgreSQL

```bash
# Create database directories
mkdir -p /opt/sokoul/data/{postgres,backups}

# Start only PostgreSQL to initialize
docker-compose -f docker-compose.prod.yml up -d postgres

# Wait for initialization
sleep 10

# Verify database created
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul -d sokoul -c "\dt"

# Run migrations (if using migration system)
docker-compose -f docker-compose.prod.yml exec sokoul-api \
    cargo sqlx migrate run
```

### 2. Create Backup User (Optional but Recommended)

```bash
# Connect to database
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul -d sokoul << SQL
-- Create backup user with limited privileges
CREATE USER backup_user WITH PASSWORD 'BACKUP_PASSWORD';
GRANT CONNECT ON DATABASE sokoul TO backup_user;
GRANT USAGE ON SCHEMA public TO backup_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO backup_user;
SQL
```

### 3. Database Backup Script

Create `/opt/sokoul/backup-db.sh`:

```bash
#!/bin/bash
set -e

BACKUP_DIR="/opt/sokoul/data/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/sokoul_backup_$TIMESTAMP.sql.gz"

echo "[$(date)] Starting database backup..."

# Create backup
docker-compose -f /opt/sokoul/docker-compose.prod.yml exec -T postgres \
    pg_dump -U sokoul sokoul | gzip > "$BACKUP_FILE"

# Verify backup size
SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "[$(date)] Backup completed: $BACKUP_FILE ($SIZE)"

# Keep only last 30 days
find "$BACKUP_DIR" -name "sokoul_backup_*.sql.gz" -mtime +30 -delete

# Optional: Upload to S3
# aws s3 cp "$BACKUP_FILE" s3://sokoul-backups/

echo "[$(date)] Backup rotation completed"
```

```bash
chmod +x /opt/sokoul/backup-db.sh

# Schedule daily backup at 2 AM
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/sokoul/backup-db.sh") | crontab -
```

---

## Application Deployment

### 1. Pre-Deployment Verification

```bash
# Verify all configuration
cd /opt/sokoul

# Check environment file
echo "=== Environment Variables ==="
source .env.prod
echo "DB_PASSWORD: ***" # Should not print actual value

# Verify secrets not in git
git log -p --all -S "CHANGE_ME" | head -5

# Verify certificates exist
ls -la certs/

# Verify volumes ready
ls -la data/
```

### 2. Build and Start Services

```bash
cd /opt/sokoul

# Build production image (takes ~10 minutes)
docker build -f Dockerfile.prod -t sokoul:latest .

# Verify image
docker images sokoul --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

# Start all services (database first)
docker-compose -f docker-compose.prod.yml up -d

# Monitor startup (wait 30 seconds)
sleep 30

# Check service status
docker-compose -f docker-compose.prod.yml ps

# View logs (first 50 lines)
docker-compose -f docker-compose.prod.yml logs --tail=50
```

### 3. Verify All Services

```bash
#!/bin/bash

echo "=== Service Health Checks ==="

# PostgreSQL
echo -n "PostgreSQL: "
docker-compose -f docker-compose.prod.yml exec -T postgres \
    pg_isready -U sokoul >/dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

# Redis
echo -n "Redis: "
docker-compose -f docker-compose.prod.yml exec -T redis \
    redis-cli PING >/dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

# NATS
echo -n "NATS: "
curl -sf http://localhost:8222/healthz >/dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

# Sokoul API
echo -n "Sokoul API: "
curl -sf http://localhost:3000/health >/dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

# Nginx
echo -n "Nginx: "
curl -sf http://localhost/health >/dev/null 2>&1 && echo "✅ OK" || echo "❌ FAILED"

echo ""
echo "=== API Health Check ==="
curl -s http://localhost:3000/health | jq .

echo ""
echo "=== All Services Running ==="
docker-compose -f docker-compose.prod.yml ps
```

### 4. Post-Deployment Smoke Tests

```bash
# Test API endpoints
echo "Testing API endpoints..."

# Health check
curl -v https://sokoul.example.com/health

# Search API
curl -X GET "https://sokoul.example.com/api/search?query=inception" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# WebSocket test (requires wscat)
npm install -g wscat
wscat -c wss://sokoul.example.com/ws

# Load testing (optional)
ab -n 100 -c 10 https://sokoul.example.com/health
```

---

## Monitoring & Alerting

### 1. Prometheus Configuration

Create `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    environment: production
    service: sokoul

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - prometheus:9093

rule_files:
  - "/etc/prometheus/rules.yml"

scrape_configs:
  - job_name: "sokoul-api"
    static_configs:
      - targets: ["sokoul-api:9090"]
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance

  - job_name: "postgres"
    static_configs:
      - targets: ["postgres-exporter:9187"]

  - job_name: "redis"
    static_configs:
      - targets: ["redis-exporter:9121"]

  - job_name: "nats"
    static_configs:
      - targets: ["nats:8222"]
```

### 2. Alert Rules

Create `prometheus/rules.yml`:

```yaml
groups:
  - name: sokoul_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(sokoul_api_errors_total[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High API error rate"

      - alert: DatabaseDown
        expr: up{job="postgres"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL is down"

      - alert: DiskSpaceRunningOut
        expr: node_filesystem_avail_bytes / node_filesystem_size_bytes < 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Disk usage above 90%"

      - alert: HighMemoryUsage
        expr: process_resident_memory_bytes / 1e9 > 3
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Memory usage above 3GB"
```

### 3. Grafana Setup

```bash
# Access Grafana
open https://sokoul.example.com:3001

# Default credentials (from .env.prod)
# Username: admin
# Password: GRAFANA_PASSWORD

# Steps:
# 1. Add Prometheus datasource (http://prometheus:9090)
# 2. Import dashboard from JSON
# 3. Configure alert notification channels
# 4. Add alert rules
```

---

## Backup & Recovery

### 1. Automated Daily Backup

Already configured in cron (see Database Setup section).

### 2. Manual Backup

```bash
# Database backup
/opt/sokoul/backup-db.sh

# Full Docker volumes backup
tar czf /opt/sokoul/data/backups/sokoul_full_$(date +%Y%m%d).tar.gz \
    /opt/sokoul/data/postgres \
    /opt/sokoul/data/redis \
    /opt/sokoul/data/nats
```

### 3. Recovery Procedure

**⚠️ WARNING: This deletes current data! Only use in disaster recovery!**

```bash
# Stop all services
docker-compose -f docker-compose.prod.yml down

# Restore database backup
BACKUP_FILE="/opt/sokoul/data/backups/sokoul_backup_20260215_020000.sql.gz"
zcat "$BACKUP_FILE" | docker-compose -f docker-compose.prod.yml exec -T postgres \
    psql -U sokoul sokoul

# Restart services
docker-compose -f docker-compose.prod.yml up -d

# Verify
docker-compose -f docker-compose.prod.yml ps
```

---

## Health Monitoring

### 1. Health Check Endpoints

```bash
# API health
curl -s http://localhost:3000/health | jq .

# Response example:
{
  "status": "healthy",
  "timestamp": "2026-02-15T15:28:37Z",
  "services": {
    "database": "ok",
    "redis": "ok",
    "nats": "ok"
  }
}
```

### 2. Container Status

```bash
# Watch container status
watch -n 5 'docker-compose -f docker-compose.prod.yml ps'

# View resource usage
docker stats

# View logs
docker-compose -f docker-compose.prod.yml logs -f sokoul-api  # Last 50 lines + follow
docker-compose -f docker-compose.prod.yml logs --tail=100 sokoul-api
```

### 3. Prometheus Metrics

```bash
# Query metrics
curl -s 'http://localhost:9091/api/v1/query?query=up' | jq .

# HTTP requests per minute
curl -s 'http://localhost:9091/api/v1/query?query=rate(sokoul_api_requests_total[1m])' | jq .
```

---

## Troubleshooting

### Common Issues & Solutions

#### Issue: "Cannot connect to database"

```bash
# Check PostgreSQL logs
docker-compose -f docker-compose.prod.yml logs postgres | tail -20

# Verify PostgreSQL is running
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul -d sokoul -c "SELECT version();"

# Check connection string in .env.prod
echo $DATABASE_URL
```

#### Issue: "CORS error on frontend"

```bash
# Verify CORS_ORIGINS in .env.prod
grep CORS_ORIGINS .env.prod

# Must include protocol and domain:
# ❌ WRONG: sokoul.example.com
# ✅ RIGHT: https://sokoul.example.com

# Reload API after change
docker-compose -f docker-compose.prod.yml restart sokoul-api
```

#### Issue: "Disk space running out"

```bash
# Check disk usage
df -h

# View Docker image sizes
docker images --format "table {{.Repository}}\t{{.Size}}"

# Clean up old containers & images
docker system prune -a --volumes

# Compress logs
gzip /opt/sokoul/data/logs/*.log

# Check database size
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul -d sokoul -c "SELECT pg_size_pretty(pg_database_size('sokoul'));"
```

#### Issue: "High memory usage"

```bash
# Monitor memory in real-time
docker stats --no-stream

# Check Redis memory
docker-compose -f docker-compose.prod.yml exec redis redis-cli INFO memory

# Check which process is using memory
ps aux --sort=-%mem | head -10

# Clear Redis cache (if needed)
docker-compose -f docker-compose.prod.yml exec redis redis-cli FLUSHALL
```

#### Issue: "Certificate renewal failed"

```bash
# Check certificate status
certbot certificates

# Manual renewal
sudo certbot renew --verbose

# Check renewal logs
sudo journalctl -u certbot.timer -n 50
```

---

## Security Hardening

### 1. Firewall Configuration

```bash
# Enable UFW
sudo ufw enable

# Allow SSH
sudo ufw allow 22/tcp

# Allow HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Deny everything else
sudo ufw default deny incoming
sudo ufw default allow outgoing

# Verify rules
sudo ufw status

# Fail2ban (brute-force protection)
sudo apt install -y fail2ban

# Configure for Nginx
cat > /etc/fail2ban/jail.local << EOF
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5

[sshd]
enabled = true

[nginx-http-auth]
enabled = true

[nginx-limit-req]
enabled = true
EOF

sudo systemctl restart fail2ban
```

### 2. SSH Hardening

```bash
# Disable password auth (use SSH keys only)
sudo nano /etc/ssh/sshd_config
# Change: PermitRootLogin no
# Change: PasswordAuthentication no
# Change: PubkeyAuthentication yes

sudo systemctl restart sshd

# Test new key before closing current session!
ssh -i ~/.ssh/id_rsa user@server
```

### 3. Container Security

```bash
# Run containers with read-only filesystem (where possible)
docker-compose -f docker-compose.prod.yml exec sokoul-api \
    mount | grep "read-only"

# Check for vulnerable dependencies
docker scan sokoul:latest

# Keep images updated
docker pull sokoul:latest
docker pull postgres:latest
docker pull redis:latest
```

### 4. Secret Management

```bash
# Never commit secrets to git
cat > .gitignore << EOF
.env.prod
.env.prod.local
certs/
data/backups/
downloads/
EOF

# Use environment file (not committed)
docker-compose -f docker-compose.prod.yml --env-file .env.prod up

# Or use Docker secrets (Docker Swarm / Kubernetes)
docker secret create db_password .env.prod
```

### 5. Audit Logging

```bash
# Enable detailed logging
RUST_LOG=debug,tower_http=debug

# Forward logs to external service
docker-compose -f docker-compose.prod.yml exec loki \
    curl -X POST http://localhost:3100/loki/api/v1/push

# Query logs
curl -G -s "http://localhost:3100/loki/api/v1/query" \
    --data-urlencode 'query={service="sokoul-api"}'
```

### 6. Regular Security Updates

```bash
# Create update script
cat > /opt/sokoul/update.sh << 'EOF'
#!/bin/bash
set -e

echo "[$(date)] Starting Sokoul update..."

# Stop services
cd /opt/sokoul
docker-compose -f docker-compose.prod.yml down

# Backup database
./backup-db.sh

# Update images
docker-compose -f docker-compose.prod.yml pull

# Restart services
docker-compose -f docker-compose.prod.yml up -d

# Wait for health
sleep 30
docker-compose -f docker-compose.prod.yml ps

echo "[$(date)] Update completed successfully"
EOF

chmod +x /opt/sokoul/update.sh

# Schedule weekly updates (Sunday 3 AM)
(crontab -l 2>/dev/null; echo "0 3 * * 0 /opt/sokoul/update.sh") | crontab -
```

---

## Maintenance Runbook

### Daily Tasks

- [ ] Monitor Grafana dashboards
- [ ] Check error rate (should be < 1%)
- [ ] Verify backup completed
- [ ] Monitor disk space (should be > 20% free)

### Weekly Tasks

- [ ] Review security logs for suspicious activity
- [ ] Update Docker images: `docker-compose pull && docker-compose up -d`
- [ ] Check certificate expiration: `certbot certificates`
- [ ] Run performance baseline tests

### Monthly Tasks

- [ ] Test database restoration from backup
- [ ] Review and rotate API keys
- [ ] Audit user access and permissions
- [ ] Generate monthly report

---

## Support & Escalation

### Getting Help

1. **Check Logs First:**
   ```bash
   docker-compose -f docker-compose.prod.yml logs sokoul-api | tail -50
   ```

2. **Check Health Status:**
   ```bash
   curl -s http://localhost:3000/health | jq .
   ```

3. **Monitor Resources:**
   ```bash
   docker stats
   df -h
   ```

4. **Review Documentation:**
   - See: `IMPLEMENTATION_COMPLETE.md`
   - See: `METRICS_GUIDE.md`
   - See: `TESTING.md`

### Emergency Contacts

- **On-Call Engineer:** [Contact Info]
- **Database Admin:** [Contact Info]
- **Security Team:** [Contact Info]

---

## Appendix: Useful Commands

```bash
# View all logs
docker-compose -f docker-compose.prod.yml logs -f

# View specific service logs
docker-compose -f docker-compose.prod.yml logs -f sokoul-api

# Execute command in container
docker-compose -f docker-compose.prod.yml exec sokoul-api bash

# Connect to database
docker-compose -f docker-compose.prod.yml exec postgres \
    psql -U sokoul sokoul

# Redis CLI
docker-compose -f docker-compose.prod.yml exec redis redis-cli

# Restart specific service
docker-compose -f docker-compose.prod.yml restart sokoul-api

# Scale service (if using Docker Swarm)
docker-compose -f docker-compose.prod.yml up -d --scale worker=3

# Remove all stopped containers and images
docker system prune -a --volumes
```

---

**Deployment Date:** _________________  
**Deployed By:** _________________  
**Status:** ☐ Successful ☐ Rollback ☐ Issues

---

*For updates and latest version, visit: https://github.com/your-org/sokoul*
