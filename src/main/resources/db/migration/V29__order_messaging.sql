-- Order-linked message threads (19-order-messaging)

CREATE TABLE message_thread (
    id                      BIGSERIAL PRIMARY KEY,
    order_id                BIGINT NOT NULL UNIQUE REFERENCES "order"(id),
    status                  TEXT NOT NULL DEFAULT 'OPEN',
    customer_unread_count   INT NOT NULL DEFAULT 0,
    admin_unread_count      INT NOT NULL DEFAULT 0,
    last_message_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT message_thread_status_chk CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE order_message (
    id              BIGSERIAL PRIMARY KEY,
    thread_id       BIGINT NOT NULL REFERENCES message_thread(id) ON DELETE CASCADE,
    author_role     TEXT NOT NULL,
    author_user_id  BIGINT REFERENCES app_user(id),
    author_label    TEXT,
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT order_message_role_chk CHECK (author_role IN ('CUSTOMER', 'ADMIN'))
);

CREATE INDEX idx_message_thread_inbox ON message_thread (status, last_message_at DESC NULLS LAST);
CREATE INDEX idx_order_message_thread ON order_message (thread_id, created_at);
