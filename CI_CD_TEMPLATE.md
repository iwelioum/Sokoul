# 🔄 CI/CD Pipeline Configuration

Configuration complète pour GitHub Actions ou GitLab CI, basée sur le Master Test Plan.

---

## GitHub Actions

**Fichier:** `.github/workflows/ci.yml`

```yaml
name: CI/CD Pipeline - SOKOUL v2

on:
  push:
    branches:
      - main
      - develop
      - 'feature/**'
  pull_request:
    branches:
      - main
      - develop
  schedule:
    - cron: '0 2 * * *'  # Nightly at 2 AM UTC

env:
  CARGO_TERM_COLOR: always
  RUST_BACKTRACE: 1

jobs:
  # ============================================================
  # PHASE 1: Format & Lint (Fast Checks - < 2 min)
  # ============================================================
  format-lint:
    name: Format & Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable
        with:
          components: rustfmt, clippy

      - name: Cache cargo registry
        uses: actions/cache@v3
        with:
          path: ~/.cargo/registry
          key: ${{ runner.os }}-cargo-registry-${{ hashFiles('**/Cargo.lock') }}

      - name: Cache cargo index
        uses: actions/cache@v3
        with:
          path: ~/.cargo/git
          key: ${{ runner.os }}-cargo-git-${{ hashFiles('**/Cargo.lock') }}

      - name: Cache cargo build
        uses: actions/cache@v3
        with:
          path: target
          key: ${{ runner.os }}-cargo-build-target-${{ hashFiles('**/Cargo.lock') }}

      - name: Format check
        run: cargo fmt --check

      - name: Clippy (Linter)
        run: cargo clippy --all-targets --all-features -- -D warnings

      - name: Secret scanning
        run: |
          echo "Scanning for secrets..."
          ! git grep -E '(password|token|secret|api_key).*=.*["\x27]' -- src/
          echo "✅ No secrets found in code"

  # ============================================================
  # PHASE 2: Security & Dependencies (< 2 min)
  # ============================================================
  security:
    name: Security & Audit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Cargo audit
        run: |
          cargo install cargo-audit
          cargo audit

      - name: SAST - Check for panics
        run: |
          echo "Checking for unhandled panics..."
          ! git grep -E "unwrap\(\)|expect\(" -- src/ | grep -v "#\[allow" | grep -v "test" | grep -v "main\.rs"
          echo "✅ Minimal unwrap/expect usage"

  # ============================================================
  # PHASE 3: Unit Tests (< 3 min)
  # ============================================================
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Cache cargo
        uses: actions/cache@v3
        with:
          path: target
          key: ${{ runner.os }}-cargo-build-target-${{ hashFiles('**/Cargo.lock') }}

      - name: Run unit tests
        run: cargo test --lib --verbose

      - name: Generate coverage report
        run: |
          cargo install tarpaulin
          cargo tarpaulin --out Xml --exclude-files tests/ --timeout 300
        continue-on-error: true

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./cobertura.xml
        continue-on-error: true

  # ============================================================
  # PHASE 4: Build (< 3 min)
  # ============================================================
  build:
    name: Build Binary
    runs-on: ubuntu-latest
    needs: [format-lint, security]
    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Cache cargo
        uses: actions/cache@v3
        with:
          path: target
          key: ${{ runner.os }}-cargo-build-target-${{ hashFiles('**/Cargo.lock') }}

      - name: Build release binary
        run: cargo build --release --verbose

      - name: Check binary size
        run: |
          SIZE=$(stat -f%z target/release/sokoul 2>/dev/null || stat -c%s target/release/sokoul)
          SIZE_MB=$((SIZE / 1024 / 1024))
          echo "Binary size: ${SIZE_MB}MB"
          if [ $SIZE_MB -gt 200 ]; then
            echo "❌ Binary too large (> 200MB)"
            exit 1
          fi
          echo "✅ Binary size OK"

      - name: Upload binary artifact
        uses: actions/upload-artifact@v3
        with:
          name: sokoul-binary-${{ github.sha }}
          path: target/release/sokoul
          retention-days: 5

  # ============================================================
  # PHASE 5: Integration Tests (< 10 min)
  # ============================================================
  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: sokoul
          POSTGRES_PASSWORD: sokoul_password
          POSTGRES_DB: sokoul_db
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 6379:6379

      nats:
        image: nats:latest
        options: >-
          --health-cmd "nats-cli server ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 4222:4222

    env:
      DATABASE_URL: postgresql://sokoul:sokoul_password@localhost:5432/sokoul_db
      REDIS_URL: redis://localhost:6379
      NATS_URL: nats://localhost:4222
      RUST_LOG: info

    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Cache cargo
        uses: actions/cache@v3
        with:
          path: target
          key: ${{ runner.os }}-cargo-build-target-${{ hashFiles('**/Cargo.lock') }}

      - name: Initialize database
        run: |
          psql -h localhost -U sokoul -d sokoul_db < init.sql
        env:
          PGPASSWORD: sokoul_password

      - name: Run integration tests
        run: cargo test --test '*' --verbose

  # ============================================================
  # PHASE 6: Security Tests (< 3 min)
  # ============================================================
  security-tests:
    name: Security Tests
    runs-on: ubuntu-latest
    needs: unit-tests
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: sokoul
          POSTGRES_PASSWORD: sokoul_password
          POSTGRES_DB: sokoul_db
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    env:
      DATABASE_URL: postgresql://sokoul:sokoul_password@localhost:5432/sokoul_db
      RUST_LOG: warn

    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Run security tests
        run: cargo test --test security_robustness_tests -- --nocapture

      - name: Check log sanitization
        run: |
          cargo test --lib -- --nocapture 2>&1 | \
            grep -i -c "password\|token\|secret\|api_key" && \
            echo "❌ Secrets found in logs!" && exit 1 || \
            echo "✅ No secrets in logs"

  # ============================================================
  # PHASE 7: Docker Build (< 5 min)
  # ============================================================
  docker-build:
    name: Build Docker Image
    runs-on: ubuntu-latest
    needs: build
    if: github.event_name == 'push'
    
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2

      - name: Download binary artifact
        uses: actions/download-artifact@v3
        with:
          name: sokoul-binary-${{ github.sha }}

      - name: Build Docker image
        uses: docker/build-push-action@v4
        with:
          context: .
          push: false
          tags: |
            sokoul:latest
            sokoul:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Test Docker image
        run: |
          docker build -t sokoul:test .
          docker run --rm sokoul:test /sokoul --version

  # ============================================================
  # PHASE 8: Performance Baseline (< 5 min, nightly only)
  # ============================================================
  performance-tests:
    name: Performance Baseline
    runs-on: ubuntu-latest
    needs: integration-tests
    if: github.event.schedule == '0 2 * * *'  # Nightly only
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: sokoul
          POSTGRES_PASSWORD: sokoul_password
          POSTGRES_DB: sokoul_db
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    env:
      DATABASE_URL: postgresql://sokoul:sokoul_password@localhost:5432/sokoul_db

    steps:
      - uses: actions/checkout@v4

      - name: Install Rust
        uses: dtolnay/rust-toolchain@stable

      - name: Run performance tests
        run: cargo test --test performance_concurrency_tests -- --nocapture

      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: performance-report-${{ github.sha }}
          path: target/performance-results.json
        continue-on-error: true

  # ============================================================
  # Status Check
  # ============================================================
  test-status:
    name: Test Status Check
    runs-on: ubuntu-latest
    needs: [format-lint, security, unit-tests, build, integration-tests, security-tests]
    if: always()
    
    steps:
      - name: Check all tests passed
        run: |
          if [ "${{ needs.format-lint.result }}" != "success" ]; then
            echo "❌ Format/Lint failed"
            exit 1
          fi
          if [ "${{ needs.security.result }}" != "success" ]; then
            echo "❌ Security checks failed"
            exit 1
          fi
          if [ "${{ needs.unit-tests.result }}" != "success" ]; then
            echo "❌ Unit tests failed"
            exit 1
          fi
          if [ "${{ needs.build.result }}" != "success" ]; then
            echo "❌ Build failed"
            exit 1
          fi
          if [ "${{ needs.integration-tests.result }}" != "success" ]; then
            echo "❌ Integration tests failed"
            exit 1
          fi
          if [ "${{ needs.security-tests.result }}" != "success" ]; then
            echo "❌ Security tests failed"
            exit 1
          fi
          echo "✅ All checks passed!"

      - name: Notify Slack
        if: failure()
        uses: slackapi/slack-github-action@v1.24.0
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
          payload: |
            {
              "text": "SOKOUL CI/CD Pipeline Failed",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Build Status:* ❌ FAILED\n*Branch:* ${{ github.ref_name }}\n*Commit:* ${{ github.sha }}\n*Author:* ${{ github.actor }}"
                  }
                }
              ]
            }

  # ============================================================
  # Staging Deployment (main branch only)
  # ============================================================
  deploy-staging:
    name: Deploy to Staging
    runs-on: ubuntu-latest
    needs: test-status
    if: github.ref == 'refs/heads/main' && success()
    
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to staging
        run: |
          echo "Deploying to staging environment..."
          # Add your deployment script here
          # e.g., docker-compose -f docker-compose.staging.yml up -d

      - name: Run smoke tests on staging
        run: |
          echo "Running smoke tests..."
          sleep 5
          curl -f http://staging.sokoul.local/health

      - name: Notify deployment
        uses: slackapi/slack-github-action@v1.24.0
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK_URL }}
          payload: |
            {
              "text": "SOKOUL Deployed to Staging ✅",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Environment:* Staging\n*Branch:* main\n*Commit:* ${{ github.sha }}\n*Status:* ✅ Deployed"
                  }
                }
              ]
            }
```

