#!/usr/bin/env bash
# Sokoul server helper (macOS / Linux). See docs/server.md.
#
#   tools/server.sh run                    Start the server in this terminal (gradlew :server:run).
#   tools/server.sh install-service        Print the steps to start it at boot/log-in (installs nothing).
#   tools/server.sh backup [folder]        Copy sokoul-server.db with a timestamp.
#   tools/server.sh tunnel [hostname] [email]
#                                          Print the Cloudflare Tunnel + Access steps (runs nothing).
#
# Nothing here prints a key. Config file: $SOKOUL_SERVER_CONFIG, or ~/.sokoul-server/server.properties.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"

config_file() { echo "${SOKOUL_SERVER_CONFIG:-$HOME/.sokoul-server/server.properties}"; }

# setting <key> <env name> <default>: env first, then the properties file, then the default.
setting() {
    local key="$1" env_name="$2" default="$3" file value
    value="${!env_name:-}"
    if [[ -z "$value" ]]; then
        file="$(config_file)"
        if [[ -f "$file" ]]; then
            value="$(sed -n -E "s/^[[:space:]]*${key//./\\.}[[:space:]]*[=:][[:space:]]*(.*[^[:space:]])[[:space:]]*$/\1/p" "$file" | tail -n 1)"
        fi
    fi
    echo "${value:-$default}"
}
data_dir() { setting dataDir SOKOUL_SERVER_DATA_DIR "$(dirname "$(config_file)")"; }
port() { setting port SOKOUL_SERVER_PORT 25570; }

cmd="${1:-run}"
case "$cmd" in
run)
    cd "$repo" && exec ./gradlew :server:run --console=plain
    ;;

install-service)
    build_root="${SOKOUL_BUILD_ROOT:-$repo/build-output}"
    dist="$build_root/server/install/server"
    cat <<EOF
Start the Sokoul server automatically. Nothing below has been run.

1. Build a standalone copy:   ./gradlew :server:installDist
   It lands in $dist
2. Copy it somewhere stable:  mkdir -p ~/.sokoul-server/app && rsync -a --delete "$dist/" ~/.sokoul-server/app/

macOS (launchd, starts at log-in):
3. Write ~/Library/LaunchAgents/com.sokoul.server.plist:
     <?xml version="1.0" encoding="UTF-8"?>
     <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
     <plist version="1.0"><dict>
       <key>Label</key><string>com.sokoul.server</string>
       <key>ProgramArguments</key><array><string>$HOME/.sokoul-server/app/bin/server</string></array>
       <key>RunAtLoad</key><true/>
       <key>KeepAlive</key><true/>
       <key>StandardErrorPath</key><string>$HOME/.sokoul-server/server.log</string>
     </dict></plist>
4. launchctl load -w ~/Library/LaunchAgents/com.sokoul.server.plist

Linux (systemd user unit, starts at boot with lingering):
3. Write ~/.config/systemd/user/sokoul-server.service:
     [Unit]
     Description=Sokoul server
     After=network-online.target
     [Service]
     ExecStart=%h/.sokoul-server/app/bin/server
     Restart=on-failure
     [Install]
     WantedBy=default.target
4. systemctl --user daemon-reload && systemctl --user enable --now sokoul-server
   loginctl enable-linger "\$USER"      (keeps it running when you are logged out)

Check:  curl http://127.0.0.1:$(port)/health
cloudflared runs as its own service: see 'tools/server.sh tunnel'.
EOF
    ;;

backup)
    db="$(data_dir)/sokoul-server.db"
    [[ -f "$db" ]] || { echo "No database at $db" >&2; exit 1; }
    dest="${2:-$(data_dir)/backups}"
    mkdir -p "$dest"
    target="$dest/sokoul-server-$(date +%Y%m%d-%H%M%S).db"
    if command -v sqlite3 >/dev/null 2>&1; then
        # Online backup: consistent even while the server is writing (WAL).
        sqlite3 "$db" ".backup '${target//\'/\'\'}'"
    else
        if (exec 3<>"/dev/tcp/127.0.0.1/$(port)") 2>/dev/null; then
            echo "sqlite3 is not installed and the server is running on port $(port). Stop it first (or install sqlite3), then run backup again." >&2
            exit 1
        fi
        # Stopped server: the WAL was checkpointed on close; copy any leftover -wal/-shm with it.
        cp "$db" "$target"
        for suffix in -wal -shm; do [[ -f "$db$suffix" ]] && cp "$db$suffix" "$target$suffix"; done
    fi
    echo "Backup written: $target"
    ;;

tunnel)
    host="${2:-sokoul.example.com}"
    email="${3:-you@example.com}"
    p="$(port)"
    cat <<EOF
Cloudflare Tunnel + Access for the Sokoul server (free plan). Nothing below has been run.
Hostname: $host   Allowed e-mail: $email   Local port: $p

A. Tunnel (cloudflared, on the server machine)
  1. Install cloudflared (macOS: brew install cloudflared; Linux: the package from Cloudflare's repository).
  2. cloudflared tunnel login                       (pick the zone that holds $host)
  3. cloudflared tunnel create sokoul               (note the tunnel UUID it prints)
  4. cloudflared tunnel route dns sokoul $host
  5. Write ~/.cloudflared/config.yml:
       tunnel: <TUNNEL-UUID>
       credentials-file: $HOME/.cloudflared/<TUNNEL-UUID>.json
       ingress:
         - hostname: $host
           service: http://127.0.0.1:$p
         - service: http_status:404
  6. Test it:     cloudflared tunnel run sokoul
     As a service: sudo cloudflared service install

B. Access application (Cloudflare Zero Trust dashboard, one.dash.cloudflare.com)
  1. Settings > Authentication > Login methods: keep "One-time PIN".
  2. Access > Applications > Add an application > Self-hosted.
     Name: Sokoul   Domain: $host   Session duration: 1 month.
  3. Policy: Action "Allow", Include > Emails > $email   (nobody else).
  4. Save, open the application again and copy its "Application Audience (AUD) Tag".
  5. Your team domain is shown under Settings > Custom pages: <team>.cloudflareaccess.com

C. Tell the server (edit $(config_file)):
       access.teamDomain=<team>.cloudflareaccess.com
       access.audience=<AUD tag>
   Restart the server. From now on every request except /health must carry Cloudflare's signed assertion.

D. Check from a phone on mobile data: https://$host/health answers only after the e-mail code,
   and https://$host/api/me answers 401 (no Sokoul account yet).
EOF
    ;;

*)
    echo "Usage: tools/server.sh run | install-service | backup [folder] | tunnel [hostname] [email]" >&2
    exit 1
    ;;
esac
