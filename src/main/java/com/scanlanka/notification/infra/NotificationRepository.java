package com.scanlanka.notification.infra;

import com.scanlanka.notification.domain.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        Collection<String> statuses, Instant now, Limit limit);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Notification> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
