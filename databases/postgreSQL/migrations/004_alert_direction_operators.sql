ALTER TABLE price_alerts
    DROP CONSTRAINT IF EXISTS ck_price_alerts_direction;

ALTER TABLE price_alerts
    ADD CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE', 'BELOW', 'GT', 'GTE', 'LT', 'LTE', 'EQ'));
