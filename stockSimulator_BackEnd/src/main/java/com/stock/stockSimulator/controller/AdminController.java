package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/open")
    public String openMarket() {
        Long adminId = adminService.openMarket();
        return "Market Opened with Admin Dummy Liquidity! (Admin ID: " + adminId + ")";
    }

    @PostMapping("/close")
    public String closeMarket() {
        adminService.closeMarket();
        return "Market Closed and Orders Cancelled";
    }
}
