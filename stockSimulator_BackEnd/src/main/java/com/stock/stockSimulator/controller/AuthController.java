package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * JWT 인증: @AuthenticationPrincipal String email (JwtAuthFilter에서 설정)
 * DIP: MemberService를 통해 데이터 조회
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @GetMapping("/me")
    public Member getMe(@AuthenticationPrincipal String email) {
        if (email == null) return null;
        try {
            return memberService.findByEmail(email);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/portfolio")
    public List<MemberStock> getPortfolio(@AuthenticationPrincipal String email) {
        if (email == null) return Collections.emptyList();
        try {
            Member member = memberService.findByEmail(email);
            return memberService.getPortfolio(member.getId());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @GetMapping("/orders")
    public List<StockOrder> getMyOrders(@AuthenticationPrincipal String email) {
        if (email == null) return Collections.emptyList();
        try {
            Member member = memberService.findByEmail(email);
            return memberService.getMyOrders(member.getId());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
