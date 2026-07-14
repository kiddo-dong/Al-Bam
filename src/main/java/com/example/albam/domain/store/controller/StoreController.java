package com.example.albam.domain.store.controller;

import com.example.albam.domain.store.dto.CreateStoreRequest;
import com.example.albam.domain.store.dto.InviteCodeResponse;
import com.example.albam.domain.store.dto.StoreResponse;
import com.example.albam.domain.store.dto.UpdateStoreRequest;
import com.example.albam.domain.store.service.StoreService;
import com.example.albam.global.common.ApiResponse;
import com.example.albam.global.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(@CurrentUserId Long userId,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeService.createStore(userId, request)));
    }

    @GetMapping
    public ApiResponse<List<StoreResponse>> getMyStores(@CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getMyStores(userId));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StoreResponse> getStore(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getStore(storeId, userId));
    }

    @PatchMapping("/{storeId}")
    public ApiResponse<StoreResponse> updateStore(@PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody UpdateStoreRequest request) {
        return ApiResponse.success(storeService.updateStore(storeId, userId, request));
    }

    @DeleteMapping("/{storeId}")
    public ApiResponse<Void> deleteStore(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam String confirmName) {
        storeService.deleteStore(storeId, userId, confirmName);
        return ApiResponse.ok();
    }

    @GetMapping("/{storeId}/invite-code")
    public ApiResponse<InviteCodeResponse> getInviteCode(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getInviteCode(storeId, userId));
    }

    @PostMapping("/{storeId}/invite-code/regenerate")
    public ApiResponse<InviteCodeResponse> regenerateInviteCode(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.regenerateInviteCode(storeId, userId));
    }
}
