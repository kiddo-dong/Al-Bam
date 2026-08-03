package com.example.albam.domain.menu.repository;

import com.example.albam.domain.menu.entity.StoreMenu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMenuRepository extends JpaRepository<StoreMenu, Long> {

    @EntityGraph(attributePaths = {"recipeItems", "recipeItems.ingredient"})
    List<StoreMenu> findAllByStoreIdOrderByCategoryAscIdAsc(Long storeId);

    @EntityGraph(attributePaths = {"recipeItems", "recipeItems.ingredient"})
    Optional<StoreMenu> findByIdAndStoreId(Long id, Long storeId);
}
