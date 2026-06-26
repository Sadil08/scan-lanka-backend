package com.scanlanka.support.web;

import com.scanlanka.shared.captcha.CaptchaGuard;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import com.scanlanka.shared.security.ClientIp;
import com.scanlanka.support.app.SupportChatService;
import com.scanlanka.support.app.SupportChatService.ConversationView;
import com.scanlanka.support.app.SupportChatService.MessageBody;
import com.scanlanka.support.app.SupportChatService.StartRequest;
import com.scanlanka.support.app.SupportChatService.StartResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportChatController {

    private final SupportChatService chat;
    private final RateLimiter rateLimiter;
    private final CaptchaGuard captcha;

    public SupportChatController(SupportChatService chat, RateLimiter rateLimiter, CaptchaGuard captcha) {
        this.chat = chat;
        this.rateLimiter = rateLimiter;
        this.captcha = captcha;
    }

    public record StartBody(String name, String email, @NotBlank String message, String pageContext) {}

    @PostMapping("/conversations")
    public ResponseEntity<StartResult> start(@Valid @RequestBody StartBody body,
                                             @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                                             @AuthenticationPrincipal AuthPrincipal principal,
                                             HttpServletRequest req) {
        rateLimiter.check("support-start:" + ClientIp.from(req), 10, 3600);
        captcha.verify(captchaToken);
        Long customerId = principal != null ? principal.userId() : null;
        StartResult result = chat.start(new StartRequest(body.name(), body.email(), body.message(), body.pageContext()),
            customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/chat/{token}")
    public ConversationView get(@PathVariable String token) {
        return chat.getByToken(token);
    }

    @PostMapping("/chat/{token}/messages")
    public ConversationView send(@PathVariable String token,
                                 @Valid @RequestBody MessageBody body,
                                 HttpServletRequest req) {
        rateLimiter.check("support-msg:" + token, 60, 3600);
        return chat.visitorMessage(token, body.body());
    }
}
