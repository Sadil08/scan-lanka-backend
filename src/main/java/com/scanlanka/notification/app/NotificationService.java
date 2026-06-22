package com.scanlanka.notification.app;

import com.scanlanka.notification.domain.Notification;
import com.scanlanka.notification.infra.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox enqueue (10 FR-NOTIFY-4/5). Idempotent on the key (a replayed trigger enqueues once).
 * Called inside the trigger's transaction; the worker delivers asynchronously.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void enqueue(String type, String recipient, String subject, String body, String idempotencyKey) {
        if (recipient == null || recipient.isBlank()) return;
        if (repo.existsByIdempotencyKey(idempotencyKey)) return; // already enqueued (idempotent)
        repo.save(new Notification(type, recipient, subject, body, idempotencyKey));
    }
}
