package com.example.albam.domain.shift.service;

import com.example.albam.domain.shift.dto.ShiftTemplateBulkRequest;
import com.example.albam.domain.shift.dto.ShiftTemplateRequest;
import com.example.albam.domain.shift.dto.ShiftTemplateResponse;
import com.example.albam.domain.shift.entity.ShiftTemplate;
import com.example.albam.domain.shift.repository.ShiftTemplateRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.NotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public ShiftTemplateResponse createTemplate(Long storeId, Long userId, ShiftTemplateRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        if (shiftTemplateRepository.existsByStoreIdAndName(storeId, request.name())) {
            throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + request.name());
        }
        ShiftTemplate template = shiftTemplateRepository.save(new ShiftTemplate(manager.getStore(),
                request.name(), request.startTime(), request.endTime(), request.breakMinutes(),
                request.displayOrderOrDefault()));
        return ShiftTemplateResponse.from(template);
    }

    /**
     * 여러 근무 유형을 한 번에 등록한다. 하나라도 어긋나면 전부 되돌아간다.
     *
     * <p>이름 중복은 저장 전에 모두 확인한다. 저장하면서 하나씩 보면 앞의 몇 개가 이미 들어간 뒤에
     * 실패하는데, 트랜잭션이 되돌리더라도 어디까지 유효했는지 알려주지 못해 원인이 불분명해진다.
     * 배치 안에서 이름이 겹치는 경우도 같이 걸러낸다 — DB에는 아직 없어 개별 조회로는 안 잡힌다.
     */
    @Transactional
    public List<ShiftTemplateResponse> createTemplates(Long storeId, Long userId,
            ShiftTemplateBulkRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);

        Set<String> namesInBatch = new HashSet<>();
        for (ShiftTemplateRequest item : request.items()) {
            if (!namesInBatch.add(item.name())) {
                throw new ConflictException("요청 안에 같은 이름이 두 번 있습니다: " + item.name());
            }
            if (shiftTemplateRepository.existsByStoreIdAndName(storeId, item.name())) {
                throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + item.name());
            }
        }

        int nextOrder = shiftTemplateRepository.findMaxDisplayOrder(storeId).orElse(-1) + 1;
        List<ShiftTemplate> templates = new ArrayList<>();
        for (ShiftTemplateRequest item : request.items()) {
            templates.add(new ShiftTemplate(manager.getStore(), item.name(), item.startTime(),
                    item.endTime(), item.breakMinutes(), nextOrder++));
        }
        return shiftTemplateRepository.saveAll(templates).stream()
                .map(ShiftTemplateResponse::from)
                .toList();
    }

    public List<ShiftTemplateResponse> getTemplates(Long storeId, Long userId) {
        storeAuthorizationService.requireMember(storeId, userId);
        return shiftTemplateRepository.findAllByStoreIdOrderByDisplayOrderAscIdAsc(storeId).stream()
                .map(ShiftTemplateResponse::from)
                .toList();
    }

    @Transactional
    public ShiftTemplateResponse updateTemplate(Long storeId, Long templateId, Long userId,
            ShiftTemplateRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        ShiftTemplate template = getTemplateInStore(storeId, templateId);
        if (shiftTemplateRepository.existsByStoreIdAndNameAndIdNot(storeId, request.name(), templateId)) {
            throw new ConflictException("같은 이름의 템플릿이 이미 있습니다: " + request.name());
        }
        template.update(request.name(), request.startTime(), request.endTime(), request.breakMinutes(),
                request.displayOrderOrDefault());
        return ShiftTemplateResponse.from(template);
    }

    @Transactional
    public void deleteTemplate(Long storeId, Long templateId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        shiftTemplateRepository.delete(getTemplateInStore(storeId, templateId));
    }

    private ShiftTemplate getTemplateInStore(Long storeId, Long templateId) {
        return shiftTemplateRepository.findByIdAndStoreId(templateId, storeId)
                .orElseThrow(() -> new NotFoundException("시프트 템플릿을 찾을 수 없습니다."));
    }
}
