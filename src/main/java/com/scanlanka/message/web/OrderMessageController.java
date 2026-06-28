package com.scanlanka.message.web;

import com.scanlanka.auth.app.AccountFeatureGuard;
import com.scanlanka.message.app.OrderMessageService;
import com.scanlanka.message.app.OrderMessageService.MessageBody;
import com.scanlanka.message.app.OrderMessageService.ThreadView;
import com.scanlanka.message.app.OrderMessageService.TokenResult;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import com.scanlanka.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderMessageController {

    private final OrderMessageService messages;
    private final AccountFeatureGuard accountFeatures;
    private final RateLimiter rateLimiter;

    public OrderMessageController(OrderMessageService messages, AccountFeatureGuard accountFeatures,
                                  RateLimiter rateLimiter) {
        this.messages = messages;
        this.accountFeatures = accountFeatures;
        this.rateLimiter = rateLimiter;
    }

    public record LookupTokenRequest(@NotBlank String orderNumber, @NotBlank @Email String email) {}

    @PostMapping("/lookup/thread-token")
    public TokenResult guestToken(@Valid @RequestBody LookupTokenRequest req, HttpServletRequest http) {
        rateLimiter.check("orderlookup:" + ClientIp.from(http), 20, 300);
        return messages.issueGuestToken(req.orderNumber(), req.email());
    }

    @GetMapping("/messages/{token}")
    public ThreadView guestThread(@PathVariable String token) {
        return messages.forGuestToken(token);
    }

    @PostMapping("/messages/{token}/messages")
    public ThreadView guestPost(@PathVariable String token,
                                @Valid @RequestBody MessageBody body,
                                HttpServletRequest http) {
        rateLimiter.check("order-msg:" + ClientIp.from(http), 30, 3600);
        return messages.postGuest(token, body.body());
    }

    @PostMapping("/messages/{token}/read")
    public void guestRead(@PathVariable String token) {
        messages.markGuestRead(token);
    }

    @GetMapping("/{orderNumber}/thread")
    public ThreadView customerThread(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String orderNumber) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        return messages.forCustomer(userId, orderNumber);
    }

    @PostMapping("/{orderNumber}/thread/messages")
    public ThreadView customerPost(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable String orderNumber,
                                   @Valid @RequestBody MessageBody body,
                                   HttpServletRequest http) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        rateLimiter.check("order-msg:" + userId, 30, 3600);
        return messages.postCustomer(userId, orderNumber, body.body());
    }

    @PostMapping("/{orderNumber}/thread/read")
    public void customerRead(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable String orderNumber) {
        long userId = requireUser(principal);
        accountFeatures.requireVerifiedEmail(userId);
        messages.markCustomerRead(userId, orderNumber);
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal.userId();
    }
}
