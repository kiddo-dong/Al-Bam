package com.example.albam.domain.store.dto;

import com.example.albam.domain.store.entity.BusinessHour;
import java.time.LocalTime;

public record BusinessHourResponse(LocalTime openTime, LocalTime closeTime, boolean closed) {

    public static BusinessHourResponse from(BusinessHour businessHour) {
        return new BusinessHourResponse(businessHour.getOpenTime(), businessHour.getCloseTime(),
                businessHour.isClosed());
    }
}
