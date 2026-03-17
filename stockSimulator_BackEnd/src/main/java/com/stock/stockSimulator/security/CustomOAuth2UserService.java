package com.stock.stockSimulator.security;

import com.stock.stockSimulator.domain.Member;
import com.stock.stockSimulator.domain.Role;
import com.stock.stockSimulator.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request){
        OAuth2User user = super.loadUser(request);
        String email = user.getAttribute("email");
        Member member = memberRepository.findByEmail(email).orElseGet(() ->
                memberRepository.save(Member.builder()
                        .name(user.getAttribute("name"))
                        .email(email)
                        .role(Role.USER)
                        .build()));

        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")), user.getAttributes(), "id");
    }
}
