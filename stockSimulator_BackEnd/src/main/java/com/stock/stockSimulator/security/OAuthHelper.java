package com.stock.stockSimulator.security;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * OAuth2User에서 이메일/사용자 정보를 추출하는 공통 헬퍼
 * DRY 원칙: AuthController, AdminController 등에서 중복되던 파싱 로직 통합
 */
@Component
@RequiredArgsConstructor
public class OAuthHelper {

    private final MemberRepository memberRepository;

    /**
     * OAuth2User에서 이메일을 추출합니다.
     */
    public String extractEmail(OAuth2User oauthUser) {
        if (oauthUser == null) return null;

        Map<String, Object> kakaoAccount = oauthUser.getAttribute("kakao_account");
        String email = (kakaoAccount != null && kakaoAccount.containsKey("email"))
                ? (String) kakaoAccount.get("email")
                : oauthUser.getAttribute("email");

        if (email == null) {
            Object kakaoId = oauthUser.getAttributes().get("id");
            email = (kakaoId != null ? String.valueOf(kakaoId) : "unknown") + "@kakao.com";
        }
        return email;
    }

    /**
     * OAuth2User에서 Member 엔티티를 조회합니다.
     */
    public Optional<Member> findMember(OAuth2User oauthUser) {
        String email = extractEmail(oauthUser);
        if (email == null) return Optional.empty();
        return memberRepository.findByEmail(email);
    }
}
