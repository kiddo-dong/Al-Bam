package com.example.albam.global.security;

import com.example.albam.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getUserId() {
        return user.getId();
    }

    /**
     * 인증 필터가 매 요청마다 이 객체를 만들면서 User를 이미 읽어오므로, 가입 완료 여부를 여기서
     * 꺼내 쓰면 추가 조회 없이 확인할 수 있다.
     */
    public boolean isProfileCompleted() {
        return user.isProfileCompleted();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
}
