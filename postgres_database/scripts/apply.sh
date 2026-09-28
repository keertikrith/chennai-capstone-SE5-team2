#!/usr/bin/env bash

set -euo pipefail

# Load .env if present
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Database connection
# Uses capstone_db by default with trade and auth schemas
DATABASE="${TARGET_DATABASE:-${DB_NAME:-capstone_db}}"


if [ -z "$DATABASE" ]; then
    echo "ERROR: TARGET_DATABASE or POSTGRES_DB is not set"
    exit 1
fi


echo "Using database: $DATABASE"


# Apply migrations
# Migrations create the trade schema and all trade-related tables
echo "Applying migrations to trade schema..."

for migration in migrations/*.sql
do
    echo "Applying: $migration"

    psql "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -f "$migration"
done


# Load seed data
# Seed files must have "SET search_path TO trade, public;" to target the trade schema
echo "Loading seed data into trade schema..."

for seed_file in seed/*.sql
do
    echo "Loading: $seed_file"

    psql "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -f "$seed_file"
done

echo "Running analytical queries on trade schema..."

for query_file in queries/*.sql
do
    echo "===================================="
    echo "Executing: $query_file"
    echo "===================================="

    # Queries run against the trade schema
    psql "$DATABASE" \
        -v ON_ERROR_STOP=1 \
        -a \
        -c "SET search_path TO trade, public;" \
        -f "$query_file"

    echo ""
done





echo "Database setup completed successfully."