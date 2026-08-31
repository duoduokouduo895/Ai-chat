package org.example.ai_agent.controller;

import org.example.ai_agent.service.admin.AdminDashboardQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardQuery adminDashboardQuery;

    public AdminDashboardController(AdminDashboardQuery adminDashboardQuery) {
        this.adminDashboardQuery = adminDashboardQuery;
    }

    @GetMapping("/stats")
    public Mono<Map<String, Long>> getDashboardStats() {
        return adminDashboardQuery.getDashboardStats();
    }
}


