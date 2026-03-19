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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request){
        OAuth2User user = super.loadUser(request);
        
        Map<String, Object> kakaoAccount = user.getAttribute("kakao_account");
        String email = kakaoAccount != null && kakaoAccount.containsKey("email") ? (String) kakaoAccount.get("email") : user.getAttribute("email");
        
        Map<String, Object> profile = kakaoAccount != null && kakaoAccount.containsKey("profile") ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        String name = profile != null && profile.containsKey("nickname") ? (String) profile.get("nickname") : user.getAttribute("name");
        
        Object kakaoIdObj = user.getAttributes().get("id");
        String kakaoIdString = kakaoIdObj != null ? String.valueOf(kakaoIdObj) : "unknown";
        
        if (email == null) email = kakaoIdString + "@kakao.com";
        if (name == null) name = "카카오유저_" + kakaoIdString.substring(0, Math.min(4, kakaoIdString.length()));
        
        final String finalEmail = email;
        final String finalName = name;

        Member member = memberRepository.findByEmail(finalEmail).orElseGet(() -> {
            boolean hasAdmin = memberRepository.findAll().stream().anyMatch(m -> m.getRole() == Role.ADMIN);
            Role newRole = hasAdmin ? Role.USER : Role.ADMIN;
            long balance = newRole == Role.ADMIN ? 1_000_000_000_000L : 100_000_000L;
            
            Member newMember = new Member();
            newMember.setName(finalName);
            newMember.setEmail(finalEmail);
            newMember.setRole(newRole);
            newMember.setBalance(balance);
            return memberRepository.save(newMember);
        });

        String roleAuth = member.getRole() == Role.ADMIN ? "ROLE_ADMIN" : "ROLE_USER";
        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority(roleAuth)), user.getAttributes(), "id");
    }
}
