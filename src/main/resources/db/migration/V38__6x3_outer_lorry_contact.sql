-- V38 — 6x3 boards need outer-lorry contact too (owner 2026-07-07).
-- 6x4 and 8x4 were already seeded with lorry_outer_whatsapp = true (outer lorry delivery for these
-- large boards is arranged manually via WhatsApp, not auto-priced). 6x3 was missed in the original
-- seed (V36) — courier_outer_blocked was correctly set for all three sizes, but lorry_outer_whatsapp
-- was not. This extends the same treatment already applied to 6x4/8x4 to 6x3.

UPDATE product_variant pv
SET lorry_outer_whatsapp = true
FROM spec_option so
WHERE so.id::text = pv.options_signature
  AND so.value = '6 x 3'
  AND pv.lorry_outer_whatsapp = false;
