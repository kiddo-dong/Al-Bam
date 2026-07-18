package com.example.albam.domain.menu.service;

import com.example.albam.domain.menu.dto.MenuIngredientRequest;
import com.example.albam.domain.menu.dto.MenuIngredientResponse;
import com.example.albam.domain.menu.dto.MenuRequest;
import com.example.albam.domain.menu.dto.MenuResponse;
import com.example.albam.domain.menu.entity.MenuIngredient;
import com.example.albam.domain.menu.entity.MenuRecipeItem;
import com.example.albam.domain.menu.entity.StoreMenu;
import com.example.albam.domain.menu.repository.MenuIngredientRepository;
import com.example.albam.domain.menu.repository.MenuRecipeItemRepository;
import com.example.albam.domain.menu.repository.StoreMenuRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 원가 계산: 재료 단가와 레시피를 관리하고 재료비·원가율·이익을 계산한다. 원가는 관리자 전용. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuCostService {

    private final MenuIngredientRepository menuIngredientRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final MenuRecipeItemRepository menuRecipeItemRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public MenuIngredientResponse createIngredient(Long storeId, Long userId,
            MenuIngredientRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        MenuIngredient ingredient = menuIngredientRepository.save(new MenuIngredient(manager.getStore(),
                request.name(), request.productInfo(), request.price(), request.packageQty(),
                request.unit(), request.lossRate(), request.category()));
        return MenuIngredientResponse.from(ingredient);
    }

    public List<MenuIngredientResponse> getIngredients(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return menuIngredientRepository.findAllByStoreIdOrderByCategoryAscIdAsc(storeId).stream()
                .map(MenuIngredientResponse::from)
                .toList();
    }

    @Transactional
    public MenuIngredientResponse updateIngredient(Long storeId, Long ingredientId, Long userId,
            MenuIngredientRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        MenuIngredient ingredient = getIngredientInStore(storeId, ingredientId);
        ingredient.update(request.name(), request.productInfo(), request.price(), request.packageQty(),
                request.unit(), request.lossRate(), request.category());
        return MenuIngredientResponse.from(ingredient);
    }

    /** 재료 삭제 시 이 재료를 쓰는 레시피 줄도 함께 제거된다 (계산기와 동일 동작). */
    @Transactional
    public void deleteIngredient(Long storeId, Long ingredientId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        MenuIngredient ingredient = getIngredientInStore(storeId, ingredientId);
        menuRecipeItemRepository.deleteByIngredientId(ingredient.getId());
        menuIngredientRepository.delete(ingredient);
    }

    @Transactional
    public MenuResponse createMenu(Long storeId, Long userId, MenuRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMenu menu = storeMenuRepository.save(new StoreMenu(manager.getStore(), request.name(),
                request.sellingPrice(), request.category()));
        menu.replaceRecipe(buildRecipeItems(storeId, menu, request.recipe()));
        return MenuResponse.from(menu);
    }

    public List<MenuResponse> getMenus(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return storeMenuRepository.findAllByStoreIdOrderByCategoryAscIdAsc(storeId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    public MenuResponse getMenu(Long storeId, Long menuId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return MenuResponse.from(getMenuInStore(storeId, menuId));
    }

    @Transactional
    public MenuResponse updateMenu(Long storeId, Long menuId, Long userId, MenuRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMenu menu = getMenuInStore(storeId, menuId);
        menu.update(request.name(), request.sellingPrice(), request.category());
        menu.replaceRecipe(buildRecipeItems(storeId, menu, request.recipe()));
        return MenuResponse.from(menu);
    }

    @Transactional
    public void deleteMenu(Long storeId, Long menuId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        storeMenuRepository.delete(getMenuInStore(storeId, menuId));
    }

    private List<MenuRecipeItem> buildRecipeItems(Long storeId, StoreMenu menu,
            List<MenuRequest.RecipeItemRequest> recipe) {
        if (recipe == null) {
            return List.of();
        }
        return recipe.stream()
                .map(item -> new MenuRecipeItem(menu, getIngredientInStore(storeId, item.ingredientId()),
                        item.amount()))
                .toList();
    }

    private MenuIngredient getIngredientInStore(Long storeId, Long ingredientId) {
        return menuIngredientRepository.findByIdAndStoreId(ingredientId, storeId)
                .orElseThrow(() -> new NotFoundException("재료를 찾을 수 없습니다: " + ingredientId));
    }

    private StoreMenu getMenuInStore(Long storeId, Long menuId) {
        return storeMenuRepository.findByIdAndStoreId(menuId, storeId)
                .orElseThrow(() -> new NotFoundException("메뉴를 찾을 수 없습니다."));
    }
}
