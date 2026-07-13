package com.example.albam.domain.payroll.controller;

import com.example.albam.domain.payroll.dto.PayrollResponse;
import com.example.albam.domain.payroll.service.PayrollService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores/{storeId}/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping
    public ApiResponse<PayrollResponse> getPayroll(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam Long memberId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(payrollService.getPayroll(storeId, memberId, userId, year, month));
    }
}
