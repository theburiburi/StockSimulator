package com.stock.stockSimulator.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentUser 어노테이션이 붙은 파라미터에 CurrentUserInfo를 주입
 * JWT 쿠키 또는 Authorization 헤더에서 토큰을 파싱하여 userId, email, role을 추출
 * WebMvcConfig에 빈으로 등록됨
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(CurrentUserInfo.class);
    }

    @Override
    public CurrentUserInfo resolveArgument(MethodParameter parameter,
                                           ModelAndViewContainer mavContainer,
                                           NativeWebRequest webRequest,
                                           WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) return null;

        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) return null;

        return new CurrentUserInfo(
                jwtTokenProvider.getUserId(token),
                jwtTokenProvider.getEmail(token),
                jwtTokenProvider.getRole(token)
        );
    }

    private String resolveToken(HttpServletRequest request) {
        // 1) Authorization: Bearer <token>
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 2) HttpOnly 쿠키 "jwt"
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
