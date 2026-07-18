package com.example.albam.domain.menu.repository;

import com.example.albam.domain.menu.entity.MenuRecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRecipeItemRepository extends JpaRepository<MenuRecipeItem, Long> {

    boolean existsByIngredientId(Long ingredientId);

    void deleteByIngredientId(Long ingredientId);
}
