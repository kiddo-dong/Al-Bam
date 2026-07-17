package com.example.albam.domain.shift.service;

import com.example.albam.domain.shift.dto.ShiftTemplateRequest;
import com.example.albam.domain.shift.dto.ShiftTemplateResponse;
import com.example.albam.domain.shift.entity.ShiftTemplate;
import com.example.albam.domain.shift.repository.ShiftTemplateRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.NotFoundException;
import java.util.List;
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
