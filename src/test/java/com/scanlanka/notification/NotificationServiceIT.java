package com.scanlanka.notification;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.notification.infra.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Outbox enqueue is idempotent on the key (10 FR-NOTIFY-4) — a replayed trigger enqueues once. */
class NotificationServiceIT extends AbstractIntegrationTest {

    @Autowired NotificationService service;
    @Autowired NotificationRepository repo;

    @Test
    void enqueueIsIdempotentOnKey() {
        service.enqueue("ORDER_RECEIPT", "a@scanlanka.lk", "Receipt", "body-1", "receipt:42");
        service.enqueue("ORDER_RECEIPT", "a@scanlanka.lk", "Receipt again", "body-2", "receipt:42");

        assertThat(repo.existsByIdempotencyKey("receipt:42")).isTrue();
        assertThat(repo.count()).isEqualTo(1); // second enqueue with same key was a no-op
    }

    @Test
    void blankRecipientIsSkipped() {
        service.enqueue("ORDER_RECEIPT", "", "Receipt", "body", "receipt:blank");
        assertThat(repo.existsByIdempotencyKey("receipt:blank")).isFalse();
    }
}
