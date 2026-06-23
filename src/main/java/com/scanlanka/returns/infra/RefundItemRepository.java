package com.scanlanka.returns.infra;

import com.scanlanka.returns.domain.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.List;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {
    List<RefundItem> findByRefundId(Long refundId);
}
