-- Distinguishes unpaid PayHere (CARD) attempts from bank-transfer orders so the admin
-- board can hide abandoned card checkouts while still showing slip-awaiting bank orders.
ALTER TABLE "order"
    ADD COLUMN payment_method VARCHAR(10)
        CHECK (payment_method IS NULL OR payment_method IN ('CARD', 'BANK'));

-- Existing prepaid rows still awaiting payment were almost certainly PayHere (bank slips
-- move to AWAITING_BANK_CONFIRMATION). Tag them CARD so the sweeper / unpaid view apply.
UPDATE "order"
   SET payment_method = 'CARD'
 WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED')
   AND delivery_payment = 'PREPAID'
   AND payment_method IS NULL;

CREATE INDEX ix_order_pending_card
    ON "order"(status, payment_method, created_at)
    WHERE status = 'PENDING_PAYMENT' AND payment_method = 'CARD';
