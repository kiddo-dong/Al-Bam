package com.example.albam.domain.supplier.repository;

import com.example.albam.domain.supplier.entity.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(Long storeId);

    Optional<Supplier> findByIdAndStoreId(Long id, Long storeId);
}
