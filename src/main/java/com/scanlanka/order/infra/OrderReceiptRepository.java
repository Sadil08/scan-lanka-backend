package com.scanlanka.order.infra;

import com.scanlanka.order.domain.OrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReceiptRepository extends JpaRepository<OrderReceipt, Long> {}
