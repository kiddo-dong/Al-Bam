package com.example.albam.domain.manual.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.albam.domain.manual.dto.ManualRequest;
import com.example.albam.domain.manual.entity.Manual;
import com.example.albam.domain.manual.repository.ManualRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.file.S3Uploader;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ManualServiceTest {

    @Mock
    private ManualRepository manualRepository;
    @Mock
    private StoreAuthorizationService storeAuthorizationService;
    @Mock
    private S3Uploader s3Uploader;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ManualService manualService;

    private static final Long STORE_ID = 1L;
    private static final Long OTHER_STORE_ID = 2L;
    private static final Long USER_ID = 1L;
    private static final Long MANUAL_ID = 100L;

    private static final String OWN_KEY = "manual-images/1/abc-photo.png";
    private static final String OTHER_STORE_KEY = "manual-images/2/victim-photo.png";

    private StoreMember manager;
    private Manual manual;

    @BeforeEach
    void setUp() {
        Store store = new Store("테스트 매장", "서울", null, null, new HashMap<>(), "ABC123",
                BreakPolicy.STATUTORY, false);
        ReflectionTestUtils.setField(store, "id", STORE_ID);
        User user = new User("manager@albam.dev", "pw", "매니저", "010-0000-0000",
                LocalDate.of(1990, 1, 1), null);
        manager = new StoreMember(store, user, MemberRole.MANAGER, 12_000);
        ReflectionTestUtils.setField(manager, "id", 10L);

        manual = new Manual(store, manager, "조리", "커피 내리는 법", "내용", 0, List.of(OWN_KEY));
        ReflectionTestUtils.setField(manual, "id", MANUAL_ID);

        lenient().when(storeAuthorizationService.requireOwnerOrManager(anyLong(), anyLong())).thenReturn(manager);
        lenient().when(manualRepository.findByIdAndStoreId(MANUAL_ID, STORE_ID)).thenReturn(Optional.of(manual));
        lenient().when(manualRepository.save(any(Manual.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(s3Uploader.toPublicUrl(any())).thenAnswer(i -> "https://bucket/" + i.getArgument(0));
        // TransactionTemplate이 콜백을 그대로 실행하도록 한다.
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void createManual_rejectsImageKeyOwnedByAnotherStore() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.createManual(STORE_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateManual_rejectsImageKeyOwnedByAnotherStore() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateManual_neverDeletesAnotherStoresImage() {
        // 남의 key를 넣었다 빼는 방식으로 그 파일을 지우려는 시도. 요청 단계에서 막혀 삭제까지 가지 않아야 한다.
        ManualRequest attack = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, attack))
                .isInstanceOf(ForbiddenException.class);

        verify(s3Uploader, never()).delete(any());
    }

    @Test
    void updateManual_deletesOnlyKeysDroppedFromThisManual() {
        // 자기 매장 이미지를 빼면 그 이미지만 삭제된다.
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of());

        manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, request);

        verify(s3Uploader).delete(OWN_KEY);
    }

    @Test
    void updateManual_keepsImageWhenStillReferenced() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of(OWN_KEY));

        manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, request);

        verify(s3Uploader, never()).delete(any());
    }

    @Test
    void createManual_acceptsNullImageKeys() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, null);

        assertThat(manualService.createManual(STORE_ID, USER_ID, request)).isNotNull();
    }

    @Test
    void uploadImage_putsFileUnderThisStoresPath() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        when(s3Uploader.upload(any(), any())).thenReturn(OWN_KEY);

        manualService.uploadImage(STORE_ID, USER_ID, file);

        // 저장 시 소유권 판별이 경로 접두사에 의존하므로, 업로드도 반드시 매장별 경로여야 한다.
        verify(s3Uploader).upload(file, "manual-images/" + STORE_ID);
    }

    @Test
    void imageKeyOfOneStoreIsNotAPrefixMatchForAnother() {
        // manual-images/1/ 과 manual-images/12/ 를 혼동하지 않는지 (접두사 검사의 흔한 함정).
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0,
                List.of("manual-images/12/other.png"));

        assertThatThrownBy(() -> manualService.createManual(STORE_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

}
