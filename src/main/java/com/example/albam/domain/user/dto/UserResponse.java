package com.example.albam.domain.user.dto;

import com.example.albam.domain.user.entity.User;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDate birthDate,
        String profileImageUrl,
        boolean profileCompleted
) {
    /** profileImageUrl은 저장된 key로부터 조립해 넘겨받는다 (엔티티에는 key만 있다). */
    public static UserResponse from(User user, String profileImageUrl) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getBirthDate(), profileImageUrl, user.isProfileCompleted());
    }
}
