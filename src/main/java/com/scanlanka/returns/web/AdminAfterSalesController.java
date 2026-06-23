package com.scanlanka.returns.web;

import com.scanlanka.returns.app.AfterSalesService;
import com.scanlanka.returns.app.AfterSalesService.CancelRequest;
import com.scanlanka.returns.app.AfterSalesService.RecordRefundRequest;
import com.scanlanka.returns.app.AfterSalesService.RefundView;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin after-sales endpoints (16 §3). Step-up required on mutations. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminAfterSalesController {

    private final AfterSalesService afterSales;

    public AdminAfterSalesController(AfterSalesService afterSales) {
        this.afterSales = afterSales;
    }

    @PostMapping("/{orderNumber}/cancel")
    public void cancel(@PathVariable String orderNumber, @RequestBody CancelRequest req,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        afterSales.cancelOrder(orderNumber, req, principal.userId());
    }

    @PostMapping("/{orderNumber}/refunds")
    public RefundView recordRefund(@PathVariable String orderNumber, @RequestBody RecordRefundRequest req,
                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return afterSales.recordRefund(orderNumber, req, principal.userId());
    }

    @GetMapping("/{orderNumber}/refunds")
    public List<RefundView> listRefunds(@PathVariable String orderNumber) {
        return afterSales.listRefunds(orderNumber);
    }
}
