-- V14 — Generated receipt PDF storage key (09 FR-18). Private; served via authz endpoints only.
CREATE TABLE order_receipt (
    order_id     BIGINT       PRIMARY KEY REFERENCES "order"(id) ON DELETE CASCADE,
    storage_key  VARCHAR(512) NOT NULL,
    generated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
