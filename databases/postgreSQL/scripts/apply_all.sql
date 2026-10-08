-- Applies every migration in order, then the seed data.
--
-- Usage (from any directory):
--   psql -U <user> -d <database> -f databases/postgreSQL/scripts/apply_all.sql
--
-- \ir resolves paths relative to this file, so the working directory doesn't matter.
-- psql cannot glob, so a new migration must be added to the list below.

\set ON_ERROR_STOP on

\echo '=== Applying migrations ==='
\ir ../migrations/001_initial_schema.sql
\ir ../migrations/002_add_query_indexes.sql
\ir ../migrations/003_extensions.sql
\ir ../migrations/004_sprint10_ui_enhancements.sql
\ir ../migrations/005_automation.sql
\ir ../migrations/006_automation_history.sql
\ir ../migrations/007_mcp_access.sql

\echo '=== Loading seed data ==='
\ir ../seed/001_seed.sql

\echo '=== Done ==='
