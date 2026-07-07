-- V35 — Fully admin-controlled lorry cells (owner 2026-07-05).
-- The owner tunes delivery per product size without deploys ("it all depends, we can't make a formula"):
--   • lorry_<zone>_enabled — explicit ON/OFF per zone per size (small boards: no lorry to outer;
--     the customer simply uses the courier). Distinct from blank-price ("arranged") and from the
--     contact flags. Effective value = product AND variant (default true on both).
--   • lorry_outer_gate_cents — min-bill thresholds are now possible on ALL three zones.
--   • A gated cell MAY also carry a price (lorry_<zone>_cents): when the gate is met the cell charges
--     price × qty; with no price it falls back to the zone's flat gate-met charge (delivery_settings).
--   • gate_met_outer_cents — flat fallback for outer gate-met cells (0 until the owner prices it).
-- Money in integer LKR cents.

ALTER TABLE product
    ADD COLUMN lorry_colombo_enabled  BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_suburb_enabled   BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_outer_enabled    BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_outer_gate_cents BIGINT;

ALTER TABLE product_variant
    ADD COLUMN lorry_colombo_enabled  BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_suburb_enabled   BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_outer_enabled    BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN lorry_outer_gate_cents BIGINT;

ALTER TABLE delivery_settings
    ADD COLUMN gate_met_outer_cents BIGINT NOT NULL DEFAULT 0;
