package com.scanlanka.checkout.web;

import com.scanlanka.auth.app.AccountFeatureGuard;
import com.scanlanka.checkout.app.AddressService;
import com.scanlanka.checkout.web.dto.AddressRequests.AddressInput;
import com.scanlanka.checkout.web.dto.AddressRequests.AddressView;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Customer saved addresses (05 §3). */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addresses;
    private final AccountFeatureGuard accountFeatures;

    public AddressController(AddressService addresses, AccountFeatureGuard accountFeatures) {
        this.addresses = addresses;
        this.accountFeatures = accountFeatures;
    }

    @GetMapping
    public List<AddressView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return addresses.list(userId);
    }

    @PostMapping
    public AddressView create(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody AddressInput input) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return addresses.create(userId, input);
    }

    @PutMapping("/{id}")
    public AddressView update(@AuthenticationPrincipal AuthPrincipal principal,
                              @PathVariable Long id,
                              @Valid @RequestBody AddressInput input) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return addresses.update(userId, id, input);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        addresses.delete(userId, id);
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return principal.userId();
    }
}
