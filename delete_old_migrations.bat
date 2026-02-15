@echo off
REM Delete all migration files except the new one

cd /d C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations

echo Deleting old migration files...

if exist "20240101000000_init.sql" (
    echo Deleting: 20240101000000_init.sql
    del "20240101000000_init.sql"
)

if exist "20260214000000_favorites.sql" (
    echo Deleting: 20260214000000_favorites.sql
    del "20260214000000_favorites.sql"
)

if exist "20260214000001_watchlist.sql" (
    echo Deleting: 20260214000001_watchlist.sql
    del "20260214000001_watchlist.sql"
)

if exist "20260214000002_watch_history.sql" (
    echo Deleting: 20260214000002_watch_history.sql
    del "20260214000002_watch_history.sql"
)

if exist "20260215000000_fix_favorites_schema.sql" (
    echo Deleting: 20260215000000_fix_favorites_schema.sql
    del "20260215000000_fix_favorites_schema.sql"
)

if exist "README.md" (
    echo Deleting: README.md
    del "README.md"
)

echo.
echo Remaining files:
dir /b *.sql

echo.
echo Done!
pause
