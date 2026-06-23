package com.scanlanka.content.web;

import com.scanlanka.content.app.ContentService;
import com.scanlanka.content.app.ContentService.ContentView;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    private final ContentService content;

    public AdminContentController(ContentService content) {
        this.content = content;
    }

    public record SaveBody(String title, String bodyHtml) {}

    @GetMapping
    public List<ContentView> list() {
        return content.listAll();
    }

    @GetMapping("/{slug}")
    public ContentView get(@PathVariable String slug) {
        return content.get(slug);
    }

    @PutMapping("/{slug}")
    public ContentView save(@PathVariable String slug, @RequestBody SaveBody body,
                            @AuthenticationPrincipal AuthPrincipal principal) {
        return content.save(slug, body.title(), body.bodyHtml(), principal.userId());
    }
}
