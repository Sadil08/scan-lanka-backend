package com.scanlanka.contact.web;

import com.scanlanka.contact.app.ContactService;
import com.scanlanka.contact.app.ContactService.SubmitRequest;
import com.scanlanka.contact.app.ContactService.WhatsAppView;
import com.scanlanka.shared.captcha.CaptchaGuard;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contact;
    private final RateLimiter rateLimiter;
    private final CaptchaGuard captcha;

    public ContactController(ContactService contact, RateLimiter rateLimiter, CaptchaGuard captcha) {
        this.contact = contact;
        this.rateLimiter = rateLimiter;
        this.captcha = captcha;
    }

    public record ContactBody(
        @NotBlank String name,
        @Email @NotBlank String email,
        String phone,
        @NotBlank String message) {}

    @PostMapping
    public ContactService.InquiryView submit(@Valid @RequestBody ContactBody body,
                                             @RequestHeader(value = "X-Captcha-Token", required = false) String captchaToken,
                                             HttpServletRequest req) {
        rateLimiter.check("contact:" + ClientIp.from(req), 5, 3600);
        captcha.verify(captchaToken);
        return contact.submit(new SubmitRequest(body.name(), body.email(), body.phone(), body.message()));
    }

    @GetMapping("/whatsapp")
    public WhatsAppView whatsapp(@RequestParam(required = false) String country,
                                 @RequestParam(required = false) String context,
                                 HttpServletRequest req) {
        return contact.whatsapp(ClientIp.from(req), country, context);
    }
}
