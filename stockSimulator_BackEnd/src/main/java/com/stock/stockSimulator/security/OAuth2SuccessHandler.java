package com.stock.stockSimulator.security;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 JWT 토큰을 발급하고 쿠키에 담아 프론트로 리다이렉트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthHelper oAuthHelper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        Member member = oAuthHelper.findMember(oauthUser).orElse(null);

        if (member == null) {
            log.warn("OAuth2SuccessHandler: Member not found after OAuth2 login");
            response.sendRedirect("/");
            return;
        }

        String role = "ROLE_" + member.getRole().name();  // "ROLE_USER" or "ROLE_ADMIN"
        String token = jwtTokenProvider.generateToken(member.getEmail(), role, member.getId());

        log.info("JWT issued for user: {}, role: {}, userId: {}", member.getEmail(), role, member.getId());

        // HttpOnly 쿠키에 JWT 저장
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24시간
        response.addCookie(cookie);

        // 메인 페이지로 리다이렉트
        response.sendRedirect("/");
    }
}
