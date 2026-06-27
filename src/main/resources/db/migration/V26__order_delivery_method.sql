-- V26 — Order snapshot of the chosen delivery rail (17/09). delivery_method records lorry vs courier;
-- courier_estimate_cents is the display-only approximate courier fee (full COD, never charged online).
-- delivery_arranged flags a lorry order where ≥1 far line had no listed price → admin arranges manually.
ALTER TABLE "order"
    ADD COLUMN delivery_method        VARCHAR(16),
    ADD COLUMN courier_estimate_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN delivery_arranged      BOOLEAN NOT NULL DEFAULT false;
