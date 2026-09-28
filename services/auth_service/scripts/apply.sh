#!/usr/bin/env bash

set -euo pipefail

# Load .env if present
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Database connection
# Uses capstone_db by default with auth schema
DATABASE="${TARGET_DATABASE:-${DB_NAME:-capstone_db}}"
AUTH_SCHEMA="${AUTH_DB_SCHEMA:-auth}"

if [ -z "$DATABASE" ]; then
    echo "ERROR: TARGET_DATABASE or DB_NAME is not set"
    exit 1
fi

echo "============================================================"
echo "Auth Service Database Setup"
echo "Using database: $DATABASE"
echo "Using schema: $AUTH_SCHEMA"
echo "============================================================"
echo

# Apply migrations
# Migrations create the auth schema and all auth-related tables
echo "Applying migrations to auth schema..."
echo

if [ ! -d "migrations" ]; then
    echo "ERROR: migrations directory does not exist"
    exit 1
fi

for migration in migrations/*.sql
do
    echo "Applying: $migration"

    psql "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -f "$migration"
    
    echo ""
done

echo "============================================================"
echo "Auth schema setup completed successfully."
echo "============================================================"
