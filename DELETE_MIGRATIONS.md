# 🔥 DELETE ALL OLD MIGRATIONS - FINAL

## Files to Delete:

In `C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations\`:

❌ 20240101000000_init.sql
❌ 20260214000000_favorites.sql
❌ 20260214000001_watchlist.sql
❌ 20260214000002_watch_history.sql
❌ 20260215000000_fix_favorites_schema.sql
❌ README.md

Keep ONLY:
✅ 20240101000000_create_schema.sql

---

## Option 1: Automatic (Batch File)

Double-click: `delete_old_migrations.bat`

---

## Option 2: Manual (Windows Explorer)

1. Open: `C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations\`
2. Select all files EXCEPT `20240101000000_create_schema.sql`
3. Delete them

---

## Option 3: Command Prompt

```cmd
cd C:\Users\oumba\Desktop\Sokoul\Sokoul\migrations
del 20240101000000_init.sql
del 20260214000000_favorites.sql
del 20260214000001_watchlist.sql
del 20260214000002_watch_history.sql
del 20260215000000_fix_favorites_schema.sql
del README.md
dir /b *.sql
```

---

## Then Run:

```bash
docker-compose down -v
docker-compose up -d
cargo run
```

---

✅ DONE!
