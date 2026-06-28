package com.scanlanka.message.web;

import com.scanlanka.message.app.OrderMessageService;
import com.scanlanka.message.app.OrderMessageService.AdminThreadView;
import com.scanlanka.message.app.OrderMessageService.MessageBody;
import com.scanlanka.message.app.OrderMessageService.ThreadSummary;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/messages/threads")
public class AdminOrderMessageController {

    private final OrderMessageService messages;
    private final RateLimiter rateLimiter;

    public AdminOrderMessageController(OrderMessageService messages, RateLimiter rateLimiter) {
        this.messages = messages;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public Page<ThreadSummary> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false, defaultValue = "false") boolean unread,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "30") int size) {
        return messages.adminList(status, unread, q, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public AdminThreadView detail(@PathVariable long id) {
        return messages.adminDetail(id);
    }

    @PostMapping("/{id}/messages")
    public AdminThreadView reply(@PathVariable long id,
                                 @Valid @RequestBody MessageBody body,
                                 @AuthenticationPrincipal AuthPrincipal principal,
                                 HttpServletRequest http) {
        rateLimiter.check("order-msg-admin:" + principal.userId(), 60, 3600);
        return messages.adminReply(id, principal.userId(), body.body());
    }

    @PostMapping("/{id}/close")
    public AdminThreadView close(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return messages.adminClose(id, principal.userId());
    }

    @PostMapping("/{id}/reopen")
    public AdminThreadView reopen(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return messages.adminReopen(id, principal.userId());
    }
}
