-- V37 — Lorry delivery cap for large orders (owner 2026-07-07).
-- Once the order's product subtotal is strictly greater than a trigger amount (default Rs 6,000), the
-- in-house lorry's TOTAL delivery charge for Colombo/Suburb is capped at a ceiling — replacing the flat
-- per-order "gate-met" charge introduced 2026-07-05 (Rs 500/800), now Rs 1,000/1,500 and reused as a
-- ceiling on the whole zone total (not just for gated cells) once the trigger is exceeded. Outer is
-- untouched — no cap, no change to its existing gate-met flat charge.

ALTER TABLE delivery_settings RENAME COLUMN gate_met_colombo_cents TO lorry_cap_colombo_cents;
ALTER TABLE delivery_settings RENAME COLUMN gate_met_suburb_cents TO lorry_cap_suburb_cents;
ALTER TABLE delivery_settings ADD COLUMN lorry_cap_trigger_cents BIGINT NOT NULL DEFAULT 600000; -- Rs 6,000

UPDATE delivery_settings SET
    lorry_cap_colombo_cents = 100000,  -- Rs 1,000 (was Rs 500)
    lorry_cap_suburb_cents  = 150000;  -- Rs 1,500 (was Rs 800)
