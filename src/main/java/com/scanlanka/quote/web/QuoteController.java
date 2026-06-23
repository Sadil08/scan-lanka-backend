package com.scanlanka.quote.web;

import com.scanlanka.quote.app.QuoteService;
import com.scanlanka.quote.app.QuoteService.ItemInput;
import com.scanlanka.quote.app.QuoteService.SubmitInput;
import com.scanlanka.quote.app.QuoteService.SubmitResult;
import com.scanlanka.shared.captcha.CaptchaGuard;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import com.scanlanka.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quotes;
    private final RateLimiter rateLimiter;
    private final CaptchaGuard captcha;

    public QuoteController(QuoteService quotes, RateLimiter rateLimiter, CaptchaGuard captcha) {
        this.quotes = quotes;
        this.rateLimiter = rateLimiter;
        this.captcha = captcha;
    }

    public record ItemBody(Long productId, Long variantId, @Min(1) int quantity, String note) {}
    public record SubmitBody(
        @NotBlank String requesterName,
        @Email @NotBlank String email,
        @NotBlank String phone,
        String country,
        String message,
        @NotEmpty List<ItemBody> items) {}
    public record MessageBody(@NotBlank String body) {}

    @PostMapping
    public SubmitResult submit(@Valid @RequestBody SubmitBody body,
                               @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                               @AuthenticationPrincipal AuthPrincipal principal,
                               HttpServletRequest req) {
        rateLimiter.check("quote:" + ClientIp.from(req), 5, 3600);
        captcha.verify(captchaToken);
        Long customerId = principal != null ? principal.userId() : null;
        List<ItemInput> items = body.items().stream()
            .map(i -> new ItemInput(i.productId(), i.variantId(), i.quantity(), i.note()))
            .toList();
        return quotes.submit(new SubmitInput(body.requesterName(), body.email(), body.phone(),
            body.country(), body.message(), items, customerId));
    }

    @GetMapping("/{token}")
    public QuoteService.QuoteView detail(@PathVariable String token) {
        return quotes.forToken(token);
    }

    @PostMapping("/{token}/messages")
    public QuoteService.MessageView message(@PathVariable String token, @Valid @RequestBody MessageBody body) {
        return quotes.requesterMessage(token, body.body());
    }

    @PostMapping("/{token}/accept")
    public void accept(@PathVariable String token) {
        quotes.requesterAccept(token);
    }
}
