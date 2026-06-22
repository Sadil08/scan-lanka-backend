package com.scanlanka.cart.web;

import com.scanlanka.cart.app.CartPricingService.LineInput;
import com.scanlanka.cart.app.CartPricingService.PricedCart;
import com.scanlanka.cart.app.CartService;
import com.scanlanka.cart.app.CartService.CartView;
import com.scanlanka.cart.web.dto.CartRequests.AddItemRequest;
import com.scanlanka.cart.web.dto.CartRequests.CartItemsRequest;
import com.scanlanka.cart.web.dto.CartRequests.ItemDTO;
import com.scanlanka.cart.web.dto.CartRequests.QuantityRequest;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Cart API (04-cart §3). /validate is public (guest); the rest require the logged-in customer. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    /** Guest cart: price the client-held lines. Public. */
    @PostMapping("/validate")
    public PricedCart validate(@RequestBody CartItemsRequest req) {
        return cart.validate(toInputs(req.items()));
    }

    @GetMapping
    public CartView get(@AuthenticationPrincipal AuthPrincipal principal) {
        return cart.getCart(requireUser(principal));
    }

    @PostMapping("/items")
    public CartView add(@AuthenticationPrincipal AuthPrincipal principal,
                        @Valid @RequestBody AddItemRequest req) {
        return cart.addItem(requireUser(principal), req.productId(), req.variantId(), req.quantity());
    }

    @PatchMapping("/items/{id}")
    public CartView update(@AuthenticationPrincipal AuthPrincipal principal,
                           @PathVariable Long id, @Valid @RequestBody QuantityRequest req) {
        return cart.updateQuantity(requireUser(principal), id, req.quantity());
    }

    @DeleteMapping("/items/{id}")
    public CartView remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return cart.removeItem(requireUser(principal), id);
    }

    @PostMapping("/merge")
    public CartView merge(@AuthenticationPrincipal AuthPrincipal principal,
                          @RequestBody CartItemsRequest guestCart) {
        return cart.merge(requireUser(principal), toInputs(guestCart.items()));
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return principal.userId();
    }

    private static List<LineInput> toInputs(List<ItemDTO> items) {
        if (items == null) return List.of();
        return items.stream().map(i -> new LineInput(i.productId(), i.variantId(), i.quantity())).toList();
    }
}
