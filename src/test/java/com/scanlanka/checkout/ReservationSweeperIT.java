package com.scanlanka.checkout;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.checkout.app.ReservationSweeper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression: the scheduled sweeper runs a {@code @Modifying} release query, which needs an active
 * transaction. Calling it without one throws {@code TransactionRequiredException} at executeUpdate()
 * (before any row is touched), so the scheduled task silently failed every tick and expired holds
 * were never released. sweep() must delegate to the @Transactional service and complete cleanly.
 */
class ReservationSweeperIT extends AbstractIntegrationTest {

    @Autowired
    ReservationSweeper sweeper;

    @Test
    void sweepRunsInsideTransactionAndDoesNotThrow() {
        assertThatCode(sweeper::sweep).doesNotThrowAnyException();
    }
}
