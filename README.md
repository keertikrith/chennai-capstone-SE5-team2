# Enterprise Trading Platform (Team 2)

Enterprise Trading Platform (ETP) is a multi-service trading system where customers can authenticate, place orders, track positions, review portfolio and P&L, manage alerts and notifications, and use a modern Angular frontend.

The platform combines:
- transactional order processing (Trade API + Executor)
- authentication and session/token flows (Auth Service)
- event-driven processing (Kafka topics and consumers)
- portfolio pricing and P&L logic
- browser UI (Angular 21)
- analytics pipeline assets (ETL_Layer)

This README is for the full project, not only Sprint 10.

## What is in this repository

### Core services
- `services/trade-api` (Spring Boot, Java 21, MyBatis, Kafka)
- `services/executor` (Spring Boot, Java 21)
- `services/auth_service` (NestJS, TypeScript, PostgreSQL)
- `frontend` (Angular 21 standalone app)

### Platform infrastructure
- `docker-compose.yml` (platform orchestration with profiles)
- `databases/postgreSQL/migrations` (schema evolution)
- `infrastructure/kafka/scripts/create-topics.sh` (topic provisioning)

### Contracts and architecture records
- `contracts/` (OpenAPI and topic/data contracts)
- `decision-log/` (architecture and design decisions)
- `security-review/Sprint10_Security_Review.md` (combined OWASP review)

## Sprint delivery map (project-wide)

The repository contains work delivered across multiple sprints. The primary milestones are:

| Sprint | Main outcome | Key locations |
|---|---|---|
| Sprint 3 | Operational transactional schema foundations | `databases/postgreSQL/migrations/001_initial_schema.sql` |
| Sprint 4 | Analytics schema and ETL/dashboard assets | `contracts/analytics-schema.sql`, `ETL_Layer/` |
| Sprint 6 | Core Trade REST API + account/order routes | `services/trade-api`, `contracts/trade-api.yaml` |
| Sprint 7 | Event-driven execution, Kafka topics, consumers | `services/executor`, `contracts/kafka-topics.md`, `infrastructure/kafka/` |
| Sprint 8 | Auth service + JWT lifecycle and validation | `services/auth_service`, `contracts/auth-api.yaml` |
| Sprint 9 | Angular UI with generated auth client and guarded flows | `frontend/` |
| Sprint 10 | Extension modules and UI enhancements | `services/trade-api/src/main/java/com/capstone/etp/{preferences,notifications,alerts,portfolio,...}` and `docs/sprint-10/` |

For contract lineage, see `contracts/README.md` (`First used` column).

## Sprint 10 extension modules (inside Trade API)

These are packages inside `services/trade-api`, not separate deployables:

| Module | Package | Contract | README |
|---|---|---|---|
| Customer preferences | `preferences` | `contracts/preferences-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/preferences/README.md` |
| Customer notifications | `notifications` | `contracts/notifications-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/notifications/README.md` |
| Watchlists & price alerts | `alerts` | `contracts/alerts-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/alerts/README.md` |
| Portfolio & P&L | `portfolio` | `contracts/portfolio-api.yaml` | `services/trade-api/src/main/java/com/capstone/etp/portfolio/README.md` |

Additional modules:
- `services/trade-api/src/main/java/com/capstone/etp/advice/README.md`
- `services/trade-api/src/main/java/com/capstone/etp/automation/README.md`
- `services/trade-api/src/main/java/com/capstone/etp/assistant/README.md`

## High-level architecture

1. Users sign in through the Auth Service and receive JWT tokens.
2. Frontend calls Trade API routes with bearer token for account-scoped operations.
3. Trade API writes transactional data to PostgreSQL and publishes/consumes Kafka events.
4. Executor processes eligible orders using market data and emits trade events.
5. Extension modules consume events for notifications and realised P&L booking.
6. Portfolio module prices holdings via Fauxnance (server-side only), with batching/caching/stale handling.

## Compose profiles

`docker-compose.yml` defines multiple profiles:

| Profile | Purpose |
|---|---|
| `platform` | Full runtime stack (Postgres, Auth Service, Trade API, Executor, Kafka, topic init, Kafbat UI) |
| `stub` | Lightweight auth stub service |
| `sonar` | SonarQube server |
| `sonar-scan` | Scanner containers for executor, trade-api, and ETL layer |

