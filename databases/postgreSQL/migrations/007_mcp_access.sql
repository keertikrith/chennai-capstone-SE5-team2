-- AI assistant access (MCP): the connector links a customer creates in Settings, the orders prepared through
-- them, and an audit trail. Only a SHA-256 hash of a connector's secret is stored. Safe to run twice.

BEGIN;

CREATE TABLE IF NOT EXISTS mcp_connectors (
    id                UUID PRIMARY KEY,
    account_id        BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    label             TEXT NOT NULL,
    token_hash        CHAR(64) NOT NULL UNIQUE,
    scopes            TEXT NOT NULL,
    max_order_value   NUMERIC(18,2) NOT NULL CHECK (max_order_value > 0),
    max_orders_per_day INTEGER NOT NULL CHECK (max_orders_per_day >= 1),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMPTZ NOT NULL,
    last_used_at      TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mcp_connectors_account ON mcp_connectors (account_id);

-- An order the assistant has prepared. It becomes a real order only when the customer confirms in the chat,
-- and only once: the row moves PENDING -> SUBMITTING in a single guarded UPDATE.
CREATE TABLE IF NOT EXISTS mcp_pending_orders (
    id            UUID PRIMARY KEY,
    connector_id  UUID NOT NULL REFERENCES mcp_connectors(id) ON DELETE CASCADE,
    account_id    BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol        TEXT NOT NULL,
    side          TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity      INTEGER NOT NULL CHECK (quantity >= 1),
    price         NUMERIC(18,2) NOT NULL CHECK (price > 0),
    est_value     NUMERIC(18,2) NOT NULL,
    status        TEXT NOT NULL CHECK (status IN ('PENDING', 'SUBMITTING', 'PLACED', 'REJECTED', 'FAILED')),
    order_id      UUID,
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMPTZ NOT NULL,
    decided_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mcp_pending_connector_day ON mcp_pending_orders (connector_id, created_at);

CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    connector_id  UUID REFERENCES mcp_connectors(id) ON DELETE SET NULL,
    account_id    BIGINT,
    tool          TEXT NOT NULL,
    outcome       TEXT NOT NULL,
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcp_audit_account ON mcp_audit_log (account_id, created_at DESC);

COMMIT;
