package com.example.albam.domain.supplier.service;

import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.supplier.dto.SupplierItemRequest;
import com.example.albam.domain.supplier.dto.SupplierItemResponse;
import com.example.albam.domain.supplier.dto.SupplierRequest;
import com.example.albam.domain.supplier.dto.SupplierResponse;
import com.example.albam.domain.supplier.entity.Supplier;
import com.example.albam.domain.supplier.entity.SupplierItem;
import com.example.albam.domain.supplier.repository.SupplierItemRepository;
import com.example.albam.domain.supplier.repository.SupplierRepository;
import com.example.albam.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierItemRepository supplierItemRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public SupplierResponse createSupplier(Long storeId, Long userId, SupplierRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = supplierRepository.save(new Supplier(manager.getStore(), request.name(),
                request.category(), request.siteUrl(), request.phone(), request.memo(),
                request.displayOrderOrDefault()));
        return SupplierResponse.from(supplier, List.of());
    }

    /** 거래처 목록 + 각 거래처의 발주 품목(요일별 수량 포함) — OWNER/MANAGER 전용. */
    public List<SupplierResponse> getSuppliers(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return supplierRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId).stream()
                .map(supplier -> SupplierResponse.from(supplier, getItemResponses(supplier.getId())))
                .toList();
    }

    @Transactional
    public SupplierResponse updateSupplier(Long storeId, Long supplierId, Long userId,
            SupplierRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        supplier.update(request.name(), request.category(), request.siteUrl(), request.phone(),
                request.memo(), request.displayOrderOrDefault());
        return SupplierResponse.from(supplier, getItemResponses(supplier.getId()));
    }

    @Transactional
    public void deleteSupplier(Long storeId, Long supplierId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        supplierItemRepository.deleteBySupplierId(supplier.getId());
        supplierRepository.delete(supplier);
    }

    @Transactional
    public SupplierItemResponse addItem(Long storeId, Long supplierId, Long userId,
            SupplierItemRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        SupplierItem item = supplierItemRepository.save(new SupplierItem(supplier, request.name(),
                request.spec(), request.memo(), request.displayOrderOrDefault(),
                request.weeklyQuantities()));
        return SupplierItemResponse.from(item);
    }

    @Transactional
    public SupplierItemResponse updateItem(Long storeId, Long itemId, Long userId,
            SupplierItemRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        SupplierItem item = getItemInStore(storeId, itemId);
        item.update(request.name(), request.spec(), request.memo(), request.displayOrderOrDefault(),
                request.weeklyQuantities());
        return SupplierItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(Long storeId, Long itemId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        supplierItemRepository.delete(getItemInStore(storeId, itemId));
    }

    private List<SupplierItemResponse> getItemResponses(Long supplierId) {
        return supplierItemRepository.findAllBySupplierIdOrderByDisplayOrderAscIdAsc(supplierId).stream()
                .map(SupplierItemResponse::from)
                .toList();
    }

    private Supplier getSupplierInStore(Long storeId, Long supplierId) {
        return supplierRepository.findByIdAndStoreId(supplierId, storeId)
                .orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
    }

    private SupplierItem getItemInStore(Long storeId, Long itemId) {
        return supplierItemRepository.findByIdAndSupplierStoreId(itemId, storeId)
                .orElseThrow(() -> new NotFoundException("발주 품목을 찾을 수 없습니다."));
    }
}
