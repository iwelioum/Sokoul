# CI/CD Implementation Guide - SOKOUL v2

**Date:** 2026-02-15  
**Status:** ✅ Implementation Ready

---

## 📁 Files Created

### 1. GitHub Actions Workflow
**File:** `.github/workflows/ci.yml`

**Jobs Included:**
- ✅ **Lint** - Format + Clippy checks
- ✅ **Test** - Matrix build (stable, beta)
- ✅ **Security** - Cargo audit + secret detection
- ✅ **Build** - Release binary compilation
- ✅ **Docker** - Docker image build (main branch)
- ✅ **Coverage** - Code coverage reporting
- ✅ **Dependencies** - Outdated dependency check
- ✅ **Docs** - Documentation build + doc tests
- ✅ **Status** - Final pipeline status check

**Features:**
- Concurrency limit (cancel old runs)
- Smart caching (Cargo + target)
- Parallel execution where possible
- Artifact upload (binary, 30-day retention)

**Trigger Events:**
- Push to `main`, `develop`
- Pull requests to `main`
- Weekly schedule (Sundays)

---

### 2. Pre-commit Hook Script
**File:** `.git/hooks/pre-commit`

**Checks:**
1. Format check (`cargo fmt --check`)
2. Lint check (`cargo clippy -- -D warnings`)
3. Security audit (`cargo audit`)
4. Secret detection (hardcoded passwords/API keys)
5. Test execution (`cargo test --all`)
6. Protected files (`.env`, `*.key`, etc.)

**Usage:**
```bash
# Make executable
chmod +x .git/hooks/pre-commit

# Now runs automatically before each commit
git commit -m "My changes"
```

**Installation:**
- This file is automatically used by git after creation
- Optional: configure git to run it:
  ```bash
  git config core.hooksPath .git/hooks
  ```

---

### 3. Release Script
**File:** `scripts/release.sh`

**Features:**
1. Version validation (semver format, higher than current)
2. Pre-release checks (format, lint, tests, audit)
3. Version bump in Cargo.toml
4. CHANGELOG.md update
5. Git commit + tag creation
6. Binary build
7. Checksum generation

**Usage:**
```bash
# Make executable
chmod +x scripts/release.sh

# Create a release
./scripts/release.sh 0.3.0

# Script will:
# 1. Validate version format and check it's higher
# 2. Run all pre-release checks
# 3. Update Cargo.toml and CHANGELOG.md
# 4. Create git commit and tag
# 5. Build release binary
# 6. Generate SHA256 checksum
```

**Next Steps After Script:**
```bash
# 1. Review changes
git show v0.3.0

# 2. Push to GitHub
git push origin main
git push origin v0.3.0

# 3. Create GitHub release manually
# Or use GitHub CLI:
gh release create v0.3.0 \
  --title "v0.3.0" \
  --body "See CHANGELOG.md for details"
```

---

## 🚀 Setup Instructions

### Step 1: GitHub Actions (Automatic)

The workflow will automatically run when:
1. You push to `main` or `develop`
2. You open a PR to `main`
3. Every Sunday at midnight UTC

**To manually trigger:**
```bash
# Push to main
git push origin main

# Or create a PR
gh pr create --base main
```

### Step 2: Pre-commit Hooks (Manual)

```bash
# Make the hook executable
chmod +x .git/hooks/pre-commit

# Test it
git commit --allow-empty -m "Test pre-commit hook"

# Should see checks running automatically
```

### Step 3: Release Process (Manual)

```bash
# 1. Create release
./scripts/release.sh 0.3.0

# 2. Push to GitHub
git push origin main
git push origin v0.3.0

# 3. Create GitHub release
# https://github.com/sokoul/sokoul/releases/new?tag=v0.3.0

# 4. Upload artifacts:
# - target/release/sokoul (binary)
# - sokoul-v0.3.0-checksums.txt (checksums)
```

---

## 📊 Pipeline Flow

