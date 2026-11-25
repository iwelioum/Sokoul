<#
Sokoul server helper (Windows). See docs/server.md.

  .\tools\server.ps1 run                      Start the server in this window (gradlew :server:run).
  .\tools\server.ps1 install-service          Print the steps to start it at log-on (installs nothing).
  .\tools\server.ps1 backup [-To <folder>]    Copy sokoul-server.db with a timestamp.
  .\tools\server.ps1 tunnel [-Hostname sokoul.example.com] [-Email you@example.com]
                                              Print the Cloudflare Tunnel + Access steps (runs nothing).

Nothing here prints a key. The config file is $env:SOKOUL_SERVER_CONFIG, or %LOCALAPPDATA%\SokoulServer\server.properties.
#>
param(
    [Parameter(Position = 0)][ValidateSet('run', 'install-service', 'backup', 'tunnel')][string]$Command = 'run',
    [string]$To,
    [string]$Hostname = 'sokoul.example.com',
    [string]$Email = 'you@example.com'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

function Get-ConfigFile {
    if ($env:SOKOUL_SERVER_CONFIG) { return $env:SOKOUL_SERVER_CONFIG }
    return Join-Path $env:LOCALAPPDATA 'SokoulServer\server.properties'
}

# Reads one key of the properties file (env override first), without printing anything.
function Get-Setting([string]$Key, [string]$EnvName, [string]$Default) {
    $fromEnv = [Environment]::GetEnvironmentVariable($EnvName)
    if ($fromEnv) { return $fromEnv }
    $file = Get-ConfigFile
    if (Test-Path $file) {
        foreach ($line in Get-Content $file) {
            if ($line -match "^\s*$([regex]::Escape($Key))\s*[=:]\s*(.*?)\s*$") { return $Matches[1] }
        }
    }
    return $Default
}

function Get-DataDir { Get-Setting 'dataDir' 'SOKOUL_SERVER_DATA_DIR' (Split-Path -Parent (Get-ConfigFile)) }
function Get-Port { Get-Setting 'port' 'SOKOUL_SERVER_PORT' '25570' }

switch ($Command) {
    'run' {
        Push-Location $repo
        try { & .\gradlew.bat :server:run --console=plain } finally { Pop-Location }
    }

    'install-service' {
        $buildRoot = if ($env:SOKOUL_BUILD_ROOT) { $env:SOKOUL_BUILD_ROOT } else { Join-Path $repo 'build-output' }
        $bat = Join-Path $buildRoot 'server\install\server\bin\server.bat'
        @"
Start the Sokoul server at log-on (Windows). Nothing below has been run.

1. Build a standalone copy of the server:
     .\gradlew.bat :server:installDist
   It lands in $(Split-Path -Parent (Split-Path -Parent $bat))

2. Copy that folder somewhere stable outside OneDrive, for example:
     robocopy "$(Split-Path -Parent (Split-Path -Parent $bat))" "$env:LOCALAPPDATA\SokoulServer\app" /MIR

3. Register a task that starts it when you log on (no admin rights needed):
     schtasks /Create /TN "Sokoul server" /SC ONLOGON /RL LIMITED /TR "\"$env:LOCALAPPDATA\SokoulServer\app\bin\server.bat\""

4. Start it now and check it answers:
     schtasks /Run /TN "Sokoul server"
     curl.exe http://127.0.0.1:$(Get-Port)/health

To remove it: schtasks /Delete /TN "Sokoul server" /F
cloudflared runs as its own service: see '.\tools\server.ps1 tunnel'.
"@ | Write-Output
    }

    'backup' {
        $db = Join-Path (Get-DataDir) 'sokoul-server.db'
        if (-not (Test-Path $db)) { throw "No database at $db" }
        $dest = if ($To) { $To } else { Join-Path (Get-DataDir) 'backups' }
        New-Item -ItemType Directory -Force $dest | Out-Null
        $target = Join-Path $dest ("sokoul-server-{0}.db" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
        $sqlite = Get-Command sqlite3 -ErrorAction SilentlyContinue
        if ($sqlite) {
            # Online backup: consistent even while the server is writing (WAL).
            & $sqlite.Source $db ".backup '$($target -replace "'", "''")'"
            if ($LASTEXITCODE -ne 0) { throw "sqlite3 .backup failed" }
        } else {
            $running = Get-NetTCPConnection -State Listen -LocalPort ([int](Get-Port)) -ErrorAction SilentlyContinue
            if ($running) {
                throw "sqlite3 is not installed and the server is running on port $(Get-Port). Stop it first (or install sqlite3, e.g. 'winget install SQLite.SQLite'), then run backup again."
            }
            # Stopped server: the WAL was checkpointed on close; copy any leftover -wal/-shm with it.
            Copy-Item $db $target
            foreach ($suffix in '-wal', '-shm') {
                if (Test-Path "$db$suffix") { Copy-Item "$db$suffix" "$target$suffix" }
            }
        }
        Write-Output "Backup written: $target"
    }

    'tunnel' {
        $port = Get-Port
        @"
Cloudflare Tunnel + Access for the Sokoul server (free plan). Nothing below has been run.
Hostname: $Hostname   Allowed e-mail: $Email   Local port: $port

A. Tunnel (cloudflared, on this PC)
  1. winget install --id Cloudflare.cloudflared
  2. cloudflared tunnel login                       (pick the zone that holds $Hostname)
  3. cloudflared tunnel create sokoul               (note the tunnel UUID it prints)
  4. cloudflared tunnel route dns sokoul $Hostname
  5. Write %USERPROFILE%\.cloudflared\config.yml:
       tunnel: <TUNNEL-UUID>
       credentials-file: C:\Users\<you>\.cloudflared\<TUNNEL-UUID>.json
       ingress:
         - hostname: $Hostname
           service: http://127.0.0.1:$port
         - service: http_status:404
  6. Test it:     cloudflared tunnel run sokoul
     As a service (admin PowerShell): cloudflared service install

B. Access application (Cloudflare Zero Trust dashboard, one.dash.cloudflare.com)
  1. Settings > Authentication > Login methods: keep "One-time PIN".
  2. Access > Applications > Add an application > Self-hosted.
     Name: Sokoul   Domain: $Hostname   Session duration: 1 month.
  3. Policy: Action "Allow", Include > Emails > $Email   (nobody else).
  4. Save, open the application again and copy its "Application Audience (AUD) Tag".
  5. Your team domain is shown under Settings > Custom pages: <team>.cloudflareaccess.com

C. Tell the server (edit $(Get-ConfigFile)):
       access.teamDomain=<team>.cloudflareaccess.com
       access.audience=<AUD tag>
   Restart the server. From now on every request except /health must carry Cloudflare's signed assertion.

D. Check from a phone on mobile data: https://$Hostname/health answers only after the e-mail code,
   and https://$Hostname/api/me answers 401 (no Sokoul account yet).
"@ | Write-Output
    }
}
