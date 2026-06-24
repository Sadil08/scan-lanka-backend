package com.scanlanka.admin.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin dashboard counters (08 FR-ADMIN-7). */
@Service
public class AdminDashboardService {

    private static final int LOW_STOCK_THRESHOLD = 3;

    private final OrderRepository orders;
    private final ProductRepository products;

    public AdminDashboardService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    public record LowStockItem(long id, String name, String sku, int stockQty) {}
    public record DashboardView(long pendingPayment, long awaitingBank, long paid, long inFulfilment,
                                long delivered, long cancelled, List<LowStockItem> lowStock) {}

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        long pending = orders.countByStatus(OrderStatus.PENDING_PAYMENT)
            + orders.countByStatus(OrderStatus.PAYMENT_FAILED)
            + orders.countByStatus(OrderStatus.BANK_SLIP_REJECTED);
        long awaitingBank = orders.countByStatus(OrderStatus.AWAITING_BANK_CONFIRMATION);
        long paid = orders.countByStatus(OrderStatus.PAID)
            + orders.countByStatus(OrderStatus.CONFIRMED);
        long inFulfilment = orders.countByStatus(OrderStatus.PACKED)
            + orders.countByStatus(OrderStatus.SHIPPED)
            + orders.countByStatus(OrderStatus.READY_FOR_PICKUP)
            + orders.countByStatus(OrderStatus.DELIVERY_FAILED);
        long delivered = orders.countByStatus(OrderStatus.COMPLETED);
        long cancelled = orders.countByStatus(OrderStatus.CANCELLED);
        List<LowStockItem> low = products.findLowStock(LOW_STOCK_THRESHOLD).stream()
            .map(AdminDashboardService::toLowStock).toList();
        return new DashboardView(pending, awaitingBank, paid, inFulfilment, delivered, cancelled, low);
    }

    private static LowStockItem toLowStock(Product p) {
        return new LowStockItem(p.getId(), p.getName(), p.getSku(), p.getStockQty() != null ? p.getStockQty() : 0);
    }
}
