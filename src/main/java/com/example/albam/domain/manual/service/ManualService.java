package com.example.albam.domain.manual.service;

import com.example.albam.domain.manual.dto.ManualRequest;
import com.example.albam.domain.manual.dto.ManualResponse;
import com.example.albam.domain.manual.dto.ManualSummaryResponse;
import com.example.albam.domain.manual.entity.Manual;
import com.example.albam.domain.manual.repository.ManualRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.file.S3Uploader;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualService {

    private static final String MANUAL_IMAGE_DIRECTORY = "manual-images";

    private final ManualRepository manualRepository;
    private final StoreAuthorizationService storeAuthorizationService;
    private final S3Uploader s3Uploader;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public ManualResponse createManual(Long storeId, Long userId, ManualRequest request) {
        StoreMember author = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Manual manual = manualRepository.save(new Manual(author.getStore(), author, request.category(),
                request.title(), request.content(), request.displayOrderOrDefault(), request.imageUrls()));
        return ManualResponse.from(manual);
    }

    public List<ManualSummaryResponse> getManuals(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return manualRepository.findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId).stream()
                .map(ManualSummaryResponse::from)
                .toList();
    }

    public ManualResponse getManual(Long storeId, Long manualId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return ManualResponse.from(getManualInStore(storeId, manualId));
    }

    /**
     * S3 삭제는 네트워크 호출이라 DB 트랜잭션 안에서 하지 않는다 — 커밋까지 커넥션을 붙잡지 않도록
     * DB 갱신을 먼저 커밋한 뒤, 지워진 이미지는 트랜잭션 밖에서 정리한다.
     */
    public ManualResponse updateManual(Long storeId, Long manualId, Long userId, ManualRequest request) {
        List<String> removedImages = new ArrayList<>();
        ManualResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            storeAuthorizationService.requireOwnerOrManager(storeId, userId);
            Manual manual = getManualInStore(storeId, manualId);
            removedImages.addAll(manual.getImageUrls());
            if (request.imageUrls() != null) {
                removedImages.removeAll(request.imageUrls());
            }
            manual.update(request.category(), request.title(), request.content(),
                    request.displayOrderOrDefault(), request.imageUrls());
            return ManualResponse.from(manual);
        });
        removedImages.forEach(s3Uploader::delete);
        return response;
    }

    public void deleteManual(Long storeId, Long manualId, Long userId) {
        List<String> images = new TransactionTemplate(transactionManager).execute(status -> {
            storeAuthorizationService.requireOwnerOrManager(storeId, userId);
            Manual manual = getManualInStore(storeId, manualId);
            List<String> removed = new ArrayList<>(manual.getImageUrls());
            manualRepository.delete(manual);
            return removed;
        });
        images.forEach(s3Uploader::delete);
    }

    /** 매뉴얼 본문에 넣을 이미지를 먼저 업로드하고 URL을 돌려받는다. */
    public String uploadImage(Long storeId, Long userId, MultipartFile image) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return s3Uploader.upload(image, MANUAL_IMAGE_DIRECTORY);
    }

    private Manual getManualInStore(Long storeId, Long manualId) {
        return manualRepository.findByIdAndStoreId(manualId, storeId)
                .orElseThrow(() -> new NotFoundException("매뉴얼을 찾을 수 없습니다."));
    }
}
