package com.example.albam.domain.user.dto;

import com.example.albam.domain.user.entity.AuthProvider;
import com.example.albam.domain.user.entity.User;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDate birthDate,
        String profileImageUrl,
        boolean profileCompleted,
        boolean emailVerified,
        /**
         * 가입 방식. LOCAL만 비밀번호가 있으므로, 비밀번호 변경 메뉴를 보여줄지 판단하는 데 쓴다.
         * 소셜 계정에 그 메뉴를 띄우면 눌러봐야 거절만 돌아온다.
         */
        AuthProvider provider
) {
    /** profileImageUrl은 저장된 key로부터 조립해 넘겨받는다 (엔티티에는 key만 있다). */
    public static UserResponse from(User user, String profileImageUrl) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getBirthDate(), profileImageUrl, user.isProfileCompleted(), user.isEmailVerified(),
                user.getProvider());
    }
}
