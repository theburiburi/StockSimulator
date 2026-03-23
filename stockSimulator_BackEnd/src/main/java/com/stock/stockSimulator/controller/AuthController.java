package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.security.OAuthHelper;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * DIP: Repository 직접 접근 제거, MemberService + OAuthHelper 사용
 * DRY: OAuth2 이메일 파싱을 OAuthHelper로 위임
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final OAuthHelper oAuthHelper;

    @GetMapping("/me")
    public Member getMe(@AuthenticationPrincipal OAuth2User oauthUser) {
        return oAuthHelper.findMember(oauthUser).orElse(null);
    }

    @GetMapping("/portfolio")
    public List<MemberStock> getPortfolio(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = oAuthHelper.findMember(oauthUser).orElse(null);
        if (member == null) return Collections.emptyList();
        return memberService.getPortfolio(member.getId());
    }

    @GetMapping("/orders")
    public List<StockOrder> getMyOrders(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = oAuthHelper.findMember(oauthUser).orElse(null);
        if (member == null) return Collections.emptyList();
        return memberService.getMyOrders(member.getId());
    }
}
