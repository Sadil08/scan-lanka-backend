package com.scanlanka.contact.web;

import com.scanlanka.contact.app.ContactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {

    private final ContactService contact;

    public AdminInquiryController(ContactService contact) {
        this.contact = contact;
    }

    @GetMapping
    public List<ContactService.InquiryView> list(@RequestParam(required = false) String status) {
        return contact.list(status);
    }

    @PostMapping("/{id}/handled")
    public void handled(@PathVariable long id) {
        contact.markHandled(id);
    }
}
