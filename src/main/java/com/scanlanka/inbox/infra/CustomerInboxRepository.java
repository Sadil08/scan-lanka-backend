package com.scanlanka.inbox.infra;

import com.scanlanka.inbox.domain.CustomerInboxItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface CustomerInboxRepository extends JpaRepository<CustomerInboxItem, Long> {

    Page<CustomerInboxItem> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    long countByCustomerIdAndReadAtIsNull(Long customerId);

    boolean existsByCustomerIdAndSourceKey(Long customerId, String sourceKey);

    @Modifying
    @Query("UPDATE CustomerInboxItem i SET i.readAt = :now WHERE i.customerId = :customerId AND i.readAt IS NULL")
    int markAllRead(Long customerId, Instant now);
}
