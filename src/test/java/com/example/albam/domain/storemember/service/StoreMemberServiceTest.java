package com.example.albam.domain.storemember.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.albam.domain.storemember.dto.UpdateStoreMemberRequest;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.entity.TaxMode;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreMemberServiceTest {

    @Mock
    private StoreMemberRepository storeMemberRepository;
    @Mock
    private StoreAuthorizationService storeAuthorizationService;

    @InjectMocks
    private StoreMemberService storeMemberService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long TARGET_MEMBER_ID = 20L;

    private StoreMember manager;
    private StoreMember staffTarget;

    @BeforeEach
    void setUp() {
        Store store = new Store("테스트 매장", "서울", null, null, new HashMap<>(), "ABC123",
                BreakPolicy.STATUTORY, false);
        ReflectionTestUtils.setField(store, "id", STORE_ID);

        User managerUser = new User("manager@albam.dev", "pw", "매니저", "010-0000-0000",
                LocalDate.of(1990, 1, 1), null);
        manager = new StoreMember(store, managerUser, MemberRole.MANAGER, 12_000);
        ReflectionTestUtils.setField(manager, "id", 1L);

        User staffUser = new User("staff@albam.dev", "pw", "직원", "010-1111-1111",
                LocalDate.of(1995, 1, 1), null);
        staffTarget = new StoreMember(store, staffUser, MemberRole.STAFF, 11_000);
        ReflectionTestUtils.setField(staffTarget, "id", TARGET_MEMBER_ID);

        lenient().when(storeAuthorizationService.requireOwnerOrManager(anyLong(), anyLong())).thenReturn(manager);
        lenient().when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(staffTarget));
    }

    @Test
    void updateMember_managerCannotPromoteTargetToOwner() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                MemberRole.OWNER, null, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("소유권 이전");

        assertThat(staffTarget.getRole()).isEqualTo(MemberRole.STAFF); // 역할이 실제로 바뀌지 않았어야 한다
    }

    @Test
    void updateMember_cannotModifyExistingOwnerThroughThisApi() {
        StoreMember ownerTarget = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        ReflectionTestUtils.setField(ownerTarget, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(ownerTarget));

        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, 12_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMember_allowsPromotionToManager() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                MemberRole.MANAGER, null, null, null, null, null);

        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request);

        assertThat(staffTarget.getRole()).isEqualTo(MemberRole.MANAGER);
    }

    @Test
    void updateMember_rejectsHourlyWageBelowMinimumWage() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, 5_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최저임금");
    }

    @Test
    void updateMember_appliesMultipleFieldChangesTogether() {
        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, 12_000, MemberStatus.INACTIVE, null, null, TaxMode.WITHHOLDING_3_3);

        storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request);

        assertThat(staffTarget.getHourlyWage()).isEqualTo(12_000);
        assertThat(staffTarget.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(staffTarget.getTaxMode()).isEqualTo(TaxMode.WITHHOLDING_3_3);
    }

    @Test
    void removeMember_ownerCannotBeRemoved() {
        StoreMember ownerTarget = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        ReflectionTestUtils.setField(ownerTarget, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(ownerTarget));

        assertThatThrownBy(() -> storeMemberService.removeMember(STORE_ID, TARGET_MEMBER_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMember_marksTargetInactive() {
        storeMemberService.removeMember(STORE_ID, TARGET_MEMBER_ID, USER_ID);

        assertThat(staffTarget.getStatus()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    void leaveStore_ownerCannotLeave() {
        StoreMember ownerSelf = new StoreMember(staffTarget.getStore(), staffTarget.getUser(), MemberRole.OWNER,
                15_000);
        when(storeAuthorizationService.requireMember(STORE_ID, USER_ID)).thenReturn(ownerSelf);

        assertThatThrownBy(() -> storeMemberService.leaveStore(STORE_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getStoreMember_throwsWhenMemberBelongsToDifferentStore() {
        Store otherStore = new Store("다른 매장", "부산", null, null, new HashMap<>(), "XYZ999",
                BreakPolicy.STATUTORY, false);
        ReflectionTestUtils.setField(otherStore, "id", 999L);
        StoreMember memberOfOtherStore = new StoreMember(otherStore, staffTarget.getUser(), MemberRole.STAFF, 10_000);
        ReflectionTestUtils.setField(memberOfOtherStore, "id", TARGET_MEMBER_ID);
        when(storeMemberRepository.findById(TARGET_MEMBER_ID)).thenReturn(Optional.of(memberOfOtherStore));

        UpdateStoreMemberRequest request = new UpdateStoreMemberRequest(
                null, 12_000, null, null, null, null);

        assertThatThrownBy(() -> storeMemberService.updateMember(STORE_ID, TARGET_MEMBER_ID, USER_ID, request))
                .isInstanceOf(com.example.albam.global.exception.NotFoundException.class);
    }
}
