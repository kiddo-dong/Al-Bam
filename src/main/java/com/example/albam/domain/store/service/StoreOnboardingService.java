package com.example.albam.domain.store.service;

import com.example.albam.domain.store.dto.OnboardingResponse;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 개설 직후의 준비 과정을 어디까지 했는지 기록한다.
 *
 * <p>진행 상태를 데이터 유무로만 판단하면, 업종 프리셋이 미리 채워 넣은 영업시간과 체크리스트 때문에
 * 손대지 않은 매장이 이미 상당 부분 끝난 것처럼 보인다. 그래서 "값이 있다"와 "사장님이 봤다"를
 * 따로 둔다.
 *
 * <p>온보딩은 매장을 세우는 일이라 OWNER만 다룬다 — 화면에서도 사장에게만 보인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreOnboardingService {

    private final StoreRepository storeRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    public OnboardingResponse getOnboarding(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        return OnboardingResponse.from(getStore(storeId));
    }

    /** 단계 하나를 확인 처리한다. 같은 단계를 다시 보내도 결과가 같다. */
    @Transactional
    public OnboardingResponse confirmStep(Long storeId, Long userId, String stepKey) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStore(storeId);
        store.confirmOnboardingStep(validateStepKey(stepKey));
        return OnboardingResponse.from(store);
    }

    /** 온보딩을 마친 것으로 표시한다. 이미 마쳤다면 처음 끝낸 시각이 유지된다. */
    @Transactional
    public OnboardingResponse complete(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        Store store = getStore(storeId);
        store.completeOnboarding();
        return OnboardingResponse.from(store);
    }

    /**
     * 단계 이름의 값 자체는 화면의 사정이라 목록으로 검사하지 않는다. 다만 경로로 들어오는 값이므로
     * 컬럼에 들어갈 수 있는 길이인지, 빈 값이 아닌지는 확인한다.
     */
    private String validateStepKey(String stepKey) {
        if (stepKey == null || stepKey.isBlank()) {
            throw new InvalidRequestException("단계 이름이 비어 있습니다.");
        }
        if (stepKey.length() > Store.ONBOARDING_STEP_KEY_MAX_LENGTH) {
            throw new InvalidRequestException(
                    "단계 이름은 " + Store.ONBOARDING_STEP_KEY_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return stepKey;
    }

    private Store getStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다."));
    }
}
