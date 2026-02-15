#!/bin/bash
# Release automation script for SOKOUL v2
# Usage: ./scripts/release.sh <version>

set -e

RESET='\033[0m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'

if [ -z "$1" ]; then
    echo -e "${RED}Usage: ./scripts/release.sh <version>${RESET}"
    echo "Example: ./scripts/release.sh 0.3.0"
    exit 1
fi

VERSION=$1
CURRENT_VERSION=$(grep '^version' Cargo.toml | head -1 | cut -d'"' -f2)
DATE=$(date +%Y-%m-%d)

echo -e "${YELLOW}🚀 SOKOUL v2 Release: $CURRENT_VERSION → $VERSION${RESET}"

# ============ VALIDATION ============
echo -e "\n${YELLOW}📋 Validating...${RESET}"

# Check version format (semver)
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}❌ Invalid version format. Use: MAJOR.MINOR.PATCH${RESET}"
    exit 1
fi

# Check if version is higher
IFS='.' read -r new_major new_minor new_patch <<< "$VERSION"
IFS='.' read -r curr_major curr_minor curr_patch <<< "$CURRENT_VERSION"

if (( new_major < curr_major || (new_major == curr_major && new_minor < curr_minor) || (new_major == curr_major && new_minor == curr_minor && new_patch <= curr_patch) )); then
    echo -e "${RED}❌ Version must be higher than $CURRENT_VERSION${RESET}"
    exit 1
fi

echo -e "${GREEN}✅ Version validation passed${RESET}"

# ============ PRE-RELEASE CHECKS ============
echo -e "\n${YELLOW}🧪 Running pre-release checks...${RESET}"

# Format
if ! cargo fmt --check; then
    echo -e "${RED}❌ Format check failed. Run: cargo fmt${RESET}"
    exit 1
fi

# Lint
if ! cargo clippy --all -- -D warnings; then
    echo -e "${RED}❌ Lint check failed${RESET}"
    exit 1
fi

# Tests
if ! cargo test --all; then
    echo -e "${RED}❌ Tests failed${RESET}"
    exit 1
fi

# Audit
if ! cargo audit; then
    echo -e "${RED}❌ Security audit failed${RESET}"
    exit 1
fi

echo -e "${GREEN}✅ All pre-release checks passed${RESET}"

# ============ UPDATE CARGO.TOML ============
echo -e "\n${YELLOW}📝 Updating Cargo.toml...${RESET}"
sed -i "s/^version = \".*\"/version = \"$VERSION\"/" Cargo.toml
echo -e "${GREEN}✅ Updated version to $VERSION${RESET}"

# ============ UPDATE CHANGELOG ============
echo -e "\n${YELLOW}📝 Updating CHANGELOG.md...${RESET}"

if [ ! -f CHANGELOG.md ]; then
    cat > CHANGELOG.md << EOF
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

EOF
fi

# Insert new entry at top
{
    echo "## [$VERSION] - $DATE"
    echo ""
    echo "### Added"
    echo "- Add your changes here"
    echo ""
    echo "### Changed"
    echo "- Change log here"
    echo ""
    echo "### Fixed"
    echo "- Fix log here"
    echo ""
    cat CHANGELOG.md
} > CHANGELOG.md.tmp

mv CHANGELOG.md.tmp CHANGELOG.md
echo -e "${GREEN}✅ Updated CHANGELOG.md${RESET}"

# ============ GIT COMMIT ============
echo -e "\n${YELLOW}🔗 Creating git commit...${RESET}"
git add Cargo.toml CHANGELOG.md
git commit -m "chore: release v$VERSION"
echo -e "${GREEN}✅ Created commit${RESET}"

# ============ GIT TAG ============
echo -e "\n${YELLOW}🏷️  Creating git tag...${RESET}"
git tag -a "v$VERSION" -m "Release version $VERSION

Changes logged in CHANGELOG.md"
echo -e "${GREEN}✅ Created tag: v$VERSION${RESET}"

# ============ BUILD RELEASE ============
echo -e "\n${YELLOW}🔨 Building release binary...${RESET}"
cargo build --release
echo -e "${GREEN}✅ Build completed${RESET}"

# ============ CREATE CHECKSUM ============
echo -e "\n${YELLOW}📦 Creating checksums...${RESET}"
BINARY="target/release/sokoul"
if [ -f "$BINARY" ]; then
    sha256sum "$BINARY" > "sokoul-v${VERSION}-checksums.txt"
    echo -e "${GREEN}✅ Created checksums${RESET}"
fi

# ============ SUMMARY ============
echo -e "\n${GREEN}✅ Release prepared successfully!${RESET}"
echo -e "${YELLOW}Next steps:${RESET}"
echo "  1. Review the changes: git show v$VERSION"
echo "  2. Push to GitHub: git push origin main && git push origin v$VERSION"
echo "  3. Create GitHub release at: https://github.com/sokoul/sokoul/releases/new?tag=v$VERSION"
echo "  4. Upload binary: target/release/sokoul"
echo "  5. Upload checksums: sokoul-v${VERSION}-checksums.txt"
echo ""
echo -e "${GREEN}Version $VERSION is ready for release!${RESET}"
