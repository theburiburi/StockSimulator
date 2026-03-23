package com.stock.stockSimulator.security;

import java.lang.annotation.*;

/**
 * 컨트롤러 메서드 파라미터에 붙이면
 * CurrentUserArgumentResolver가 JWT에서 CurrentUserInfo를 꺼내 주입
 *
 * 사용 예:
 * public ResponseEntity<?> myApi(@CurrentUser CurrentUserInfo user) { ... }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
