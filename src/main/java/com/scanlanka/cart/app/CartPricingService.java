package com.scanlanka.cart.app;

import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.LinePricing;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side cart pricing (04-cart FR-CART-3/4/7, SEC-CART-2). The client sends only ids + quantity;
 * prices and the stock cap are computed here. Unavailable lines are flagged + excluded from the subtotal.
 */
@Service
public class CartPricingService {

    private final ProductLookupService lookup;

    public CartPricingService(ProductLookupService lookup) {
        this.lookup = lookup;
    }

    public record LineInput(Long productId, Long variantId, int quantity) {}

    public record PricedLine(Long productId, Long variantId, String name, int quantity,
                             long unitPriceCents, long lineTotalCents, String status) {}

    public record PricedCart(List<PricedLine> lines, long subtotalCents) {}

    public PricedCart price(List<LineInput> items) {
        List<PricedLine> lines = new ArrayList<>();
        long subtotal = 0;
        for (LineInput in : items) {
            LinePricing lp = lookup.resolveLine(in.productId(), in.variantId()).orElse(null);
            if (lp == null) {
                lines.add(new PricedLine(in.productId(), in.variantId(), null, in.quantity(), 0, 0, "UNAVAILABLE"));
                continue;
            }
            Integer stock = lp.stock();              // null = unlimited
            if (stock != null && stock <= 0) {
                lines.add(new PricedLine(in.productId(), in.variantId(), lp.name(), in.quantity(), lp.unitPriceCents(), 0, "OUT_OF_STOCK"));
                continue;
            }
            int qty = in.quantity();
            String status = "OK";
            if (stock != null && qty > stock) {      // cap to available
                qty = stock;
                status = "CAPPED";
            }
            long lineTotal = lp.unitPriceCents() * (long) qty;
            lines.add(new PricedLine(in.productId(), in.variantId(), lp.name(), qty, lp.unitPriceCents(), lineTotal, status));
            subtotal += lineTotal;
        }
        return new PricedCart(lines, subtotal);
    }
}
