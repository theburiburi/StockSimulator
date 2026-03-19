package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository orderRepository;

    @GetMapping("/portfolio")
    public List<MemberStock> getPortfolio(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = getMe(oauthUser);
        if (member == null) return Collections.emptyList();
        return memberStockRepository.findAllByMemberId(member.getId());
    }

    @GetMapping("/orders")
    public List<StockOrder> getMyOrders(@AuthenticationPrincipal OAuth2User oauthUser) {
        Member member = getMe(oauthUser);
        if (member == null) return Collections.emptyList();
        return orderRepository.findByMemberIdOrderByCreatedAtDesc(member.getId());
    }

    @GetMapping("/me")
    public Member getMe(@AuthenticationPrincipal OAuth2User oauthUser) {
        if (oauthUser == null) return null;
        
        Map<String, Object> kakaoAccount = oauthUser.getAttribute("kakao_account");
        String email = kakaoAccount != null && kakaoAccount.containsKey("email") ? (String) kakaoAccount.get("email") : oauthUser.getAttribute("email");
        
        Object kakaoIdObj = oauthUser.getAttributes().get("id");
        String kakaoIdString = kakaoIdObj != null ? String.valueOf(kakaoIdObj) : "unknown";
        
        if (email == null) email = kakaoIdString + "@kakao.com";
        
        return memberRepository.findByEmail(email).orElse(null);
    }
}
