package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.Role;
import com.stock.stockSimulator.security.OAuthHelper;
import com.stock.stockSimulator.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DRY: 중복되던 OAuth2 이메일 파싱을 OAuthHelper로 위임
 * SRP: 어드민 마켓 제어만 담당
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OAuthHelper oAuthHelper;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/open")
    public String openMarket(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = oAuthHelper.findMember(oauthUser)
                .filter(m -> m.getRole() == Role.ADMIN)
                .orElse(null);

        if (member == null) {
            log.warn("Unauthorized openMarket attempt.");
            return "Unauthorized: Admin role required.";
        }

        log.info("Admin [{}] is opening the market.", member.getEmail());
        Long botId = adminService.openMarket(member.getId());
        return "Market Opened by Admin " + member.getName() + "! (Bot ID: " + botId + ")";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/close")
    public String closeMarket(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = oAuthHelper.findMember(oauthUser)
                .filter(m -> m.getRole() == Role.ADMIN)
                .orElse(null);

        if (member == null) {
            log.warn("Unauthorized closeMarket attempt.");
            return "Unauthorized: Admin role required.";
        }

        log.info("Admin [{}] is closing the market.", member.getEmail());
        adminService.closeMarket(member.getId());
        return "Market Closed by Admin " + member.getName();
    }
}
