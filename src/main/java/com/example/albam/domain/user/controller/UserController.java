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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
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

    @PostMapping("/profile-image")
    public ApiResponse<UserResponse> updateProfileImage(@CurrentUserId Long userId,
            @RequestParam("image") MultipartFile image) {
        return ApiResponse.success(userService.updateProfileImage(userId, image));
    }

    @DeleteMapping("/profile-image")
    public ApiResponse<UserResponse> deleteProfileImage(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.deleteProfileImage(userId));
    }
}
