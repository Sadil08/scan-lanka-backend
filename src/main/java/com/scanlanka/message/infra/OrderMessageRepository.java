package com.scanlanka.message.infra;

import com.scanlanka.message.domain.OrderMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {

    List<OrderMessage> findByThreadIdOrderByCreatedAtAsc(long threadId);
}
