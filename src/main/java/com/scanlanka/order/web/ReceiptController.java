package com.scanlanka.order.web;

import com.scanlanka.auth.app.AccountFeatureGuard;
import com.scanlanka.order.app.OrderQueryService;
import com.scanlanka.order.app.receipt.ReceiptService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Receipt PDF download (09 FR-18). Owner, guest lookup, or admin. */
@RestController
@RequestMapping("/api/orders")
public class ReceiptController {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ReceiptService receipts;
    private final OrderQueryService query;
    private final AccountFeatureGuard accountFeatures;
    private final RateLimiter rateLimiter;

    public ReceiptController(OrderRepository orders, OrderItemRepository items, ReceiptService receipts,
                             OrderQueryService query, AccountFeatureGuard accountFeatures,
                             RateLimiter rateLimiter) {
        this.orders = orders;
        this.items = items;
        this.receipts = receipts;
        this.query = query;
        this.accountFeatures = accountFeatures;
        this.rateLimiter = rateLimiter;
    }

    public record LookupRequest(@NotBlank String orderNumber, @Email @NotBlank String email) {}

    @GetMapping("/{orderNumber}/receipt.pdf")
    public ResponseEntity<byte[]> customerReceipt(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable String orderNumber) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        accountFeatures.requireVerifiedEmail(principal.userId());
        Order order = orders.findByOrderNumber(orderNumber)
            .filter(o -> o.getCustomerId() != null && o.getCustomerId().equals(principal.userId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        return pdfResponse(order, items.findByOrderId(order.getId()));
    }

    @PostMapping("/lookup/receipt.pdf")
    public ResponseEntity<byte[]> guestReceipt(@Valid @RequestBody LookupRequest req, HttpServletRequest http) {
        rateLimiter.check("orderlookup:" + ip(http), 20, 300);
        query.lookup(req.orderNumber(), req.email()); // authz + 404 uniform
        Order order = orders.findByOrderNumber(req.orderNumber()).orElseThrow(notFound());
        return pdfResponse(order, items.findByOrderId(order.getId()));
    }

    private ResponseEntity<byte[]> pdfResponse(Order order, List<OrderItem> lines) {
        byte[] pdf = receipts.ensurePdf(order, lines);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"SL-" + order.getOrderNumber() + "-receipt.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    private static java.util.function.Supplier<ResponseStatusException> notFound() {
        return () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }

    private static String ip(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
