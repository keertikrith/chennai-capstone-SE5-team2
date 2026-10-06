-- Sprint 10 extensions: customer preferences, notifications, price alerts, portfolio P&L.
--
-- Idempotent on purpose (CREATE ... IF NOT EXISTS, no DROP): 001_initial_schema.sql drops and
-- recreates the trading tables, so it is only ever run on an empty database, but this file can be
-- applied to a database that already holds data:
--
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f databases/postgreSQL/migrations/003_extensions.sql
--
-- Each module owns its tables and nothing else writes to them. The only foreign keys point at the
-- shared trading tables (accounts, clients), never from one module to another: a module that
-- needs another module's data calls its published Java interface.

BEGIN;

-- ---------------------------------------------------------------------
-- CUSTOMER PREFERENCES  (module: preferences)
--
-- One row per CLIENT (the customer), not per account: a customer with two accounts has one set of
-- preferences. No e-mail address or phone number is stored here. The alert channel is a choice;
-- the destination is read from clients.email / clients.phone at the moment it is needed, so there is
-- exactly one copy of each contact detail on the platform.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_preferences (
    client_id           BIGINT PRIMARY KEY
                            REFERENCES clients(id) ON DELETE RESTRICT,
    default_account_id  BIGINT NOT NULL
                            REFERENCES accounts(id) ON DELETE RESTRICT,
    alert_channel       TEXT NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_customer_preferences_channel
        CHECK (alert_channel IN ('EMAIL', 'SMS', 'PUSH'))
);

-- ---------------------------------------------------------------------
-- NOTIFICATIONS  (module: notifications)
--
-- event_id is the idempotency key: the eventId of the trade event, or a name-based UUID derived from
-- the alert for a price alert. A second delivery of the same event hits the unique constraint and
-- is a no-op. The destination (e-mail / phone) is deliberately NOT stored: it is resolved on every
-- send and never written down. resolved_channel records what the send actually used.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id                BIGSERIAL PRIMARY KEY,
    event_id          UUID NOT NULL UNIQUE,
    account_id        BIGINT NOT NULL
                          REFERENCES accounts(id) ON DELETE RESTRICT,
    kind              TEXT NOT NULL,
    subject           TEXT NOT NULL,
    body              TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'QUEUED',
    status_reason     TEXT,
    resolved_channel  TEXT,
    channel_source    TEXT,
    attempts          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,

    CONSTRAINT ck_notifications_kind
        CHECK (kind IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT')),
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('QUEUED', 'HELD', 'SENT', 'FAILED')),
    CONSTRAINT ck_notifications_channel
        CHECK (resolved_channel IS NULL OR resolved_channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_notifications_channel_source
        CHECK (channel_source IS NULL OR channel_source IN ('STORED', 'DEFAULT'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_account_created
    ON notifications (account_id, created_at DESC, id DESC);

-- The dispatcher only ever looks at what is still waiting.
CREATE INDEX IF NOT EXISTS idx_notifications_due
    ON notifications (next_attempt_at)
    WHERE status IN ('QUEUED', 'HELD');

-- ---------------------------------------------------------------------
-- PRICE ALERTS  (module: alerts)
--
-- status        ACTIVE -> TRIGGERED (once, guarded by WHERE status = 'ACTIVE') or CANCELLED.
-- delivery_state says what happened when the fired alert was handed to notifications, so a customer
-- looking at a TRIGGERED alert can tell whether the message was handed over.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS price_alerts (
    id                  BIGSERIAL PRIMARY KEY,
    account_id          BIGINT NOT NULL
                            REFERENCES accounts(id) ON DELETE RESTRICT,
    symbol              TEXT NOT NULL,
    direction           TEXT NOT NULL,
    threshold           NUMERIC(18,4) NOT NULL,
    status              TEXT NOT NULL DEFAULT 'ACTIVE',
    delivery_state      TEXT NOT NULL DEFAULT 'NONE',
    delivery_attempts   INTEGER NOT NULL DEFAULT 0,
    triggered_price     NUMERIC(18,4),
    triggered_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE', 'BELOW')),
    CONSTRAINT ck_price_alerts_threshold
        CHECK (threshold > 0),
    CONSTRAINT ck_price_alerts_status
        CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED')),
    CONSTRAINT ck_price_alerts_delivery_state
        CHECK (delivery_state IN ('NONE', 'PENDING_HANDOVER', 'HANDED_OVER', 'HANDOVER_FAILED')),
    CONSTRAINT ck_price_alerts_trigger_state
        CHECK ((status = 'TRIGGERED') = (triggered_at IS NOT NULL))
);

-- The quote consumer asks "which active alerts exist for this symbol" on every message.
CREATE INDEX IF NOT EXISTS idx_price_alerts_active_symbol
    ON price_alerts (symbol)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_price_alerts_account
    ON price_alerts (account_id, created_at DESC, id DESC);

-- Fired alerts whose handover to notifications has not been confirmed.
CREATE INDEX IF NOT EXISTS idx_price_alerts_pending_handover
    ON price_alerts (triggered_at)
    WHERE status = 'TRIGGERED' AND delivery_state IN ('PENDING_HANDOVER', 'HANDOVER_FAILED');

-- ---------------------------------------------------------------------
-- REALISED PROFIT AND LOSS  (module: portfolio)
--
-- One row per sell fill, booked from the trade-events message at the moment of the sale and never
-- recomputed. event_id is the primary key, so replaying the same event cannot book it twice.
-- Amounts are in the account's base currency (the currency of the cash balance).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS portfolio_realised_pnl (
    event_id             UUID PRIMARY KEY,
    account_id           BIGINT NOT NULL
                             REFERENCES accounts(id) ON DELETE RESTRICT,
    symbol               TEXT NOT NULL,
    quantity             INTEGER NOT NULL,
    executed_price       NUMERIC(18,4) NOT NULL,
    average_cost         NUMERIC(18,4) NOT NULL,
    base_currency        CHAR(3) NOT NULL,
    realised_pnl         NUMERIC(18,4) NOT NULL,
    booked_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_portfolio_realised_quantity CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_realised_account_booked
    ON portfolio_realised_pnl (account_id, booked_at);

COMMIT;
