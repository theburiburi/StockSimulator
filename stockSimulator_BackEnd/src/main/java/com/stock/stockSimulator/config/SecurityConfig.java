package com.stock.stockSimulator.config;

import com.stock.stockSimulator.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
    private final CustomOAuth2UserService oauthService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2UserService oAuth2UserService) throws  Exception{
        return http.csrf(c -> c.disable()).headers(h -> h.frameOptions(f -> f.disable()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/", "/index.html", "/*.css", "/*.js", "/favicon.ico", "/api/auth/**", "/api/stocks/**", "/ws-stock/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(o -> o.userInfoEndpoint(u -> u.userService(oAuth2UserService))).build();
    }
}
