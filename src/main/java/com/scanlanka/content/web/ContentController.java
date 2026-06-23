package com.scanlanka.content.web;

import com.scanlanka.content.app.ContentService;
import com.scanlanka.content.app.ContentService.ContentView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService content;

    public ContentController(ContentService content) {
        this.content = content;
    }

    @GetMapping("/{slug}")
    public ContentView get(@PathVariable String slug) {
        return content.get(slug);
    }
}
