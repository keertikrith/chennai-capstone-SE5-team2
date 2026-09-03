# TRANSFORM step for the Postgres ETL pipeline.
#
# Validates raw rows from Postgres and shapes them for DuckDB load.
# Bad rows are quarantined (not raised) so the rest of the batch continues.
#
# Mirrors the structure of analytics_etl/transform.py.

from __future__ import annotations

import logging
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Any

logger = logging.getLogger(__name__)

VALID_SIDES    = {"BUY", "SELL"}
VALID_STATUSES = {"NEW", "FILLED", "CANCELLED", "REJECTED"}


# ---------------------------------------------------------------------------
# Field validators
# ---------------------------------------------------------------------------

def _require_str(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"'{field}' must be a non-empty string, got {value!r}")
    return value.strip()


def _require_int(value: Any, field: str) -> int:
    try:
        v = int(value)
    except (TypeError, ValueError):
        raise ValueError(f"'{field}' must be an integer, got {value!r}")
    return v


def _require_positive_decimal(value: Any, field: str) -> Decimal:
    if value is None:
        raise ValueError(f"'{field}' cannot be null")
    try:
        d = Decimal(str(value).strip())
    except (InvalidOperation, TypeError):
        raise ValueError(f"'{field}' is not a valid decimal: {value!r}")
    if not d.is_finite():
        raise ValueError(f"'{field}' must be finite, got {value!r}")
    if d <= 0:
        raise ValueError(f"'{field}' must be > 0, got {d}")
    return d


def _optional_decimal(value: Any, field: str) -> Decimal | None:
    if value is None:
        return None
    return _require_positive_decimal(value, field)


def _require_datetime(value: Any, field: str) -> datetime:
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value
    raise ValueError(f"'{field}' must be a datetime, got {type(value).__name__!r}")


def _date_key(dt: datetime) -> int:
    """Convert a datetime to an integer YYYYMMDD date key."""
    return int(dt.strftime("%Y%m%d"))


# ---------------------------------------------------------------------------
# Single-row transform
# ---------------------------------------------------------------------------

def transform_row(row: dict[str, Any]) -> dict[str, Any]:
    """
    Validate and reshape one raw Postgres row into a clean dict ready for load.

    Raises ValueError on any validation failure — the caller quarantines the row.
    """
    # --- Core order fields ---
    source_order_id = _require_str(row.get("source_order_id"), "source_order_id")
    side = _require_str(row.get("side"), "side").upper()
    if side not in VALID_SIDES:
        raise ValueError(f"'side' must be BUY or SELL, got {side!r}")

    status = _require_str(row.get("status"), "status").upper()
    if status not in VALID_STATUSES:
        raise ValueError(f"'status' must be one of {VALID_STATUSES}, got {status!r}")

    quantity = _require_int(row.get("quantity"), "quantity")
    if quantity <= 0:
        raise ValueError(f"'quantity' must be > 0, got {quantity}")

    price = _require_positive_decimal(row.get("price"), "price")
    executed_price = _optional_decimal(row.get("executed_price"), "executed_price")

    # trade_value: use executed_price when filled, otherwise requested price
    effective_price = executed_price if executed_price is not None else price
    trade_value = Decimal(quantity) * effective_price

    created_at = _require_datetime(row.get("created_at"), "created_at")
    date_key_val = _date_key(created_at)

    # --- Instrument fields ---
    symbol      = _require_str(row.get("symbol"), "symbol")
    instrument_id = _require_int(row.get("instrument_id"), "instrument_id")

    # --- Account fields ---
    account_id  = _require_str(str(row.get("account_id")), "account_id")
    account_ref = _require_str(row.get("account_ref"), "account_ref")
    holder_name = _require_str(row.get("holder_name"), "holder_name")
    account_status = _require_str(row.get("account_status"), "account_status")

    return {
        # fact_trades columns
        "source_order_id": source_order_id,
        "side":            side,
        "status":          status,
        "quantity":        quantity,
        "price":           price,
        "executed_price":  executed_price,
        "trade_value":     trade_value,
        "created_at":      created_at,
        "date_key":        date_key_val,

        # instrument (for dim_instrument lookup/upsert)
        "instrument_id":   instrument_id,
        "symbol":          symbol,
        "instrument_name": row.get("instrument_name"),
        "asset_class":     row.get("asset_class"),
        "currency":        row.get("currency"),
        "exchange":        row.get("exchange"),
        "tradable":        row.get("tradable"),

        # account (for dim_account lookup/upsert)
        "account_id":      account_id,
        "account_ref":     account_ref,
        "holder_name":     holder_name,
        "account_status":  account_status,
        "account_created_at": row.get("account_created_at"),
    }


# ---------------------------------------------------------------------------
# Batch transform
# ---------------------------------------------------------------------------

def transform(raw_rows: list[dict[str, Any]]) -> dict[str, Any]:
    """
    Transform a list of raw Postgres rows.

    Returns:
        {
            "clean":      [list of validated, shaped dicts],
            "quarantine": [list of {"row": ..., "reason": ...} dicts],
        }
    """
    clean     : list[dict] = []
    quarantine: list[dict] = []

    for i, row in enumerate(raw_rows, start=1):
        try:
            clean.append(transform_row(row))
        except ValueError as exc:
            logger.warning(
                "QUARANTINE row=%d source_order_id=%r reason=%s",
                i, row.get("source_order_id"), exc,
            )
            quarantine.append({"row": row, "reason": str(exc)})

    logger.info(
        "TRANSFORM complete: %d clean, %d quarantined",
        len(clean), len(quarantine),
    )
    return {"clean": clean, "quarantine": quarantine}
