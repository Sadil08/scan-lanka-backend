package com.scanlanka.order.infra;

import com.scanlanka.order.domain.OrderStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusEventRepository extends JpaRepository<OrderStatusEvent, Long> {
    List<OrderStatusEvent> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
