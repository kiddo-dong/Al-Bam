package com.example.albam.domain.store.dto;

import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.entity.StoreCategory;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.stream.Collectors;

public record StoreResponse(
        Long id,
        String name,
        String address,
        String businessRegistrationNumber,
        StoreCategory category,
        Map<DayOfWeek, BusinessHourResponse> businessHours
) {
    public static StoreResponse from(Store store) {
        Map<DayOfWeek, BusinessHourResponse> businessHours = store.getBusinessHours().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> BusinessHourResponse.from(entry.getValue())));
        return new StoreResponse(store.getId(), store.getName(), store.getAddress(),
                store.getBusinessRegistrationNumber(), store.getCategory(), businessHours);
    }
}
