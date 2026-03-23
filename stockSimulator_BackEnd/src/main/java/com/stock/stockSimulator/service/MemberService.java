package com.stock.stockSimulator.service;

import com.stock.stockSimulator.common.exception.BusinessException;
import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.MemberStock;
import com.stock.stockSimulator.domain.StockOrder;
import com.stock.stockSimulator.dto.request.CreateMemberRequest;
import com.stock.stockSimulator.repository.MemberRepository;
import com.stock.stockSimulator.repository.MemberStockRepository;
import com.stock.stockSimulator.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 관련 비즈니스 로직 담당
 * SRP: AuthController, MemberController에서 Repository를 직접 사용하던 로직을 통합
 * DIP: Controller가 Repository가 아닌 Service에 의존하도록 변경
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberStockRepository memberStockRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다. ID: " + id));
    }

    @Transactional(readOnly = true)
    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다. Email: " + email));
    }

    @Transactional(readOnly = true)
    public List<MemberStock> getPortfolio(Long memberId) {
        return memberStockRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<StockOrder> getMyOrders(Long memberId) {
        return orderRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional
    public Member createMember(CreateMemberRequest request) {
        Member member = Member.builder()
                .name(request.getName())
                .email(request.getEmail())
                .role(request.getRole())
                .build();
        return memberRepository.save(member);
    }
}
