package com.scanlanka.payment.app;

import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cancels abandoned PayHere (CARD) attempts that never completed payment, so they stop cluttering
 * the database as perpetual PENDING_PAYMENT rows. Stock holds are released; a delayed PayHere
 * success notify can still revive the order (see {@link OrderFulfilmentConfirmer}).
 */
@Component
public class AbandonedCardOrderSweeper {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCardOrderSweeper.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final StockReservationService reservations;
    private final Duration ttl;

    public AbandonedCardOrderSweeper(OrderRepository orders, OrderService orderService,
                                     StockReservationService reservations,
                                     @Value("${app.payhere.abandon-ttl-minutes:60}") long ttlMinutes) {
        this.orders = orders;
        this.orderService = orderService;
        this.reservations = reservations;
        this.ttl = Duration.ofMinutes(Math.max(15, ttlMinutes));
    }

    @Scheduled(fixedDelayString = "${app.payhere.abandon-sweep-ms:120000}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now().minus(ttl);
        List<Order> abandoned = orders.findAbandonedCardOrders(cutoff);
        for (Order order : abandoned) {
            try {
                orderService.transition(order.getId(), OrderStatus.CANCELLED, ActorType.SYSTEM, null,
                    "Abandoned PayHere card attempt after " + ttl.toMinutes() + "m");
                reservations.releaseForOrder(order.getId());
            } catch (Exception e) {
                log.warn("Failed to cancel abandoned card order {}", order.getOrderNumber(), e);
            }
        }
        if (!abandoned.isEmpty()) {
            log.info("Cancelled {} abandoned PayHere card order(s)", abandoned.size());
        }
    }
}