---

## GitLab CI

**Fichier:** `.gitlab-ci.yml`

```yaml
stages:
  - lint
  - test
  - build
  - deploy

variables:
  CARGO_TERM_COLOR: "always"
  RUST_BACKTRACE: "1"

# ============================================================
# Stage 1: Lint
# ============================================================
format-check:
  stage: lint
  image: rust:latest
  script:
    - cargo fmt --check
    - cargo clippy --all-targets -- -D warnings
  cache:
    paths:
      - target/
      - .cargo/

security-check:
  stage: lint
  image: rust:latest
  script:
    - cargo install cargo-audit
    - cargo audit
  allow_failure: true

# ============================================================
# Stage 2: Test
# ============================================================
unit-tests:
  stage: test
  image: rust:latest
  script:
    - cargo test --lib --verbose
  coverage: '/coverage: \d+\.\d+%/'
  cache:
    paths:
      - target/
      - .cargo/

integration-tests:
  stage: test
  image: rust:latest
  services:
    - postgres:16
    - redis:7-alpine
    - nats:latest
  variables:
    POSTGRES_USER: sokoul
    POSTGRES_PASSWORD: sokoul_password
    POSTGRES_DB: sokoul_db
    DATABASE_URL: postgresql://sokoul:sokoul_password@postgres:5432/sokoul_db
    REDIS_URL: redis://redis:6379
    NATS_URL: nats://nats:4222
  script:
    - cargo test --test '*' --verbose
  cache:
    paths:
      - target/
      - .cargo/

# ============================================================
# Stage 3: Build
# ============================================================
build-binary:
  stage: build
  image: rust:latest
  script:
    - cargo build --release
  artifacts:
    paths:
      - target/release/sokoul
    expire_in: 1 week
  cache:
    paths:
      - target/
      - .cargo/

build-docker:
  stage: build
  image: docker:latest
  services:
    - docker:dind
  script:
    - docker build -t sokoul:$CI_COMMIT_SHA .
    - docker tag sokoul:$CI_COMMIT_SHA sokoul:latest
  only:
    - main
    - develop

# ============================================================
# Stage 4: Deploy
# ============================================================
deploy-staging:
  stage: deploy
  image: alpine:latest
  script:
    - echo "Deploying to staging..."
    - apk add --no-cache docker-compose
    - docker-compose -f docker-compose.staging.yml up -d
  environment:
    name: staging
    url: https://staging.sokoul.local
  only:
    - main
```

