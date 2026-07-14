package com.example.albam.domain.payroll.controller;

import com.example.albam.domain.payroll.dto.DashboardResponse;
import com.example.albam.domain.payroll.service.StoreDashboardService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final StoreDashboardService storeDashboardService;

    /** 사장님용 월간 대시보드: 인건비 합계, 멤버별 비용, 근태 준수 현황 (OWNER/MANAGER). */
    @GetMapping("/api/v1/stores/{storeId}/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(@PathVariable Long storeId,
            @CurrentUserId Long userId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(storeDashboardService.getDashboard(storeId, userId, year, month));
    }
}
