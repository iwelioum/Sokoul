#!/usr/bin/env bash
# macOS counterpart of API/start-backend.ps1.
# Starts the whole local Sokoul backend in Docker: MySQL, Redis, Mainapi (25565), proxiesembed (25569), the watch
# party (25566). The historical web site (8088) is opt-in with --with-web.
# Settings live in ~/Library/Application Support/SokoulApi/local.env (never printed).
# Usage: API/start-backend.sh [--stop | --status] [--with-web] [--lan-address 192.168.1.20]
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
stop=0; status=0; with_web=0; lan=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --stop) stop=1 ;;
        --status) status=1 ;;
        --with-web) with_web=1 ;;
        --lan-address) lan="${2:-}"; shift ;;
        -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
    shift
done

[[ "$(uname -s)" == "Darwin" ]] || { echo "This script is for macOS. On Windows use API/start-backend.ps1." >&2; exit 1; }
for brew_bin in /opt/homebrew/bin /usr/local/bin; do
    [[ -d "$brew_bin" && ":$PATH:" != *":$brew_bin:"* ]] && PATH="$brew_bin:$PATH"
done
export PATH

# The Node helpers (init-local-schema.js, seed-local-key.js) and the app look for SokoulApi under LOCALAPPDATA.
export LOCALAPPDATA="$HOME/Library/Application Support"
dir="$LOCALAPPDATA/SokoulApi"
env_file="$dir/local.env"
compose_file="$root/docker-compose.yml"
export SOKOUL_ENV_FILE="$env_file"

compose() {
    local options=(--env-file "$env_file" -f "$compose_file")
    [[ $with_web -eq 1 ]] && options+=(--profile web)
    docker compose "${options[@]}" "$@" || { echo "Docker Compose failed. Check Docker Desktop and run this script with --status." >&2; exit 1; }
}

if [[ $stop -eq 1 && $status -eq 1 ]]; then echo "Choose either --stop or --status." >&2; exit 1; fi
command -v docker >/dev/null 2>&1 || { echo "Docker Desktop is required: https://www.docker.com/products/docker-desktop/" >&2; exit 1; }

if [[ $stop -eq 1 || $status -eq 1 ]]; then
    [[ -f "$env_file" ]] || { echo "Backend is not configured. Run this script without --status or --stop first." >&2; exit 1; }
    if [[ $stop -eq 1 ]]; then compose --profile web down; exit 0; fi
    compose ps --all
    unhealthy=0
    for service in Mainapi:25565 Proxy:25569 WatchParty:25566; do
        name="${service%%:*}"; port="${service##*:}"
        if code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$port/health")"; then
            echo "$name: HTTP $code"
        else
            echo "$name: unavailable or not healthy"; unhealthy=1
        fi
    done
    [[ $unhealthy -eq 0 ]] || { echo "One or more backend HTTP health checks failed." >&2; exit 1; }
    exit 0
fi

command -v node >/dev/null 2>&1 || { echo "Node.js is required for schema initialization: brew install node" >&2; exit 1; }
docker info --format '{{.ServerVersion}}' >/dev/null 2>&1 || { echo "Docker Desktop is not ready. Start it and retry." >&2; exit 1; }
mkdir -p "$dir"
chmod 700 "$dir"

new_secret() { openssl rand -base64 "${1:-24}" | tr '+/=' 'abc' | tr -d '\n'; }

