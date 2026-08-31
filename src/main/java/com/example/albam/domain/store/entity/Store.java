package com.example.albam.domain.store.entity;

import com.example.albam.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "stores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 소프트 삭제된(deleted_at이 채워진) 매장은 일반 조회에서 항상 제외한다. 조회하는 쪽마다 조건을
// 붙이면 하나라도 빠뜨렸을 때 지운 매장이 다시 노출되므로, 엔티티 레벨에서 한 번에 막는다.
// 유예기간 정리 배치(StorePurgeService)는 이 필터를 우회해야 하므로 네이티브 쿼리를 쓴다.
@SQLRestriction("deleted_at IS NULL")
public class Store extends BaseTimeEntity {

    /** 단계 이름은 화면이 정하므로 값 자체는 검사하지 않고, 길이만 제한해 둔다. */
    public static final int ONBOARDING_STEP_KEY_MAX_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    private StoreCategory category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "store_business_hours", joinColumns = @JoinColumn(name = "store_id"))
    @MapKeyColumn(name = "day_of_week")
    @MapKeyEnumerated(EnumType.STRING)
    private Map<DayOfWeek, BusinessHour> businessHours = new HashMap<>();

    @Column(nullable = false, unique = true, length = 6)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakPolicy breakPolicy;

    /** 상시 근로자 5인 미만 사업장 여부. 5인 미만은 연장·야간·휴일 가산수당과 주 52시간 상한이 적용되지 않는다. */
    @Column(nullable = false)
    private boolean smallBusiness;

    /**
     * 급여 지급일(매월 며칠). 미지정이면 null.
     *
     * <p>알바생에게 "며칠에 받는지" 보여주기 위한 값일 뿐, 앱이 지급을 처리하지는 않는다. 그래서
     * 31일로 둔 매장의 2월을 며칠로 볼지 같은 규칙은 두지 않는다 — 실제 지급은 사장이 알아서 한다.
     */
    private Integer payday;

    /**
     * 사장님이 실제로 보고 확인한 온보딩 단계들.
     *
     * <p>"데이터가 있으면 완료"로만 판단하면, 매장을 만들 때 업종 프리셋으로 채워 넣은 영업시간·
     * 체크리스트 때문에 아무것도 안 한 매장이 이미 절반 넘게 끝난 것처럼 보인다. 그 내용이 이 매장에
     * 맞는지는 아직 아무도 안 봤는데도 그렇다. 그래서 데이터 유무와 별개로 확인 여부를 따로 남긴다.
     *
     * <p>단계 이름을 enum이 아니라 문자열로 두는 이유는 프리셋과 같다 — 어떤 단계를 보여줄지는
     * 화면의 사정이라, 단계를 하나 넣고 빼는 데 서버 배포가 필요하지 않게 한다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "store_onboarding_steps", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "step_key", nullable = false, length = ONBOARDING_STEP_KEY_MAX_LENGTH)
    private Set<String> confirmedOnboardingSteps = new HashSet<>();

    /** 사장님이 온보딩을 마쳤다고 표시한 시각. 아직이면 null. */
    private LocalDateTime onboardingCompletedAt;

    /** 매장 대표 사진의 S3 key (전체 URL이 아님). 공개 URL은 응답을 만들 때 조립한다. */
    private String profileImageKey;

    /**
     * 요금제. 지금은 모든 매장이 BASIC이고 이 값을 보는 기능이 아직 없다.
     *
     * <p>생성자로 받지 않고 기본값으로 두는 이유는, 매장을 만드는 쪽이 정할 값이 아니기 때문이다.
     * 같은 이유로 CreateStoreRequest·UpdateStoreRequest에도 넣지 않았다 — 요청 본문으로 등급을
     * 올릴 수 있으면 결제를 건너뛰는 길이 된다. 나중에 결제가 붙을 때 그쪽 경로에서만 바뀐다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorePlan plan = StorePlan.BASIC;

    /**
     * 삭제 표시 시각. null이 아니면 소프트 삭제된 것이고, {@code @SQLRestriction} 때문에 일반 조회에
     * 걸리지 않는다. 실제 행은 유예기간이 지난 뒤 {@code StorePurgeService}가 지운다.
     */
    private LocalDateTime deletedAt;

    public Store(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, String inviteCode, BreakPolicy breakPolicy,
            Boolean smallBusiness, Integer payday) {
        this.name = name;
        this.address = address;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.category = category;
        if (businessHours != null) {
            this.businessHours = businessHours;
        }
        this.inviteCode = inviteCode;
        this.breakPolicy = breakPolicy == null ? BreakPolicy.STATUTORY : breakPolicy;
        this.smallBusiness = Boolean.TRUE.equals(smallBusiness);
        this.payday = payday;
    }

    /**
     * 단계 하나를 확인 상태로 표시한다. 이미 표시되어 있으면 아무 일도 하지 않으므로, 같은 요청이
     * 여러 번 들어와도 결과가 같다.
     */
    public void confirmOnboardingStep(String stepKey) {
        this.confirmedOnboardingSteps.add(stepKey);
    }

    /** 온보딩을 마친 것으로 표시한다. 이미 마쳤다면 처음 끝낸 시각을 유지한다. */
    public void completeOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = LocalDateTime.now();
        }
    }

    public void changeProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    /** 삭제 표시. 이미 표시되어 있다면 최초 시각을 유지해 반복 호출로 유예기간이 늘어나지 않게 한다. */
    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public void update(String name, String address, String businessRegistrationNumber, StoreCategory category,
            Map<DayOfWeek, BusinessHour> businessHours, BreakPolicy breakPolicy, Boolean smallBusiness,
            Integer payday) {
        this.name = name;
        this.address = address;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.category = category;
        this.businessHours.clear();
        if (businessHours != null) {
            this.businessHours.putAll(businessHours);
        }
        this.breakPolicy = breakPolicy == null ? BreakPolicy.STATUTORY : breakPolicy;
        this.smallBusiness = Boolean.TRUE.equals(smallBusiness);
        this.payday = payday;
    }
}