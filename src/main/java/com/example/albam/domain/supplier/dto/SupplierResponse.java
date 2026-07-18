package com.example.albam.domain.supplier.dto;

import com.example.albam.domain.supplier.entity.Supplier;
import java.util.List;

public record SupplierResponse(
        Long id,
        String name,
        String category,
        String siteUrl,
        String phone,
        String memo,
        int displayOrder,
        List<SupplierItemResponse> items
) {
    public static SupplierResponse from(Supplier supplier, List<SupplierItemResponse> items) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getCategory(),
                supplier.getSiteUrl(), supplier.getPhone(), supplier.getMemo(), supplier.getDisplayOrder(),
                items);
    }
}
