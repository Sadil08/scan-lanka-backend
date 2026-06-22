package com.scanlanka.order.web;

import com.scanlanka.order.app.AdminOrderService;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin orders board (08 §3). Under /api/admin/** → ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService admin;

    public AdminOrderController(AdminOrderService admin) {
        this.admin = admin;
    }

    public record StatusRequest(String to, String note) {}

    @GetMapping
    public Page<AdminOrderService.OrderSummary> list(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return admin.list(status, PageRequest.of(Math.max(0, page), Math.min(size, 100)));
    }

    @GetMapping("/{orderNumber}")
    public AdminOrderService.OrderDetailView detail(@PathVariable String orderNumber) {
        return admin.detail(orderNumber);
    }

    @PostMapping("/{orderNumber}/status")
    public void updateStatus(@PathVariable String orderNumber, @RequestBody StatusRequest req,
                             @AuthenticationPrincipal AuthPrincipal principal) {
        admin.updateStatus(orderNumber, req.to(), principal != null ? principal.userId() : null, req.note());
    }

    @GetMapping("/{orderNumber}/dispatch-summary")
    public AdminOrderService.DispatchSummary dispatch(@PathVariable String orderNumber) {
        return admin.dispatchSummary(orderNumber);
    }

    @GetMapping("/{orderNumber}/receipt.pdf")
    public org.springframework.http.ResponseEntity<byte[]> receiptPdf(@PathVariable String orderNumber) {
        return admin.receiptPdf(orderNumber);
    }

    @GetMapping("/dashboard")
    public AdminOrderService.DashboardCounts dashboard() {
        return admin.dashboardCounts();
    }
}
