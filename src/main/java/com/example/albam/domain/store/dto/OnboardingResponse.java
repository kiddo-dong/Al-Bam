package com.example.albam.domain.store.dto;

import com.example.albam.domain.store.entity.Store;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 매장 온보딩 진행 상태.
 *
 * <p>어떤 단계가 있는지는 화면이 정하므로 여기서는 목록을 내려주지 않는다. 사장님이 확인했다고
 * 표시한 단계와, 온보딩을 마쳤는지만 담는다.
 */
public record OnboardingResponse(
        /** 사장님이 확인한 단계 이름들. 순서는 의미 없다. */
        List<String> confirmedSteps,
        /** 온보딩을 마쳤다고 표시한 시각. 아직이면 null. */
        LocalDateTime completedAt,
        boolean completed
) {
    public static OnboardingResponse from(Store store) {
        Set<String> confirmed = store.getConfirmedOnboardingSteps();
        return new OnboardingResponse(List.copyOf(confirmed), store.getOnboardingCompletedAt(),
                store.getOnboardingCompletedAt() != null);
    }
}
