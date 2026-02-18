#!/usr/bin/env bash
set -euo pipefail

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ─── PIDs tracking ────────────────────────────────────────────────────────────
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo -e "\n${YELLOW}Arrêt des services...${RESET}"
    if [[ -n "$BACKEND_PID" ]]; then
        kill "$BACKEND_PID" 2>/dev/null || true
        echo -e "${RED}  Backend stoppé${RESET}"
    fi
    if [[ -n "$FRONTEND_PID" ]]; then
        kill "$FRONTEND_PID" 2>/dev/null || true
        echo -e "${RED}  Frontend stoppé${RESET}"
    fi
    echo
    read -p "Appuyez sur Entrée pour fermer..."
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# ─── Header ───────────────────────────────────────────────────────────────────
echo -e "${BOLD}${CYAN}"
echo "  ███████╗ ██████╗ ██╗  ██╗ ██████╗ ██╗   ██╗██╗     "
echo "  ██╔════╝██╔═══██╗██║ ██╔╝██╔═══██╗██║   ██║██║     "
echo "  ███████╗██║   ██║█████╔╝ ██║   ██║██║   ██║██║     "
echo "  ╚════██║██║   ██║██╔═██╗ ██║   ██║██║   ██║██║     "
echo "  ███████║╚██████╔╝██║  ██╗╚██████╔╝╚██████╔╝███████╗"
echo "  ╚══════╝ ╚═════╝ ╚═╝  ╚═╝ ╚═════╝  ╚═════╝ ╚══════╝"
echo -e "${RESET}"
echo -e "${BOLD}  Démarrage de l'environnement de développement...${RESET}\n"

# ─── Charger les outils ──────────────────────────────────────────────────────

# Check Rust
if ! command -v cargo &> /dev/null; then
    [[ -f "$HOME/.cargo/env" ]] && source "$HOME/.cargo/env"
fi

# Check Node.js (Supporte NVM ou installation système)
if ! command -v npm &> /dev/null; then
    export NVM_DIR="$HOME/.nvm"
    [[ -s "$NVM_DIR/nvm.sh" ]] && source "$NVM_DIR/nvm.sh"
fi

if ! command -v npm &> /dev/null; then
    echo -e "${RED}Node.js (npm) non trouvé. Veuillez l'installer.${RESET}"
    exit 1
fi

# ─── Vérifier Docker + services infra ────────────────────────────────────────
echo -e "${BLUE}[1/3] Vérification des services Docker...${RESET}"
if ! docker compose ps --services --filter "status=running" 2>/dev/null | grep -qE "postgres|redis|nats"; then
    echo -e "${YELLOW}  Services Docker absents — démarrage automatique...${RESET}"
    docker compose up -d postgres redis nats
    echo -n "  Attente de PostgreSQL"
    count=0
    until docker exec sokoul-postgres-1 pg_isready -U sokoul -q 2>/dev/null || [ $count -gt 30 ]; do
        echo -n "."
        sleep 1
        count=$((count+1))
    done
    if [ $count -gt 30 ]; then
        echo -e " ${YELLOW}(timeout, on continue quand même)${RESET}"
    else
        echo -e " ${GREEN}OK${RESET}"
    fi
else
    echo -e "${GREEN}  PostgreSQL, Redis, NATS : actifs${RESET}"
fi

# ─── Backend Rust ─────────────────────────────────────────────────────────────
echo -e "\n${BLUE}[2/3] Démarrage du backend Rust...${RESET}"
cd "$SCRIPT_DIR"

# Compiler d'abord pour avoir un feedback rapide
if [[ "${1:-}" == "--build" ]]; then
    echo -e "  ${YELLOW}Compilation (cargo build)...${RESET}"
    cargo build 2>&1 | sed "s/^/  ${YELLOW}[BACKEND]${RESET} /"
fi

cargo run 2>&1 | sed $'s/^/\033[33m[BACKEND]\033[0m /' &
BACKEND_PID=$!
echo -e "${GREEN}  Backend lancé (PID: $BACKEND_PID)${RESET}"

# Attendre que le backend soit prêt
echo -n "  Attente du backend (port 3000)"
for i in $(seq 1 30); do
    if curl -sf http://127.0.0.1:3000/health >/dev/null 2>&1; then
        echo -e " ${GREEN}OK${RESET}"
        break
    fi
    echo -n "."
    sleep 1
    if [[ $i -eq 30 ]]; then
        echo -e " ${YELLOW}(timeout — vérifier les logs ci-dessous)${RESET}"
    fi
done

# ─── Frontend SvelteKit ───────────────────────────────────────────────────────
echo -e "\n${BLUE}[3/3] Démarrage du frontend SvelteKit...${RESET}"
cd "$SCRIPT_DIR/dashboard"
if [ ! -d "node_modules" ]; then
    echo -e "  ${YELLOW}Installation des dépendances (npm install)...${RESET}"
    npm install
fi
npm run dev 2>&1 | sed $'s/^/\033[36m[FRONTEND]\033[0m /' &
FRONTEND_PID=$!
echo -e "${GREEN}  Frontend lancé (PID: $FRONTEND_PID)${RESET}"

# ─── Résumé ───────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${GREEN}  Sokoul est démarré !${RESET}"
echo -e "  ${CYAN}Frontend  :${RESET} http://localhost:5173"
echo -e "  ${CYAN}Backend   :${RESET} http://localhost:3000"
echo -e "  ${CYAN}API Health:${RESET} http://localhost:3000/health"
echo -e "  ${CYAN}Métriques :${RESET} http://localhost:3000/metrics"
echo -e "\n  ${YELLOW}Ctrl+C pour tout stopper${RESET}\n"

# ─── Garder le script actif et afficher les logs ──────────────────────────────
wait $BACKEND_PID $FRONTEND_PID
