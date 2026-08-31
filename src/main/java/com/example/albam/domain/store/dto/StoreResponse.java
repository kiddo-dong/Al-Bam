package com.example.albam.domain.store.dto;

import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.entity.StoreCategory;
import com.example.albam.domain.store.entity.StorePlan;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.stream.Collectors;

public record StoreResponse(
        Long id,
        String name,
        String address,
        String businessRegistrationNumber,
        StoreCategory category,
        Map<DayOfWeek, BusinessHourResponse> businessHours,
        BreakPolicy breakPolicy,
        boolean smallBusiness,
        /** 요금제. 아직 어떤 기능도 이 값에 따라 달라지지 않는다. */
        StorePlan plan,
        /** 급여 지급일(매월 며칠). 미지정이면 null. */
        Integer payday
) {
    public static StoreResponse from(Store store) {
        Map<DayOfWeek, BusinessHourResponse> businessHours = store.getBusinessHours().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> BusinessHourResponse.from(entry.getValue())));
        return new StoreResponse(store.getId(), store.getName(), store.getAddress(),
                store.getBusinessRegistrationNumber(), store.getCategory(), businessHours,
                store.getBreakPolicy(), store.isSmallBusiness(), store.getPlan(), store.getPayday());
    }
}
