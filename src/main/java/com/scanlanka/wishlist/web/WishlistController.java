package com.scanlanka.wishlist.web;

import com.scanlanka.auth.app.AccountFeatureGuard;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.shared.security.AuthPrincipal;
import com.scanlanka.wishlist.app.WishlistService;
import com.scanlanka.wishlist.web.dto.WishlistRequests.MergeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Customer wishlist API (03-wishlist §3). Guest lists are client-side. */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlist;
    private final AccountFeatureGuard accountFeatures;

    public WishlistController(WishlistService wishlist, AccountFeatureGuard accountFeatures) {
        this.wishlist = wishlist;
        this.accountFeatures = accountFeatures;
    }

    @GetMapping
    public List<ProductChipDTO> list(@AuthenticationPrincipal AuthPrincipal principal) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return wishlist.list(userId);
    }

    @PostMapping("/{productId}")
    public Map<String, Boolean> add(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long productId) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return Map.of("added", wishlist.add(userId, productId));
    }

    @DeleteMapping("/{productId}")
    public Map<String, Boolean> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long productId) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return Map.of("removed", wishlist.remove(userId, productId));
    }

    @PostMapping("/merge")
    public List<ProductChipDTO> merge(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody MergeRequest req) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return wishlist.merge(userId, req.productIds());
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return principal.userId();
    }
}
