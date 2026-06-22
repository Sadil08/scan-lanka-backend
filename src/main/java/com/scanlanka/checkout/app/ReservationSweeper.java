package com.scanlanka.checkout.app;

import com.scanlanka.checkout.infra.StockReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases expired stock reservations (05 soft-reserve sweeper). */
@Component
public class ReservationSweeper {

    private final StockReservationRepository reservations;

    public ReservationSweeper(StockReservationRepository reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${scanlanka.reservation-sweep-ms:60000}")
    public void sweep() {
        reservations.releaseExpired(java.time.Instant.now());
    }
}
