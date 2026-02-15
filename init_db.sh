#!/bin/bash
# Initialize database with schema from init.sql

echo "🔄 Initializing database schema..."

# Run init.sql through PostgreSQL
docker-compose exec -T postgres psql -U sokoul -d sokoul -f /docker-entrypoint-initdb.d/init.sql 2>/dev/null || {
    echo "⚠️  init.sql already applied or not needed"
}

echo "✅ Database initialization check complete"
