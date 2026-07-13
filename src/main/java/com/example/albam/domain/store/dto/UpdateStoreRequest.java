package com.example.albam.domain.store.dto;

import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.StoreCategory;
import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.util.Map;

public record UpdateStoreRequest(
        @NotBlank String name,
        String address,
        String businessRegistrationNumber,
        StoreCategory category,
        Map<DayOfWeek, BusinessHourRequest> businessHours,
        BreakPolicy breakPolicy,
        Boolean smallBusiness
) {
}
