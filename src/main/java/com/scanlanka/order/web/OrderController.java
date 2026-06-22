package com.scanlanka.order.web;

import com.scanlanka.auth.app.AccountFeatureGuard;
import com.scanlanka.order.app.OrderQueryService;
import com.scanlanka.order.web.dto.OrderResponses.OrderDetailView;
import com.scanlanka.order.web.dto.OrderResponses.OrderSummaryView;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Customer order history + detail (09 FR-ORDER-6). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService query;
    private final AccountFeatureGuard accountFeatures;

    public OrderController(OrderQueryService query, AccountFeatureGuard accountFeatures) {
        this.query = query;
        this.accountFeatures = accountFeatures;
    }

    @GetMapping
    public List<OrderSummaryView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return query.listForCustomer(userId);
    }

    @GetMapping("/{orderNumber}")
    public OrderDetailView detail(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable String orderNumber) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return query.detailForCustomer(userId, orderNumber);
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return principal.userId();
    }
}
