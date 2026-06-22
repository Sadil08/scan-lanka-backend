-- V15 — Per-item fulfilment + COD actual delivery cost (08 FR-ADMIN-6c).
ALTER TABLE order_item
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PREPARING','PREPARED','SHIPPED','DELIVERED','CANCELLED'));

ALTER TABLE "order"
    ADD COLUMN actual_delivery_cents BIGINT CHECK (actual_delivery_cents IS NULL OR actual_delivery_cents >= 0),
    ADD COLUMN delivery_courier VARCHAR(120);
