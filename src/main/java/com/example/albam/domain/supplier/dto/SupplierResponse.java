package com.example.albam.domain.supplier.dto;

import com.example.albam.domain.supplier.entity.Supplier;

public record SupplierResponse(
        Long id,
        String name,
        String category,
        String siteUrl,
        String phone,
        String memo,
        int displayOrder
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getCategory(),
                supplier.getSiteUrl(), supplier.getPhone(), supplier.getMemo(), supplier.getDisplayOrder());
    }
}
