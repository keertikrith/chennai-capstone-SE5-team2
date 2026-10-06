# Trade REST API

Spring Boot 3.5 / Java 21 / MyBatis / Kafka. The Sprint 6 order and account routes, plus the four Sprint 10 extension modules, each a package in this one service:

| Module | Package | Brief | README |
|---|---|---|---|
| Customer preferences | `preferences` | `contracts/preferences-api.yaml` | [README](src/main/java/com/capstone/etp/preferences/README.md) |
| Customer notifications | `notifications` | `contracts/notifications-api.yaml` | [README](src/main/java/com/capstone/etp/notifications/README.md) |
| Watchlists and price alerts | `alerts` | `contracts/alerts-api.yaml` | [README](src/main/java/com/capstone/etp/alerts/README.md) |
| Portfolio and P&L | `portfolio` | `contracts/portfolio-api.yaml` | [README](src/main/java/com/capstone/etp/portfolio/README.md) |

Design notes: [`docs/sprint-10/seams.md`](../../docs/sprint-10/seams.md) (the two seams), [`decision-log/`](../../decision-log), [`security-review/`](../../security-review/Sprint10_Security_Review.md), [demo script](../../docs/sprint-10/demo.md).

## Run it

```
docker compose --profile platform up -d --build      # from the repository root
```

A database created before the extension tables existed needs them once (idempotent, safe to re-run):

```
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f databases/postgreSQL/migrations/003_extensions.sql
```

The API listens on `SERVER_PORT` (default 8084). Swagger-style contracts are in `contracts/`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | none (required) | HS256 key shared with the auth service |
| `DB_*` | localhost / `springboot_db` | Postgres |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | broker |
| `FAUXNANCE_BASE_URL`, `FAUXNANCE_API_KEY` | `http://localhost:8080`, empty | quote provider, **server side only** |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM` | empty | the e-mail channel; without `SMTP_HOST` it records `STUB_DELIVERY` |
| `ALERTS_MAX_PER_ACCOUNT` | 25 | alert cap |
| `PORTFOLIO_QUOTE_TTL_SECONDS` | 60 | cache window for a fetched quote |
| `PORTFOLIO_STALE_AFTER_SECONDS` | 900 | an older observation is marked stale |
| `PORTFOLIO_PROVIDER_COOLDOWN_SECONDS` | 15 | pause after a failed quote call |
| `*_CONSUMER_ENABLED` | true | switch a consumer off (`MARKET_DATA_`, `NOTIFICATIONS_`, `PORTFOLIO_`) |

## Kafka consumer groups

Written down here because a group id names a logical consumer: another consumer adopting one of these would silently take half its messages.

| Group | Topic | Module |
|---|---|---|
| `watchlist-service` | `market-data` | alerts (and the quote cache) |
| `notification-service` | `trade-events` | notifications |
| `portfolio-service` | `trade-events` | portfolio |

Auto-commit is off; offsets are committed after the listener returns. Malformed messages and exhausted retries go to `<topic>.DLT`.

## Tests

```
JWT_SECRET=any-32-byte-secret-for-tests-0000000 mvn test
```

* **Unit tests** (default). Baseline before the extension work: 118 tests, 4 failing (`OrderValidatorTest`, currency mismatch, **pre-existing and unrelated**). Now 306, with the same 4 failing and nothing else.
* **Integration tests** need a Postgres with the platform schema, the seed, and `003_extensions.sql` applied. They are skipped without `TEST_DB_URL` and are not part of the default run:

```
TEST_DB_URL=jdbc:postgresql://localhost:5432/springboot_db TEST_DB_USER=postgres TEST_DB_PASSWORD=postgres \
JWT_SECRET=integration-test-secret-integration-test-secret \
mvn test -Dtest='ExtensionsChainIT,KafkaChainIT'
```

`ExtensionsChainIT` runs the real application over HTTP with real signed tokens against that database (and a local stand-in for Fauxnance, so the real client code runs). `KafkaChainIT` adds an embedded Kafka broker to prove group ids, replay, committed offsets and dead-lettering.
Both **truncate** `notifications`, `price_alerts`, `portfolio_realised_pnl` and `customer_preferences`: point them at a scratch database.

## Runbook: where to look when something is wrong

```sql
-- notifications that did not go out, and why
SELECT status, status_reason, resolved_channel, count(*) FROM notifications
 WHERE status <> 'SENT' GROUP BY 1, 2, 3;
-- the oldest message still waiting
SELECT id, account_id, status, status_reason, attempts, created_at FROM notifications
 WHERE status IN ('QUEUED', 'HELD') ORDER BY created_at LIMIT 10;
-- fired alerts whose hand-over is not confirmed
SELECT id, account_id, symbol, delivery_state, delivery_attempts FROM price_alerts
 WHERE status = 'TRIGGERED' AND delivery_state <> 'HANDED_OVER';
-- realised P&L booked per account
SELECT account_id, count(*), sum(realised_pnl) FROM portfolio_realised_pnl GROUP BY 1;
```

Dead letters: inspect `trade-events.DLT` (Kafbat UI, port 8086). Each record has the original message as its value and the failure and the originating group in its headers.
`GET /health` reports Postgres and Fauxnance (with the remaining quota).

## Known gaps

* The Trade REST API's own cancel route does not publish `ORDER_CANCELLED`, so customer-initiated cancels are not notified until something does.
* SMS and push have no provider: they record `STUB_DELIVERY`.
* Realised P&L covers sales still within `trade-events` retention (30 days) when the module first runs.
* Outstanding security items: see the review.
