package com.stock.stockSimulator.controller;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.Role;
import com.stock.stockSimulator.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    @GetMapping("/{id}")
    public Member getMember(@PathVariable Long id) {
        return memberRepository.findById(id).orElse(null);
    }

    @PostMapping
    public Member createMember(@RequestBody MemberRequest request) {
        Member member = Member.builder()
                .name(request.getName())
                .email(request.getEmail())
                .role(request.getRole())
                .build();
        return memberRepository.save(member);
    }

    @lombok.Data
    public static class MemberRequest {
        private String name;
        private String email;
        private Role role;
    }
}
