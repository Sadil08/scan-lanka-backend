-- V39 — Lorry delivery cap always applies, no trigger (owner 2026-07-07, same-day follow-up to V37).
-- The owner's first cut had the cap kick in only once order subtotal > Rs 6,000; after seeing a
-- Rs 1,687 order charge Rs 1,600 Colombo delivery (uncapped, per-cell sum), the owner confirmed the
-- cap should be UNCONDITIONAL: Colombo lorry delivery never exceeds Rs 1,000 and Suburb never exceeds
-- Rs 1,500, on any order size. The trigger column is dropped rather than deprecated — it shipped
-- and was superseded within the same session, no external consumers.

ALTER TABLE delivery_settings DROP COLUMN lorry_cap_trigger_cents;
