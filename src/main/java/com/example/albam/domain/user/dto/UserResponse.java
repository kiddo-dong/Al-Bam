package com.example.albam.domain.user.dto;

import com.example.albam.domain.user.entity.User;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDate birthDate,
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getBirthDate(), user.getProfileImageUrl());
    }
}
