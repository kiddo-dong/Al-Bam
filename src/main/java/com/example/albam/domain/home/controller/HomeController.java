package com.example.albam.domain.home.controller;

import com.example.albam.domain.home.dto.HomeResponse;
import com.example.albam.domain.home.service.HomeService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /** 매장 홈 화면. 역할에 따라 공통(myDay) / 관리자 / 오너 섹션이 채워진다. */
    @GetMapping("/api/v1/stores/{storeId}/home")
    public ApiResponse<HomeResponse> getHome(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(homeService.getHome(storeId, userId));
    }
}
