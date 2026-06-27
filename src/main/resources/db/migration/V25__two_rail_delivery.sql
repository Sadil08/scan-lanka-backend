-- V25 — Two-rail delivery model (05/17, owner 2026-06-27). Replaces the DIM/zone-rate model.
-- Rail A: in-house lorry = fixed per-variant per-zone charge, only for orders over the global min bill.
-- Rail B: Citrek courier = round(weight × per_kg) + base per courier zone (display-only, full COD).
-- Money in LKR cents. weight_kg already exists (V2); legacy length/width/height_cm are now unused.

-- Per-product defaults + per-variant (per-size) lorry charges and whatsapp-only flag (01 FR-CATALOG-14).
ALTER TABLE product
    ADD COLUMN lorry_colombo_cents BIGINT,
    ADD COLUMN lorry_suburb_cents  BIGINT,
    ADD COLUMN lorry_outer_cents   BIGINT,
    ADD COLUMN whatsapp_only        BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE product_variant
    ADD COLUMN lorry_colombo_cents BIGINT,
    ADD COLUMN lorry_suburb_cents  BIGINT,
    ADD COLUMN lorry_outer_cents   BIGINT,
    ADD COLUMN whatsapp_only        BOOLEAN NOT NULL DEFAULT false;

-- Citrek courier rate card (admin-config, 08). estimate = round(weight × per_kg) + base.
CREATE TABLE courier_rate_card (
    zone         VARCHAR(16) PRIMARY KEY
                   CHECK (zone IN ('COLOMBO_1_15','OTHER','JAFFNA_NORTH')),
    base_cents   BIGINT NOT NULL CHECK (base_cents >= 0),
    per_kg_cents BIGINT NOT NULL CHECK (per_kg_cents >= 0)
);
INSERT INTO courier_rate_card (zone, base_cents, per_kg_cents) VALUES
    ('COLOMBO_1_15', 47000, 18500),   -- Rs 470 + Rs 185/kg
    ('OTHER',        61000, 21000),   -- Rs 610 + Rs 210/kg
    ('JAFFNA_NORTH', 75000, 23000);   -- Rs 750 + Rs 230/kg

-- Postal code → lorry zone + courier zone (one row per code). Seeded later from LK.txt.
CREATE TABLE postal_zone (
    postal_code  VARCHAR(10) PRIMARY KEY,
    lorry_zone   VARCHAR(8)  NOT NULL CHECK (lorry_zone IN ('COLOMBO','SUBURB','OUTER')),
    courier_zone VARCHAR(16) NOT NULL CHECK (courier_zone IN ('COLOMBO_1_15','OTHER','JAFFNA_NORTH')),
    district     VARCHAR(60),
    province     VARCHAR(60)
);

-- Per-rail global enable toggle (no pickup).
CREATE TABLE delivery_method_config (
    method  VARCHAR(16) PRIMARY KEY CHECK (method IN ('COMPANY_LORRY','COURIER')),
    enabled BOOLEAN NOT NULL DEFAULT true
);
INSERT INTO delivery_method_config (method, enabled) VALUES
    ('COMPANY_LORRY', true), ('COURIER', true);

-- Global delivery settings (singleton): in-house-lorry minimum order value (owner: "more than Rs 6,000").
CREATE TABLE delivery_settings (
    id                   SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    lorry_min_bill_cents BIGINT   NOT NULL DEFAULT 600000   -- Rs 6,000
);
INSERT INTO delivery_settings (id) VALUES (1);
