package com.scanlanka.notification.app;

import com.scanlanka.notification.domain.Notification;
import com.scanlanka.notification.infra.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Admin notification audit view + manual resend (10 FR-NOTIFY-7). */
@Service
public class AdminNotificationService {

    private final NotificationRepository repo;

    public AdminNotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public record NotificationView(long id, String type, String recipient, String subject, String status,
                                   int attempts, String lastError, Instant createdAt, Instant sentAt) {}

    @Transactional(readOnly = true)
    public Page<NotificationView> list(String status, Pageable pageable) {
        Page<Notification> page = (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
            ? repo.findAllByOrderByCreatedAtDesc(pageable)
            : repo.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        return page.map(this::toView);
    }

    @Transactional
    public void resend(long id) {
        Notification n = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if (!Notification.Status.FAILED.name().equals(n.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_FAILED");
        }
        n.resetForRetry();
        repo.save(n);
    }

    private NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getRecipient(), n.getSubject(), n.getStatus(),
            n.getAttempts(), n.getLastError(), n.getCreatedAt(), n.getSentAt());
    }
}
