#!/bin/bash
# SOKOUL - Clean Start (ONE migration only)

cd migrations/

# Delete ALL migrations except the new one
for file in *.sql; do
    if [ "$file" != "20240101000000_create_schema.sql" ]; then
        echo "❌ Deleting: $file"
        rm "$file"
    fi
done

echo ""
echo "✅ Only 1 migration file remains:"
ls -la

cd ..

echo ""
echo "🔄 Resetting database..."
docker-compose down -v
docker-compose up -d

echo ""
echo "🚀 Starting server..."
cargo run
