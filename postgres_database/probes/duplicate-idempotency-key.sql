BEGIN;

-- First order: adding an order with idempotency key = 'f0f0f0f0-f0f0-f0f0-f0f0-f0f0f0f0f0f0'
INSERT INTO orders
(
    account_id,
    instrument_id,
    idempotency_key,
    order_type,
    quantity,
    limit_price,
    status
)
VALUES
(
    1,                  -- ACC0000001
    1,                  -- AAPL
    'f0f0f0f0-f0f0-f0f0-f0f0-f0f0f0f0f0f0'::UUID,
    'BUY',
    1,
    100.00000000,
    'NEW'
);

COMMIT;


-- Second order - adding the same order again - WILL FAIL with error code 23505

INSERT INTO orders
(
    account_id,
    instrument_id,
    idempotency_key,
    order_type,
    quantity,
    limit_price,
    status
)
VALUES
(
    1,                  -- ACC0000001
    1,                  -- AAPL
    'f0f0f0f0-f0f0-f0f0-f0f0-f0f0f0f0f0f0'::UUID,
    'BUY',
    1,
    100.00000000,
    'NEW'
);

ROLLBACK;
