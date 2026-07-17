package com.example.albam.domain.shift.repository;

import com.example.albam.domain.shift.entity.ShiftTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {

    List<ShiftTemplate> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);

    Optional<ShiftTemplate> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndName(Long storeId, String name);

    boolean existsByStoreIdAndNameAndIdNot(Long storeId, String name, Long id);
}
