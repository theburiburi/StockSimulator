package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.security.CurrentUser;
import com.stock.stockSimulator.security.CurrentUserInfo;
import com.stock.stockSimulator.service.AdminService;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @CurrentUser CurrentUserInfo user → JWT payload의 userId 직접 사용
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
    public String openMarket(@CurrentUser CurrentUserInfo user) {
        Member member = memberService.findById(user.userId());
        log.info("Admin [{}] is opening the market.", user.email());
        Long botId = adminService.openMarket(member.getId());
        return "Market Opened by Admin " + member.getName() + "! (Bot ID: " + botId + ")";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/close")
    public String closeMarket(@CurrentUser CurrentUserInfo user) {
        Member member = memberService.findById(user.userId());
        log.info("Admin [{}] is closing the market.", user.email());
        adminService.closeMarket(member.getId());
        return "Market Closed by Admin " + member.getName();
    }
}