## Prerequisites

- Docker + Docker Compose
- Java 21 (for local service runs)
- Maven 3.9+
- Node.js 20+ (frontend) and Node.js 18+ (auth service)
- npm
- PostgreSQL client tools (`psql`) for manual migration/debug work

## Quick start (recommended: Docker)

From repository root:

```bash
docker compose --profile platform up -d --build
```

Default ports:
- Trade API: `8084`
- Auth Service: `8081`
- Executor: `8085`
- PostgreSQL: `5432`
- Kafka external listener: `9092`
- Kafbat UI: `8086`

Stop platform:

```bash
docker compose --profile platform down
```

Validate compose file and resolved paths:

```bash
docker compose --profile platform config
```

## Environment configuration

`docker-compose.yml` reads environment values (for example from `.env` at repo root).

Common variables:
- `JWT_SECRET` (required for auth and trade-api token verification)
- `JWT_ISSUER` (default `auth-service`)
- `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `FAUXNANCE_BASE_URL`, `FAUXNANCE_API_KEY`
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`
- `KAFKA_BOOTSTRAP_SERVERS` (for local non-compose service runs)

Helper script for remote Linux VM setup:

```bash
./setup-jwt-docker.sh <username> <vm-ip>
```

## Running services locally (without Docker orchestration)

### Frontend

```bash
cd frontend
npm ci
npm start
```

Also available:

```bash
npm run build
npm test
npm run e2e:install
npm run e2e
npm run scan:bundle
```

### Auth service

```bash
cd services/auth_service
npm install
npm run start:dev
```

Tests:

```bash
npm test
npm run test:cov
```

### Trade API

```bash
cd services/trade-api
mvn spring-boot:run
```

Tests:

```bash
JWT_SECRET=any-32-byte-secret-for-tests-0000000 mvn test
```

### Executor

```bash
cd services/executor
mvn spring-boot:run
```

## Database migrations

The Compose bootstrap mounts and applies migration SQL files from:
- `databases/postgreSQL/migrations`
- `services/auth_service/migrations`

If using an existing database that predates extension tables, apply needed migration files manually with `psql`.

Current migration chain includes:
- `001_initial_schema.sql`
- `002_add_query_indexes.sql`
- `003_extensions.sql`
- `004_sprint10_ui_enhancements.sql`
- `005_automation.sql`
- `006_automation_history.sql`

## Contracts and API-first development

Contracts are in `contracts/` and are binding between modules/teams.

Useful commands:

```bash
npx @redocly/cli lint contracts/trade-api.yaml
npx @redocly/cli preview-docs contracts/trade-api.yaml
```

For frontend generated clients:

```bash
cd frontend
npm run generate:clients
```

## Testing strategy

- **Frontend**: Angular unit tests + Playwright e2e.
- **Trade API**: unit tests by default; integration tests for extension chains/kafka paths when configured.
- **Auth service**: Jest tests for auth flows, repositories, and password logic.
- **Executor**: Maven/JUnit tests.
- **ETL_Layer**: Python tests under `ETL_Layer/tests`.

ETL quick run (if working on analytics):

```bash
cd ETL_Layer
python -m pip install -e .[dev]
pytest
```

## Security and operational references

- Combined security review: `security-review/Sprint10_Security_Review.md`
- Decision log: `decision-log/`
- Trade API runbook queries: `services/trade-api/README.md`

## Troubleshooting

- Verify compose configuration:

```bash
docker compose --profile platform config
```

- Check service logs:

```bash
docker compose logs backend
docker compose logs auth-service
docker compose logs trade-executor
```

- Kafka dead letters:
	- inspect `trade-events.DLT` in Kafbat UI (`http://localhost:8086`)

- If frontend calls fail locally:
	- verify proxy settings in `frontend/proxy.conf.json`
	- verify backend/auth ports and JWT configuration

## Related docs

- Sprint 10 seams: `docs/sprint-10/seams.md`
- Sprint 10 demo script: `docs/sprint-10/demo.md`
- Sprint 10 scope/backlog: `docs/sprint10/scope-and-backlog.md`
- Trade API details: `services/trade-api/README.md`
- Auth service details: `services/auth_service/README.md`
- Frontend details: `frontend/README.md`
- Contracts guide: `contracts/README.md`
- Decision template: `decision-log/TEMPLATE.md`

