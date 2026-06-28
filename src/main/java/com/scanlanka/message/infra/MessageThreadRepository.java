package com.scanlanka.message.infra;

import com.scanlanka.message.domain.MessageThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

    Optional<MessageThread> findByOrderId(long orderId);

    List<MessageThread> findByStatusOrderByLastMessageAtDesc(String status);

    @Query("""
        SELECT t FROM MessageThread t, Order o
        WHERE t.orderId = o.id
          AND (:status IS NULL OR t.status = :status)
          AND (:unreadOnly = false OR t.adminUnreadCount > 0)
          AND (:q IS NULL OR :q = '' OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY t.lastMessageAt DESC NULLS LAST, t.updatedAt DESC
        """)
    Page<MessageThread> adminSearch(@Param("status") String status,
                                    @Param("unreadOnly") boolean unreadOnly,
                                    @Param("q") String q,
                                    Pageable pageable);
}
