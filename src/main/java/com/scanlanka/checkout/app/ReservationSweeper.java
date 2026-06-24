package com.scanlanka.checkout.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases expired stock reservations (05 soft-reserve sweeper). */
@Component
public class ReservationSweeper {

    private final StockReservationService reservations;

    public ReservationSweeper(StockReservationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${scanlanka.reservation-sweep-ms:60000}")
    public void sweep() {
        // delegate to the @Transactional service: the @Modifying release query needs a tx
        reservations.releaseExpired();
    }
}