```
developer commits code
         ↓
   ↙─────┴─────╖
   ↓            ↓
[Pre-commit Hook]  (local)
   ↓
Format check ✓
Lint check ✓
Security audit ✓
Secret detection ✓
Tests ✓
Protected files ✓
   ↓
git push origin
   ↓
GitHub Actions (CI/CD)
   ↙────┬────┬────┬────┬────┬────┬────╖
   ↓    ↓    ↓    ↓    ↓    ↓    ↓    ↓
[Lint][Test][Security][Build][Docker][Coverage][Deps][Docs]
   ↓    ↓    ↓    ↓    ↓    ↓    ↓    ↓
   ↘────┬────┬────┬────┬────┬────┬────╜
         ↓
   [Status Check]
         ↓
   ✓ All jobs passed
         ↓
    PR approved
         ↓
    Merge to main
         ↓
   (Optional) Release
```

---

## 🔍 Monitoring

### GitHub Actions Dashboard
```
https://github.com/sokoul/sokoul/actions
```

Check status of:
- All workflow runs
- Individual job logs
- Coverage reports
- Build artifacts

### Local Testing

Before pushing:
```bash
# Run all checks locally
cargo test --all
cargo clippy -- -D warnings
cargo fmt --check
cargo audit
```

### Release Monitoring

After tagging:
1. Check workflow runs at Actions tab
2. Verify Docker image build (if on main)
3. Download binary artifact
4. Verify checksum: `sha256sum -c sokoul-v0.3.0-checksums.txt`

---

## 📋 Checklist for GitHub Actions Setup

- [ ] `.github/workflows/ci.yml` exists
- [ ] GitHub repository settings:
  - [ ] Branch protection enabled for `main`
  - [ ] Require status checks: `lint`, `test`, `security`
  - [ ] Require PR reviews (optional): 1 approval
  - [ ] Dismiss stale PR reviews: enabled
  - [ ] Require branches up to date: enabled
- [ ] Secrets configured (if needed):
  - [ ] `CODECOV_TOKEN` (optional, for coverage)
  - [ ] Slack webhook (optional, for notifications)

---

## 🛠️ Customization

### Add More Checks

Edit `.github/workflows/ci.yml` to add:

```yaml
  check_migrations:
    name: Check Migrations
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate migrations
        run: |
          # Your migration check here
```

### Change Test Matrix

```yaml
strategy:
  matrix:
    rust: [stable, beta, nightly]
    os: [ubuntu-latest, macos-latest, windows-latest]
```

### Add Slack Notifications

```yaml
  notify:
    name: Notify Slack
    runs-on: ubuntu-latest
    if: always()
    steps:
      - uses: 8398a7/action-slack@v3
        with:
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
          status: ${{ job.status }}
```

---

## 🐛 Troubleshooting

### Pre-commit Hook Not Running
```bash
# Check permissions
ls -la .git/hooks/pre-commit

# Should be executable (x permission)
# If not, run:
chmod +x .git/hooks/pre-commit
```

### Workflow Not Triggering
- Check workflow file syntax (YAML)
- Verify branch names match (main, develop)
- Check `on:` triggers
- Push to correct branch

### Tests Failing in CI
```bash
# Run locally first
cargo test --all

# If passes locally but fails in CI:
# Check for OS-specific code
# Verify dependencies are up to date
# Check for hardcoded paths
```

### Coverage Not Uploading
- Ensure `CODECOV_TOKEN` is set in GitHub secrets
- Or set `fail_ci_if_error: false` to not block

---

## 📞 Next Steps

1. **Commit and Push**
   ```bash
   git add .github/workflows/ci.yml .git/hooks/pre-commit scripts/release.sh
   git commit -m "feat: add CI/CD automation"
   git push origin main
   ```

2. **Watch First Run**
   - Go to GitHub Actions tab
   - Watch all jobs complete
   - Check for any failures

3. **Set Branch Protection**
   - Go to Settings → Branches
   - Add rule for `main` branch
   - Require status checks to pass

4. **Create First Release**
   ```bash
   ./scripts/release.sh 0.3.0
   git push origin main v0.3.0
   # Then create GitHub release manually
   ```

5. **Monitor Regularly**
   - Check Actions for failures
   - Review test coverage
   - Update dependencies

---

## 📚 Related Documentation

- **TEST_FRAMEWORK_SUMMARY.md** - Complete test framework overview
- **CI_CD_TEMPLATE.md** - Original templates used
- **TEST_EXECUTION_GUIDE.md** - How to run tests locally
- **GEMINI.md** - Master test plan

---

**Status:** ✅ Ready to deploy  
**Next:** Push changes to GitHub and monitor first workflow run
