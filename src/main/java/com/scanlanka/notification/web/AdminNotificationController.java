package com.scanlanka.notification.web;

import com.scanlanka.notification.app.AdminNotificationService;
import com.scanlanka.notification.app.AdminNotificationService.NotificationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin notification audit (10 FR-NOTIFY-7). Under /api/admin/** → ADMIN-gated. */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationService notifications;

    public AdminNotificationController(AdminNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationView> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size) {
        return notifications.list(status, PageRequest.of(Math.max(0, page), Math.min(size, 100)));
    }

    @PostMapping("/{id}/resend")
    public void resend(@PathVariable long id) {
        notifications.resend(id);
    }
}
