package com.scanlanka.order;

import com.scanlanka.order.domain.OrderStateMachine;
import org.junit.jupiter.api.Test;

import static com.scanlanka.order.domain.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStateMachineTest {

    @Test
    void legalTransitionsAllowed() {
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, PAID)).isTrue();
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, AWAITING_BANK_CONFIRMATION)).isTrue();
        assertThat(OrderStateMachine.canTransition(PAID, PACKED)).isTrue();
        assertThat(OrderStateMachine.canTransition(PACKED, SHIPPED)).isTrue();
        assertThat(OrderStateMachine.canTransition(SHIPPED, COMPLETED)).isTrue();
        assertThat(OrderStateMachine.canTransition(AWAITING_BANK_CONFIRMATION, PAID)).isTrue();
    }

    @Test
    void illegalTransitionsRejected() {
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, COMPLETED)).isFalse();
        assertThat(OrderStateMachine.canTransition(PENDING_PAYMENT, SHIPPED)).isFalse();
        assertThat(OrderStateMachine.canTransition(PAID, COMPLETED)).isFalse();
        assertThat(OrderStateMachine.canTransition(COMPLETED, PACKED)).isFalse();
    }

    @Test
    void terminalStatesHaveNoTransitions() {
        assertThat(OrderStateMachine.isTerminal(COMPLETED)).isTrue();
        assertThat(OrderStateMachine.isTerminal(CANCELLED)).isTrue();
        assertThat(OrderStateMachine.isTerminal(PENDING_PAYMENT)).isFalse();
    }
}
