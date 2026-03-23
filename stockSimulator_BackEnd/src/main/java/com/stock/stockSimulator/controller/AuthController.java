package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.security.CurrentUser;
import com.stock.stockSimulator.security.CurrentUserInfo;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * @CurrentUser CurrentUserInfo user → JWT payload에서 DB 조회 없이 userId 직접 사용
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @GetMapping("/me")
    public Member getMe(@CurrentUser CurrentUserInfo user) {
        if (user == null) return null;
        return memberService.findById(user.userId());
    }

    @GetMapping("/portfolio")
    public List<MemberStock> getPortfolio(@CurrentUser CurrentUserInfo user) {
        if (user == null) return Collections.emptyList();
        return memberService.getPortfolio(user.userId());
    }

    @GetMapping("/orders")
    public List<StockOrder> getMyOrders(@CurrentUser CurrentUserInfo user) {
        if (user == null) return Collections.emptyList();
        return memberService.getMyOrders(user.userId());
    }
}
