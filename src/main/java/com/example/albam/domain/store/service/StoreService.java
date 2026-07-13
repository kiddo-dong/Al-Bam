package com.example.albam.domain.store.service;

import com.example.albam.domain.store.dto.BusinessHourRequest;
import com.example.albam.domain.store.dto.CreateStoreRequest;
import com.example.albam.domain.store.dto.InviteCodeResponse;
import com.example.albam.domain.store.dto.StoreResponse;
import com.example.albam.domain.store.dto.UpdateStoreRequest;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.NotFoundException;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private static final int OWNER_DEFAULT_WAGE = 0;
    private static final int INVITE_CODE_LENGTH = 6;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final UserRepository userRepository;
    private final StoreAuthorizationService storeAuthorizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public StoreResponse createStore(Long userId, CreateStoreRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        Store store = storeRepository.save(
                new Store(request.name(), request.address(), request.businessRegistrationNumber(),
                        request.category(), toBusinessHours(request.businessHours()), generateUniqueInviteCode()));
        storeMemberRepository.save(new StoreMember(store, user, MemberRole.OWNER, OWNER_DEFAULT_WAGE));
        return StoreResponse.from(store);
    }

    public InviteCodeResponse getInviteCode(Long storeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return new InviteCodeResponse(getStoreEntity(storeId).getInviteCode());
    }

    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        store.changeInviteCode(generateUniqueInviteCode());
        return new InviteCodeResponse(store.getInviteCode());
    }

    public List<StoreResponse> getMyStores(Long userId) {
        return storeMemberRepository.findAllByUserId(userId).stream()
                .map(StoreMember::getStore)
                .map(StoreResponse::from)
                .toList();
    }

    public StoreResponse getStore(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return StoreResponse.from(getStoreEntity(storeId));
    }

    @Transactional
    public StoreResponse updateStore(Long storeId, Long userId, UpdateStoreRequest request) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStoreEntity(storeId);
        store.update(request.name(), request.address(), request.businessRegistrationNumber(),
                request.category(), toBusinessHours(request.businessHours()));
        return StoreResponse.from(store);
    }

    @Transactional
    public void deleteStore(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        storeRepository.deleteById(storeId);
    }

    private Store getStoreEntity(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다."));
    }

    private Map<DayOfWeek, BusinessHour> toBusinessHours(Map<DayOfWeek, BusinessHourRequest> businessHours) {
        if (businessHours == null) {
            return Map.of();
        }
        return businessHours.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new BusinessHour(entry.getValue().openTime(), entry.getValue().closeTime(),
                                entry.getValue().closed())));
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (storeRepository.existsByInviteCode(code));
        return code;
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
