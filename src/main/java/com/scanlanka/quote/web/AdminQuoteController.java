package com.scanlanka.quote.web;

import com.scanlanka.quote.app.QuoteService;
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

@RestController
@RequestMapping("/api/admin/quotes")
public class AdminQuoteController {

    private final QuoteService quotes;

    public AdminQuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    public record AdminMessageBody(String body, Long quotedPriceCents) {}
    public record ConvertResult(String orderNumber) {}

    @GetMapping
    public Page<QuoteService.QuoteView> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return quotes.adminList(status, PageRequest.of(Math.max(0, page), Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public QuoteService.QuoteView detail(@PathVariable long id) {
        return quotes.adminDetail(id);
    }

    @PostMapping("/{id}/messages")
    public QuoteService.MessageView message(@PathVariable long id, @RequestBody AdminMessageBody body,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return quotes.adminMessage(id, principal.userId(), body.body(), body.quotedPriceCents());
    }

    @PostMapping("/{id}/accept")
    public void accept(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        quotes.adminAccept(id, principal.userId());
    }

    @PostMapping("/{id}/convert")
    public ConvertResult convert(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return new ConvertResult(quotes.convertToOrder(id, principal.userId()));
    }
}
