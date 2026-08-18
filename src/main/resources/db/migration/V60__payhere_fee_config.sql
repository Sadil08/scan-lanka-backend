-- Admin-tunable PayHere online-card surcharge, mirroring tax_config (singleton row, off by default).
-- Charged on the full door total (subtotal + delivery + tax) only for prepaid CARD (PayHere) checkouts.
CREATE TABLE payhere_fee_config (
    id        INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    rate_bps  INTEGER NOT NULL DEFAULT 0 CHECK (rate_bps >= 0),
    label     VARCHAR(60) NOT NULL DEFAULT 'PayHere fee'
);
INSERT INTO payhere_fee_config (id, rate_bps, label) VALUES (1, 0, 'PayHere fee');

-- Persist the fee actually charged per order (0 for bank/COD orders) for receipts/audits.
ALTER TABLE "order" ADD COLUMN payhere_fee_cents BIGINT NOT NULL DEFAULT 0;
