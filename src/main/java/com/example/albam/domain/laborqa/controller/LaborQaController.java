package com.example.albam.domain.laborqa.controller;

import com.example.albam.domain.laborqa.dto.LaborQaRequest;
import com.example.albam.domain.laborqa.dto.LaborQaResponse;
import com.example.albam.domain.laborqa.service.LaborQaService;
import com.example.albam.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 근로기준법·세무 Q&A. 매장과 무관하게 로그인한 사용자라면 누구나(STAFF 포함) 사용할 수 있다. */
@RestController
@RequestMapping("/api/v1/labor-qa")
@RequiredArgsConstructor
public class LaborQaController {

    private final LaborQaService laborQaService;

    @PostMapping("/ask")
    public ApiResponse<LaborQaResponse> ask(@Valid @RequestBody LaborQaRequest request) {
        return ApiResponse.success(laborQaService.ask(request));
    }
}
