package com.scanlanka.support.web;

import com.scanlanka.shared.security.AuthPrincipal;
import com.scanlanka.support.app.SupportChatService;
import com.scanlanka.support.app.SupportChatService.ConversationView;
import com.scanlanka.support.app.SupportChatService.MessageBody;
import com.scanlanka.support.app.SupportChatService.SummaryView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/support/conversations")
public class AdminSupportChatController {

    private final SupportChatService chat;

    public AdminSupportChatController(SupportChatService chat) {
        this.chat = chat;
    }

    @GetMapping
    public List<SummaryView> list(@RequestParam(required = false) String status) {
        return chat.adminList(status);
    }

    @GetMapping("/{id}")
    public ConversationView detail(@PathVariable long id) {
        return chat.adminDetail(id);
    }

    @PostMapping("/{id}/messages")
    public ConversationView reply(@PathVariable long id,
                                  @Valid @RequestBody MessageBody body,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return chat.adminMessage(id, principal.userId(), body.body());
    }

    @PostMapping("/{id}/close")
    public void close(@PathVariable long id) {
        chat.close(id);
    }
}
