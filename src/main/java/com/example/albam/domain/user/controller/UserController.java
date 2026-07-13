package com.example.albam.domain.user.controller;

import com.example.albam.domain.user.dto.UpdateUserRequest;
import com.example.albam.domain.user.dto.UserResponse;
import com.example.albam.domain.user.service.UserService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<UserResponse> getMe(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@CurrentUserId Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateMe(userId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@CurrentUserId Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok();
    }
}
