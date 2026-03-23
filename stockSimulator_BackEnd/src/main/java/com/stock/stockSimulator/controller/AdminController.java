package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.service.AdminService;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT 인증: @AuthenticationPrincipal String email
 * SRP: 어드민 마켓 제어만 담당
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final MemberService memberService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/open")
    public String openMarket(@AuthenticationPrincipal String email) {
        Member member = memberService.findByEmail(email);
        log.info("Admin [{}] is opening the market.", member.getEmail());
        Long botId = adminService.openMarket(member.getId());
        return "Market Opened by Admin " + member.getName() + "! (Bot ID: " + botId + ")";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/close")
    public String closeMarket(@AuthenticationPrincipal String email) {
        Member member = memberService.findByEmail(email);
        log.info("Admin [{}] is closing the market.", member.getEmail());
        adminService.closeMarket(member.getId());
        return "Market Closed by Admin " + member.getName();
    }
}
