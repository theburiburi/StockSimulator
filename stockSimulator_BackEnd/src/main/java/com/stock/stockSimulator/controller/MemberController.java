package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.dto.request.CreateMemberRequest;
import com.stock.stockSimulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * DIP: MemberRepository 직접 접근 제거, MemberService 사용
 * SRP: Inner DTO 클래스를 dto/request 패키지로 분리
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public Member getMember(@PathVariable Long id) {
        return memberService.findById(id);
    }

    @PostMapping
    public Member createMember(@RequestBody CreateMemberRequest request) {
        return memberService.createMember(request);
    }
}
