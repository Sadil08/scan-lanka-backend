package com.scanlanka.cart.app;

import com.scanlanka.cart.app.CartPricingService.LineInput;
import com.scanlanka.cart.app.CartPricingService.PricedCart;
import com.scanlanka.cart.app.CartPricingService.PricedLine;
import com.scanlanka.cart.domain.Cart;
import com.scanlanka.cart.domain.CartItem;
import com.scanlanka.cart.infra.CartItemRepository;
import com.scanlanka.cart.infra.CartRepository;
import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.LinePricing;
import com.scanlanka.shared.security.RlsContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart logic (04-cart). Guest carts are validated/priced server-side (no persistence); customer carts
 * persist and are RLS-scoped (the GUC is set per transaction). Prices/stock are always server-authoritative.
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final CartItemRepository items;
    private final CartPricingService pricing;
    private final ProductLookupService lookup;
    private final RlsContext rls;

    public CartService(CartRepository carts, CartItemRepository items, CartPricingService pricing,
                       ProductLookupService lookup, RlsContext rls) {
        this.carts = carts;
        this.items = items;
        this.pricing = pricing;
        this.lookup = lookup;
        this.rls = rls;
    }

    public record CartLineView(Long itemId, Long productId, Long variantId, String name, int quantity,
                               long unitPriceCents, long lineTotalCents, String status) {}

    public record CartView(List<CartLineView> lines, long subtotalCents) {}

    /** Guest cart: price the client-held lines (no persistence). */
    public PricedCart validate(List<LineInput> lineInputs) {
        return pricing.price(lineInputs);
    }

    @Transactional
    public CartView getCart(long userId) {
        rls.setCurrentUser(userId);
        return toView(cartFor(userId));
    }

    @Transactional
    public CartView addItem(long userId, Long productId, Long variantId, int quantity) {
        rls.setCurrentUser(userId);
        Cart cart = cartFor(userId);
        LinePricing lp = lookup.resolveLine(productId, variantId)
            .orElseThrow(() -> badRequest("INVALID_PRODUCT"));
        CartItem existing = items.findByCartIdAndProductIdAndVariantId(cart.getId(), productId, variantId).orElse(null);
        int desired = (existing != null ? existing.getQuantity() : 0) + quantity;
        assertWithinStock(lp, desired);
        if (existing != null) {
            existing.setQuantity(desired);
            items.save(existing);
        } else {
            items.save(new CartItem(cart.getId(), productId, variantId, quantity));
        }
        return toView(cart);
    }

    @Transactional
    public CartView updateQuantity(long userId, Long itemId, int quantity) {
        rls.setCurrentUser(userId);
        Cart cart = cartFor(userId);
        if (quantity < 1) throw badRequest("INVALID_QUANTITY");
        CartItem item = ownItem(cart, itemId);
        LinePricing lp = lookup.resolveLine(item.getProductId(), item.getVariantId())
            .orElseThrow(() -> badRequest("INVALID_PRODUCT"));
        assertWithinStock(lp, quantity);
        item.setQuantity(quantity);
        items.save(item);
        return toView(cart);
    }

    @Transactional
    public CartView removeItem(long userId, Long itemId) {
        rls.setCurrentUser(userId);
        Cart cart = cartFor(userId);
        items.findById(itemId)
            .filter(i -> i.getCartId().equals(cart.getId()))   // ownership (app + RLS)
            .ifPresent(items::delete);
        return toView(cart);
    }

    @Transactional
    public CartView merge(long userId, List<LineInput> guestItems) {
        rls.setCurrentUser(userId);
        Cart cart = cartFor(userId);
        for (LineInput in : guestItems) {
            LinePricing lp = lookup.resolveLine(in.productId(), in.variantId()).orElse(null);
            if (lp == null) continue;
            CartItem existing = items.findByCartIdAndProductIdAndVariantId(cart.getId(), in.productId(), in.variantId()).orElse(null);
            int merged = (existing != null ? existing.getQuantity() : 0) + in.quantity();
            if (lp.stock() != null) merged = Math.min(merged, lp.stock()); // sum then clamp (Q-2)
            if (merged < 1) continue;
            if (existing != null) {
                existing.setQuantity(merged);
                items.save(existing);
            } else {
                items.save(new CartItem(cart.getId(), in.productId(), in.variantId(), merged));
            }
        }
        return toView(cart);
    }

    // --- helpers ---

    private Cart cartFor(long userId) {
        return carts.findByCustomerId(userId).orElseGet(() -> carts.save(new Cart(userId)));
    }

    private CartItem ownItem(Cart cart, Long itemId) {
        return items.findById(itemId)
            .filter(i -> i.getCartId().equals(cart.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found")); // no enumeration
    }

    private CartView toView(Cart cart) {
        List<CartItem> cartItems = items.findByCartId(cart.getId());
        List<LineInput> inputs = cartItems.stream()
            .map(i -> new LineInput(i.getProductId(), i.getVariantId(), i.getQuantity())).toList();
        PricedCart priced = pricing.price(inputs);
        List<CartLineView> lines = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem it = cartItems.get(i);
            PricedLine pl = priced.lines().get(i);
            lines.add(new CartLineView(it.getId(), pl.productId(), pl.variantId(), pl.name(),
                pl.quantity(), pl.unitPriceCents(), pl.lineTotalCents(), pl.status()));
        }
        return new CartView(lines, priced.subtotalCents());
    }

    private static void assertWithinStock(LinePricing lp, int desired) {
        if (lp.stock() != null && desired > lp.stock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "STOCK_EXCEEDED");
        }
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
