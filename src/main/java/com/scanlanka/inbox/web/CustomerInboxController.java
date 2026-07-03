package com.scanlanka.inbox.web;

import com.scanlanka.inbox.app.CustomerInboxService;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inbox")
public class CustomerInboxController {

    private final CustomerInboxService inbox;

    public CustomerInboxController(CustomerInboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public CustomerInboxService.InboxPage list(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestParam(defaultValue = "0") int page) {
        return inbox.list(requireUser(principal), page);
    }

    @GetMapping("/summary")
    public CustomerInboxService.Summary summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return inbox.summary(requireUser(principal));
    }

    @PostMapping("/{id}/read")
    public void markRead(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long id) {
        inbox.markRead(requireUser(principal), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        inbox.markAllRead(requireUser(principal));
    }

    private static long requireUser(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        return principal.userId();
    }
}
