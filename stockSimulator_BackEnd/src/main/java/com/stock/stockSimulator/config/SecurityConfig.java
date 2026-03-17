package com.stock.stockSimulator.config;

import com.stock.stockSimulator.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomOAuth2UserService oauthService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws  Exception{
        return http.csrf(c -> c.disable())
    }
}
