package com.scanlanka.order.web;

import com.scanlanka.order.app.OrderQueryService;
import com.scanlanka.order.app.OrderQueryService.OrderStatusView;
import com.scanlanka.shared.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public guest order lookup (09 §3). Rate-limited; needs order# + matching email; uniform 404. */
@RestController
@RequestMapping("/api/orders")
public class OrderLookupController {

    private final OrderQueryService query;
    private final RateLimiter rateLimiter;

    public OrderLookupController(OrderQueryService query, RateLimiter rateLimiter) {
        this.query = query;
        this.rateLimiter = rateLimiter;
    }

    public record LookupRequest(@NotBlank String orderNumber, @Email @NotBlank String email) {}

    @PostMapping("/lookup")
    public OrderStatusView lookup(@Valid @RequestBody LookupRequest req, HttpServletRequest http) {
        rateLimiter.check("orderlookup:" + ip(http), 20, 300);   // anti-enumeration (T-4/T-13)
        return query.lookup(req.orderNumber(), req.email());
    }

    private static String ip(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
