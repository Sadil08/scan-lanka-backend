-- Explicit phone country code for quote requests (owner 2026-07-14, 11 FR-QUOTE-7).
-- Existing phone values were free-text without a code; left null and backfilled by the
-- admin correcting entries as needed, new submissions always populate it.
ALTER TABLE quote_request ADD COLUMN country_code VARCHAR(6);
