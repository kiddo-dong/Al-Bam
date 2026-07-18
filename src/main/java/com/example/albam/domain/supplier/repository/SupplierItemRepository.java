package com.example.albam.domain.supplier.repository;

import com.example.albam.domain.supplier.entity.SupplierItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierItemRepository extends JpaRepository<SupplierItem, Long> {

    List<SupplierItem> findAllBySupplierIdOrderByDisplayOrderAscIdAsc(Long supplierId);

    Optional<SupplierItem> findByIdAndSupplierStoreId(Long id, Long storeId);

    void deleteBySupplierId(Long supplierId);
}