# The address the app (PC or TV) uses to reach the media proxy: this Mac's address on the network, found again at each
# start because the router may change it.
if [[ -n "$lan" ]]; then
    [[ "$lan" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { echo "--lan-address must be an IPv4 address of this Mac." >&2; exit 1; }
else
    iface="$(route -n get default 2>/dev/null | awk '/interface:/{print $2}')"
    [[ -n "$iface" ]] && lan="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    [[ -n "$lan" ]] || lan="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
fi
[[ -n "$lan" ]] || lan="127.0.0.1"

# local.env: keep existing values (secrets are generated once), refresh the public addresses.
lines=()
if [[ -f "$env_file" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do lines+=("$line"); done < "$env_file"
fi
first_run=$(( ${#lines[@]} == 0 ))
set_value() { # name value [only-if-missing]
    local name="$1" value="$2" only_if_missing="${3:-}" i
    # ${lines[@]+...}: macOS's bash 3.2 treats an empty array as unbound under set -u.
    for i in ${lines[@]+"${!lines[@]}"}; do
        if [[ "${lines[$i]}" == "$name="* ]]; then
            [[ -n "$only_if_missing" ]] || lines[$i]="$name=$value"
            return
        fi
    done
    lines+=("$name=$value")
}
if [[ $first_run -eq 1 ]]; then
    # Same TMDB key lookup as the app's build. Never printed.
    tmdb=""
    for file in "${SOKOUL_KEYS_FILE:-}" "$root/../local.properties"; do
        if [[ -n "$file" && -f "$file" ]]; then
            tmdb="$(grep -E '^\s*sokoul\.key\.tmdb\s*=' "$file" | head -n1 | cut -d= -f2- | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')" || true
            [[ -n "$tmdb" ]] && break
        fi
    done
    set_value TMDB_API_KEY "$tmdb"
fi
set_value PORT 25565 missing
set_value NODE_ENV development missing
set_value NUM_WORKERS 2 missing
set_value DB_HOST 127.0.0.1 missing
set_value DB_PORT 33069 missing
set_value DB_USER root missing
set_value DB_PASSWORD "$(new_secret)" missing
set_value DB_NAME sokoul missing
set_value DB_POOL_CONNECTION_LIMIT 10 missing
set_value DB_POOL_QUEUE_LIMIT 200 missing
set_value REDIS_HOST 127.0.0.1 missing
set_value REDIS_PORT 63799 missing
set_value REDIS_PASSWORD "$(new_secret)" missing
set_value JWT_SECRET "$(new_secret 48)" missing
set_value MEDIA_SIGNING_SECRET "$(new_secret 48)" missing
set_value INTERNAL_API_KEY "$(new_secret 32)" missing
set_value VAPID_PUBLIC_KEY "" missing
set_value VAPID_PRIVATE_KEY "" missing
set_value VAPID_SUBJECT "" missing
set_value PUBLIC_API_BASE "http://${lan}:25565"
set_value PROXY_SERVER_URL "http://${lan}:25569/proxy"
set_value PROXIESEMBED_PUBLIC_URL "http://${lan}:25569"
set_value WATCHPARTY_PUBLIC_URL "http://${lan}:25566"
set_value SITE_PUBLIC_URL "http://${lan}:8088"
set_value PROXY_BASE "http://${lan}:25569"

# Add the keys of local.env.template that local.env lacks, with their comments. Existing values are never touched.
template="$root/local.env.template"
if [[ -f "$template" ]]; then
    pending=()
    while IFS= read -r line || [[ -n "$line" ]]; do
        if [[ "$line" =~ ^([A-Z0-9_]+)= ]]; then
            key="${BASH_REMATCH[1]}" present=0
            for existing in ${lines[@]+"${lines[@]}"}; do [[ "$existing" == "$key="* ]] && { present=1; break; }; done
            if [[ $present -eq 0 ]]; then
                lines+=(${pending[@]+"${pending[@]}"} "$line")
            fi
            pending=()
        elif [[ -z "$line" ]]; then
            pending=("")
        else
            pending+=("$line")
        fi
    done < <(sed -n '/^# =====/,$p' "$template")
fi
( umask 077; printf '%s\n' "${lines[@]}" > "$env_file" )

# Report legacy native services occupying backend ports; never terminate unrelated processes.
# Docker's own relay is left alone.
for port in 25565 25566 25569; do
    while read -r pid; do
        [[ -n "$pid" ]] || continue
        pname="$(ps -p "$pid" -o comm= 2>/dev/null | xargs basename 2>/dev/null || true)"
        case "$pname" in
            node|python|python3|Python)
                echo "Port $port is occupied by $pname (PID $pid). Stop that service explicitly before starting Docker." >&2
                exit 1 ;;
        esac
    done < <(lsof -nP -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u)
done

# The Node helpers need the dependencies of Mainapi (mysql2) and of the extraction runtime (esbuild): installed once.
# --ignore-scripts: the host only runs pure-JS helpers; Mainapi's native modules (better-sqlite3...) are built inside
# its Docker image, and their old node-gyp fails on recent Node/Python versions.
if [[ ! -d "$root/Mainapi/node_modules/mysql2" ]]; then
    echo "Installing Mainapi dependencies for the helper scripts (once)..."
    (cd "$root/Mainapi" && npm ci --ignore-scripts --no-audit --no-fund --loglevel=error)
fi
if [[ ! -d "$root/../extraction/node_modules/esbuild" ]]; then
    echo "Installing extraction runtime dependencies (once)..."
    (cd "$root/../extraction" && npm ci --ignore-scripts --no-audit --no-fund --loglevel=error)
fi

echo "Database and cache..."
compose up -d --wait mysql redis
# Mainapi expects tables it does not create itself; then the access key the app uses.
node "$root/init-local-schema.js" || { echo "Schema init failed." >&2; exit 1; }
node "$root/seed-local-key.js" || { echo "Access key seeding failed." >&2; exit 1; }

echo "Services (built on first run, a few minutes)..."
build_context="$(node "$root/prepare-build-context.js")"
[[ -n "$build_context" ]] || { echo "Backend build context preparation failed." >&2; exit 1; }
export SOKOUL_MAINAPI_CONTEXT="$build_context/Mainapi"
export SOKOUL_PROXY_CONTEXT="$build_context/proxiesembed"
export SOKOUL_WATCHPARTY_CONTEXT="$build_context/watchpartyAPI"
services=(mainapi proxiesembed watchparty)
[[ $with_web -eq 1 ]] && services+=(web)
compose up -d --build --wait --wait-timeout 180 "${services[@]}"
rm -rf "$build_context"

echo "Ready: Mainapi http://${lan}:25565  proxiesembed http://${lan}:25569  watch party http://${lan}:25566"
[[ $with_web -eq 1 ]] && echo "Optional legacy site: http://${lan}:8088"
if [[ "$lan" == "127.0.0.1" ]]; then
    echo "Warning: no LAN address found. TV access requires --lan-address with this Mac's IPv4 address." >&2
fi
exit 0
