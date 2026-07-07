-- V34 — Domex-only delivery refinement (owner 2026-07-03).
--   • Per-cell lorry minimum bills (Colombo/Suburb columns) replace the global min-bill gate.
--   • Flat per-order "gate-met" delivery charge per zone (Rs 500 Colombo / Rs 800 Suburb).
--   • Courier is available for every couriable product in every area; only large boards are restricted
--     TO OUTER areas: lorry_outer_whatsapp = lorry-to-outer needs manual contact (glass, key holders,
--     6×4, 8×4); courier_outer_blocked = courier-to-outer not allowed (6×3, 6×4, 8×4). In-city courier
--     of those boards uses the normal BETWEEN_2FT_6FT rate.
-- Money in integer LKR cents. The global lorry_min_bill_cents column is retired (kept, unused).

ALTER TABLE product
    ADD COLUMN lorry_colombo_gate_cents BIGINT,
    ADD COLUMN lorry_suburb_gate_cents  BIGINT,
    ADD COLUMN lorry_outer_whatsapp     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN courier_outer_blocked    BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE product_variant
    ADD COLUMN lorry_colombo_gate_cents BIGINT,
    ADD COLUMN lorry_suburb_gate_cents  BIGINT,
    ADD COLUMN lorry_outer_whatsapp     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN courier_outer_blocked    BOOLEAN NOT NULL DEFAULT false;

-- Flat per-order charge applied once when a gated small-item line qualifies (subtotal > its threshold).
ALTER TABLE delivery_settings
    ADD COLUMN gate_met_colombo_cents BIGINT NOT NULL DEFAULT 50000,   -- Rs 500
    ADD COLUMN gate_met_suburb_cents  BIGINT NOT NULL DEFAULT 80000;   -- Rs 800
