package com.stock.stockSimulator.security;

/**
 * JWT 토큰에서 추출한 인증된 사용자 정보
 * @CurrentUser 어노테이션을 통해 컨트롤러에서 주입받음
 */
public record CurrentUserInfo(
        Long userId,
        String email,
        String role
) {}
