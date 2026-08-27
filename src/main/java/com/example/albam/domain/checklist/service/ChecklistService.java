package com.example.albam.domain.checklist.service;

import com.example.albam.domain.checklist.dto.ChecklistItemBulkRequest;
import com.example.albam.domain.checklist.dto.ChecklistItemRequest;
import com.example.albam.domain.checklist.dto.ChecklistItemResponse;
import com.example.albam.domain.checklist.dto.DailyChecklistEntry;
import com.example.albam.domain.checklist.entity.ChecklistCompletion;
import com.example.albam.domain.checklist.entity.ChecklistItem;
import com.example.albam.domain.checklist.entity.ChecklistType;
import com.example.albam.domain.checklist.repository.ChecklistCompletionRepository;
import com.example.albam.domain.checklist.repository.ChecklistItemRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistCompletionRepository checklistCompletionRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public ChecklistItemResponse addItem(Long storeId, Long userId, ChecklistItemRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        ChecklistItem item = checklistItemRepository.save(new ChecklistItem(manager.getStore(),
                request.type(), request.content(), request.displayOrderOrDefault()));
        return ChecklistItemResponse.from(item);
    }

    /**
     * 여러 항목을 한 번에 등록한다. 업종 프리셋처럼 10개 안팎을 넣을 때 쓰인다.
     *
     * <p>한 트랜잭션이라 하나라도 실패하면 전부 되돌아간다. 단건 등록을 반복하면 앞의 몇 개만 남는
     * 상태가 생기는데, 온보딩에서는 항목이 하나만 있어도 완료로 보여 그 절반의 실패를 알아채기 어렵다.
     *
     * <p>{@code displayOrder}는 요청 배열의 순서로 매긴다. 다만 0부터 다시 매기면 이미 있는 항목과
     * 겹치므로, 그 매장·구분의 현재 최대 순번 뒤에 이어 붙인다.
     */
    @Transactional
    public List<ChecklistItemResponse> addItems(Long storeId, Long userId, ChecklistItemBulkRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);

        Map<ChecklistType, Integer> nextOrder = new EnumMap<>(ChecklistType.class);
        List<ChecklistItem> items = new ArrayList<>();
        for (ChecklistItemRequest item : request.items()) {
            int order = nextOrder.computeIfAbsent(item.type(),
                    type -> checklistItemRepository.findMaxDisplayOrder(storeId, type).orElse(-1) + 1);
            nextOrder.put(item.type(), order + 1);
            items.add(new ChecklistItem(manager.getStore(), item.type(), item.content(), order));
        }
        return checklistItemRepository.saveAll(items).stream()
                .map(ChecklistItemResponse::from)
                .toList();
    }

    public List<ChecklistItemResponse> getItems(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId).stream()
                .map(ChecklistItemResponse::from)
                .toList();
    }

    @Transactional
    public ChecklistItemResponse updateItem(Long storeId, Long itemId, Long userId,
            ChecklistItemRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        item.update(request.type(), request.content(), request.displayOrderOrDefault());
        return ChecklistItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(Long storeId, Long itemId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        checklistCompletionRepository.deleteByItemId(item.getId());
        checklistItemRepository.delete(item);
    }

    /** 특정 날짜의 체크리스트 현황: 항목 + 체크 여부 + 누가 언제 체크했는지. */
    public List<DailyChecklistEntry> getDailyChecklist(Long storeId, Long userId, LocalDate date) {
        storeAuthorizationService.requireMember(storeId, userId);
        Map<Long, ChecklistCompletion> completionByItemId = checklistCompletionRepository
                .findAllByItemStoreIdAndWorkDate(storeId, date).stream()
                .collect(Collectors.toMap(completion -> completion.getItem().getId(),
                        completion -> completion));
        return checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId).stream()
                .map(item -> {
                    ChecklistCompletion completion = completionByItemId.get(item.getId());
                    return new DailyChecklistEntry(item.getId(), item.getType(), item.getContent(),
                            item.getDisplayOrder(), completion != null,
                            completion == null ? null : completion.getCheckedBy().getUser().getName(),
                            completion == null ? null : completion.getCheckedAt());
                })
                .toList();
    }

    /** 항목 체크. 이미 체크된 항목이면 그대로 둔다 (멱등). */
    @Transactional
    public void check(Long storeId, Long itemId, Long userId, LocalDate date) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        if (checklistCompletionRepository.findByItemIdAndWorkDate(item.getId(), date).isEmpty()) {
            checklistCompletionRepository.save(new ChecklistCompletion(item, date, me));
        }
    }

    /** 체크 해제 (실수로 체크한 경우). */
    @Transactional
    public void uncheck(Long storeId, Long itemId, Long userId, LocalDate date) {
        storeAuthorizationService.requireMember(storeId, userId);
        ChecklistItem item = getItemInStore(storeId, itemId);
        checklistCompletionRepository.findByItemIdAndWorkDate(item.getId(), date)
                .ifPresent(checklistCompletionRepository::delete);
    }

    private ChecklistItem getItemInStore(Long storeId, Long itemId) {
        return checklistItemRepository.findByIdAndStoreId(itemId, storeId)
                .orElseThrow(() -> new NotFoundException("체크리스트 항목을 찾을 수 없습니다."));
    }
}
