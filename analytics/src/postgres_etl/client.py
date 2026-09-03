# Postgres HTTP/DB client — connects to Postgres and extracts trade data.
#
# This is the Postgres equivalent of analytics_etl/client.py (FauxnanceClient).
# It wraps psycopg2 and exposes one extraction method: get_orders_since().

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

import psycopg2
import psycopg2.extras

from .config import get_postgres_dsn
from .errors import PostgresConnectionError, PostgresExtractError

logger = logging.getLogger(__name__)

# SQL: extract orders + transactions + instruments + accounts in one query.
# We pull ALL order statuses (NEW, FILLED, CANCELLED, REJECTED) so that
# fill-rate analytics can be computed against the full population.
_EXTRACT_SQL = """
SELECT
    o.id                        AS order_id,
    o.idempotency_key           AS source_order_id,
    o.order_type                AS side,
    o.quantity                  AS quantity,
    o.price                     AS price,
    o.status                    AS status,
    o.created_at                AS created_at,
    o.terminal_at               AS terminal_at,

    -- Transaction (filled price) — NULL for unfilled orders
    t.price                     AS executed_price,
    t.executed_at               AS executed_at,

    -- Instrument
    i.id                        AS instrument_id,
    i.symbol                    AS symbol,
    i.name                      AS instrument_name,
    i.asset_class               AS asset_class,
    i.currency                  AS currency,
    i.exchange                  AS exchange,
    i.is_tradable               AS tradable,

    -- Account
    a.id                        AS account_id,
    a.account_ref               AS account_ref,
    a.status                    AS account_status,
    a.created_at                AS account_created_at,

    -- Client (holder name)
    c.name                      AS holder_name

FROM orders o
JOIN instruments i  ON i.id = o.instrument_id
JOIN accounts    a  ON a.id = o.account_id
JOIN clients     c  ON c.id = a.client_id
LEFT JOIN transactions t ON t.order_id = o.id

WHERE o.created_at >= %(watermark)s

ORDER BY o.created_at ASC
"""


class PostgresClient:
    """
    Thin wrapper around psycopg2 for extracting trade data.

    Usage:
        client = PostgresClient()
        rows = client.get_orders_since(datetime(2026, 1, 1, tzinfo=timezone.utc))
        client.close()

    Or as a context manager:
        with PostgresClient() as client:
            rows = client.get_orders_since(watermark)
    """

    def __init__(self, dsn: str | None = None):
        dsn = dsn or get_postgres_dsn()
        try:
            self._conn = psycopg2.connect(
                dsn,
                cursor_factory=psycopg2.extras.RealDictCursor,
            )
            self._conn.set_session(readonly=True, autocommit=True)
            logger.info("PostgresClient connected")
        except psycopg2.OperationalError as exc:
            raise PostgresConnectionError(str(exc)) from exc

    def health(self) -> bool:
        """Return True if the Postgres connection is alive."""
        try:
            with self._conn.cursor() as cur:
                cur.execute("SELECT 1")
            return True
        except Exception:
            return False

    def get_orders_since(self, watermark: datetime) -> list[dict[str, Any]]:
        """
        Return all orders created at or after `watermark`.

        Each row is a dict with keys matching the _EXTRACT_SQL column aliases.
        Rows are ordered by created_at ASC so the pipeline processes them
        in chronological order.
        """
        if watermark.tzinfo is None:
            watermark = watermark.replace(tzinfo=timezone.utc)

        try:
            with self._conn.cursor() as cur:
                cur.execute(_EXTRACT_SQL, {"watermark": watermark})
                rows = cur.fetchall()
                logger.info(
                    "PostgresClient fetched %d orders since %s",
                    len(rows),
                    watermark.isoformat(),
                )
                return [dict(row) for row in rows]
        except psycopg2.Error as exc:
            raise PostgresExtractError(str(exc)) from exc

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass

    # Context manager support
    def __enter__(self) -> PostgresClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()
