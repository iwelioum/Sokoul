@echo off
REM Neutralize/delete problematic SQL migrations and clear sqlx records (run from project root)
cd /d "%~dp0"
echo Neutralizing migration files in %CD%\migrations
cd "C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations" 2>nul || (
  echo Migrations folder not found or empty. Exiting.
  exit /b 0
)

if exist "20240101000000_init.sql" del "20240101000000_init.sql"
if exist "20260214000000_favorites.sql" del "20260214000000_favorites.sql"
if exist "20260214000001_watchlist.sql" del "20260214000001_watchlist.sql"
if exist "20260214000002_watch_history.sql" del "20260214000002_watch_history.sql"
if exist "20260215000000_fix_favorites_schema.sql" del "20260215000000_fix_favorites_schema.sql"
if exist "README.md" del "README.md"
echo Done deleting listed files (if present).

REM Clear sqlx migration records in the running postgres container (requires docker-compose with service named 'postgres')
echo Executing SQL to clear _sqlx_migrations for versions >= 20260214000000 (if running in Docker)...
docker-compose exec postgres psql -U sokoul -d sokoul -c "DELETE FROM _sqlx_migrations WHERE version >= 20260214000000;" || (
  echo Could not run docker-compose exec postgres (container may not be running). Run the SQL manually if needed.
)
echo You can now restart services: docker-compose down -v && docker-compose up -d && cargo run
echo Neutralize script finished.
pause