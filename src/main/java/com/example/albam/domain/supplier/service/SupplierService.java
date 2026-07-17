package com.example.albam.domain.supplier.service;

import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.supplier.dto.SupplierRequest;
import com.example.albam.domain.supplier.dto.SupplierResponse;
import com.example.albam.domain.supplier.entity.Supplier;
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
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public SupplierResponse createSupplier(Long storeId, Long userId, SupplierRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = supplierRepository.save(new Supplier(manager.getStore(), request.name(),
                request.category(), request.siteUrl(), request.phone(), request.memo(),
                request.displayOrderOrDefault()));
        return SupplierResponse.from(supplier);
    }

    public List<SupplierResponse> getSuppliers(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return supplierRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId).stream()
                .map(SupplierResponse::from)
                .toList();
    }

    @Transactional
    public SupplierResponse updateSupplier(Long storeId, Long supplierId, Long userId,
            SupplierRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Supplier supplier = getSupplierInStore(storeId, supplierId);
        supplier.update(request.name(), request.category(), request.siteUrl(), request.phone(),
                request.memo(), request.displayOrderOrDefault());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void deleteSupplier(Long storeId, Long supplierId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        supplierRepository.delete(getSupplierInStore(storeId, supplierId));
    }

    private Supplier getSupplierInStore(Long storeId, Long supplierId) {
        return supplierRepository.findByIdAndStoreId(supplierId, storeId)
                .orElseThrow(() -> new NotFoundException("거래처를 찾을 수 없습니다."));
    }
}
