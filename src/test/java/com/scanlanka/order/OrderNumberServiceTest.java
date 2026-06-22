package com.scanlanka.order;

import com.scanlanka.order.app.OrderNumberService;
import com.scanlanka.order.app.OrderProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberServiceTest {

    private final OrderNumberService service =
        new OrderNumberService(new OrderProperties("test-order-secret-please-rotate"));

    @Test
    void generatedNumberVerifies() {
        String number = service.generate();
        assertThat(number).startsWith("SL-");
        assertThat(service.verify(number)).isTrue();
    }

    @Test
    void tamperedNumberFailsVerification() {
        String number = service.generate();
        // flip the last signature char
        char last = number.charAt(number.length() - 1);
        String tampered = number.substring(0, number.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThat(service.verify(tampered)).isFalse();
    }

    @Test
    void malformedNumberFails() {
        assertThat(service.verify("not-an-order")).isFalse();
        assertThat(service.verify(null)).isFalse();
        assertThat(service.verify("SL-20260622-ABC-XYZ")).isFalse();
    }

    @Test
    void differentSecretCannotVerify() {
        String number = service.generate();
        OrderNumberService other = new OrderNumberService(new OrderProperties("a-different-secret"));
        assertThat(other.verify(number)).isFalse();
    }
}
