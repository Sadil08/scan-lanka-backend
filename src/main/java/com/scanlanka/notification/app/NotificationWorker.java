package com.scanlanka.notification.app;

import com.scanlanka.notification.domain.Notification;
import com.scanlanka.notification.infra.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Drains the outbox (10 FR-NOTIFY-4/5): sends due messages, retries with backoff, dead-letters after
 * MAX attempts with an ops alert. A provider outage never blocks checkout — messages just queue + retry.
 */
@Component
public class NotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private static final int MAX_ATTEMPTS = 6;

    private final NotificationRepository repo;
    private final EmailProvider provider;

    public NotificationWorker(NotificationRepository repo, EmailProvider provider) {
        this.repo = repo;
        this.provider = provider;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:15000}")
    @Transactional
    public void drain() {
        List<Notification> due = repo.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            List.of("PENDING", "RETRYING"), Instant.now(), Limit.of(50));
        for (Notification n : due) {
            try {
                provider.send(n.getRecipient(), n.getSubject(), n.getBody());
                n.markSent();
            } catch (Exception e) {
                if (n.getAttempts() + 1 >= MAX_ATTEMPTS) {
                    n.fail(e.getMessage());
                    log.error("notification {} permanently FAILED (ops alert): {}", n.getId(), e.getMessage());
                } else {
                    n.retryLater(Instant.now().plusSeconds(backoffSeconds(n.getAttempts())));
                }
            }
            repo.save(n);
        }
    }

    private static long backoffSeconds(int attempt) {
        // First retry is immediate (transient blips clear fast); then exponential: 30s, 60s, 120s … capped 1h.
        if (attempt <= 0) return 0;
        return (long) Math.min(3600, Math.pow(2, attempt - 1) * 30);
    }
}
