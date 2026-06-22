-- Trigram index for storefront name search (02-storefront-browse §2).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS ix_product_name_trgm ON product USING gin (name gin_trgm_ops);
