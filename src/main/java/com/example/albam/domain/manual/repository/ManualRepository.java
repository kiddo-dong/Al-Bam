package com.example.albam.domain.manual.repository;

import com.example.albam.domain.manual.entity.Manual;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualRepository extends JpaRepository<Manual, Long> {

    List<Manual> findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(Long storeId);

    Optional<Manual> findByIdAndStoreId(Long id, Long storeId);
}
