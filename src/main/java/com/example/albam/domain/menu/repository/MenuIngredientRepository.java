package com.example.albam.domain.menu.repository;

import com.example.albam.domain.menu.entity.MenuIngredient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuIngredientRepository extends JpaRepository<MenuIngredient, Long> {

    List<MenuIngredient> findAllByStoreIdOrderByCategoryAscIdAsc(Long storeId);

    Optional<MenuIngredient> findByIdAndStoreId(Long id, Long storeId);
}
