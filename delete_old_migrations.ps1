# Delete old migration files - Keep only create_schema.sql

$dir = "C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations"

$toDelete = @(
    "20240101000000_init.sql",
    "20260214000000_favorites.sql",
    "20260214000001_watchlist.sql",
    "20260214000002_watch_history.sql",
    "20260215000000_fix_favorites_schema.sql",
    "README.md"
)

Write-Host "Deleting old migrations..."
foreach ($file in $toDelete) {
    $path = Join-Path $dir $file
    if (Test-Path $path) {
        Write-Host "❌ Deleting: $file"
        Remove-Item $path -Force
    }
}

Write-Host ""
Write-Host "✅ Remaining files:"
Get-ChildItem $dir -Filter "*.sql" | Select-Object Name

Write-Host ""
Write-Host "🔄 Now run:"
Write-Host "  docker-compose down -v"
Write-Host "  docker-compose up -d"
Write-Host "  cargo run"