---

## Pre-commit Hooks

**Fichier:** `.git/hooks/pre-commit`

```bash
#!/bin/bash
set -e

echo "🔍 Running pre-commit checks..."

# Format check
echo "📝 Checking format..."
cargo fmt --check || {
    echo "❌ Format check failed. Run: cargo fmt"
    exit 1
}

# Lint check
echo "🎯 Running clippy..."
cargo clippy --all-targets -- -D warnings || {
    echo "❌ Clippy found issues"
    exit 1
}

# Secret check
echo "🔐 Scanning for secrets..."
git grep -E '(password|token|secret|api_key).*=.*["\x27]' -- src/ && {
    echo "❌ Secrets detected in code!"
    exit 1
} || true

echo "✅ All pre-commit checks passed!"
exit 0
```

**Installation:**

```bash
chmod +x .git/hooks/pre-commit
```

---

## Environment Variables (Secrets)

Configure these in GitHub Actions or GitLab CI:

```
SLACK_WEBHOOK_URL     # For notifications
DOCKER_REGISTRY_URL   # Docker registry
DOCKER_USERNAME       # Registry credentials
DOCKER_PASSWORD       # Registry credentials
DATABASE_URL_STAGING  # Staging DB connection
NATS_URL_STAGING      # Staging NATS server
```

---

## Deployment Checklist

Before deploying to production:

- [ ] All tests passing
- [ ] Code review approved
- [ ] Documentation updated
- [ ] Security audit completed
- [ ] Performance baseline acceptable
- [ ] Rollback plan documented
- [ ] Team notified

---

**Last Updated:** 2026-02-15  
**Version:** 1.0
