package com.example.albam.domain.shift.controller;

import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.shift.dto.UpdateShiftRequest;
import com.example.albam.domain.shift.service.ShiftService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores/{storeId}/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShiftResponse>> createShift(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shiftService.createShift(storeId, userId, request)));
    }

    @GetMapping
    public ApiResponse<List<ShiftResponse>> getShifts(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam(required = false) Long storeMemberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(shiftService.getShifts(storeId, userId, storeMemberId, from, to));
    }

    @PatchMapping("/{shiftId}")
    public ApiResponse<ShiftResponse> updateShift(@PathVariable Long storeId, @PathVariable Long shiftId,
            @CurrentUserId Long userId, @Valid @RequestBody UpdateShiftRequest request) {
        return ApiResponse.success(shiftService.updateShift(storeId, shiftId, userId, request));
    }

    @DeleteMapping("/{shiftId}")
    public ApiResponse<Void> deleteShift(@PathVariable Long storeId, @PathVariable Long shiftId,
            @CurrentUserId Long userId) {
        shiftService.deleteShift(storeId, shiftId, userId);
        return ApiResponse.ok();
    }
}
