package com.stock.stockSimulator.config;

import com.stock.stockSimulator.security.CustomOAuth2UserService;
import com.stock.stockSimulator.security.JwtAuthFilter;
import com.stock.stockSimulator.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService oauthService;
    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .headers(h -> h.frameOptions(f -> f.disable()))
                // Stateless: 세션 사용 안 함
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/", "/index.html", "/stock.html",
                                "/*.css", "/*.js", "/favicon.ico",
                                "/api/auth/**", "/api/stocks/**",
                                "/ws-stock/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인 성공 시 JWT 발급
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.userService(oauthService))
                        .successHandler(oAuth2SuccessHandler)
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
