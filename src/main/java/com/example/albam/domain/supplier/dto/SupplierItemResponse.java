package com.example.albam.domain.supplier.dto;

import com.example.albam.domain.supplier.entity.SupplierItem;
import java.time.DayOfWeek;
import java.util.Map;

public record SupplierItemResponse(
        Long id,
        String name,
        String spec,
        String memo,
        int displayOrder,
        Map<DayOfWeek, String> weeklyQuantities
) {
    public static SupplierItemResponse from(SupplierItem item) {
        return new SupplierItemResponse(item.getId(), item.getName(), item.getSpec(), item.getMemo(),
                item.getDisplayOrder(), Map.copyOf(item.getWeeklyQuantities()));
    }
}
