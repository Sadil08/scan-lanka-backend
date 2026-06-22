package com.scanlanka.admin.web;

import com.scanlanka.admin.app.AdminDashboardService;
import com.scanlanka.admin.app.AdminDashboardService.DashboardView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboard;

    public AdminDashboardController(AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardView dashboard() {
        return dashboard.dashboard();
    }
}
