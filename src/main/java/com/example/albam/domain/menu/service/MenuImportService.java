package com.example.albam.domain.menu.service;

import com.example.albam.domain.menu.dto.ConfirmIngredientImportRequest;
import com.example.albam.domain.menu.dto.ConfirmIngredientImportResult;
import com.example.albam.domain.menu.dto.IngredientDraftItem;
import com.example.albam.domain.menu.dto.IngredientImportDraft;
import com.example.albam.domain.menu.dto.IngredientImportDraft.ExtractedIngredient;
import com.example.albam.domain.menu.dto.IngredientImportDraftResponse;
import com.example.albam.domain.menu.dto.MenuIngredientRequest;
import com.example.albam.domain.menu.dto.MenuIngredientResponse;
import com.example.albam.domain.menu.dto.RejectedIngredientDraftItem;
import com.example.albam.domain.menu.entity.IngredientUnit;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.file.SpreadsheetTextExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/**
 * 사장님이 쓰던 엑셀/CSV 원가표를 AI로 해석해 재료 등록 초안을 만든다. 엑셀에서 수작업으로 옮겨 적는
 * 마이그레이션 장벽을 없애는 것이 목적이다.
 *
 * <p>스케줄 AI와 동일한 안전 설계: LLM 추출 결과는 신뢰하지 않는 입력으로 취급해 서버가 항목별로 검증하고,
 * 사용자가 미리보기에서 확인(수정 가능)한 뒤 confirm으로 다시 보내야만 실제 등록된다. 자동 저장은 없다.
 */
@Service
@RequiredArgsConstructor
public class MenuImportService {

    private static final String SYSTEM_PROMPT = """
            당신은 소규모 매장의 재료 원가표(엑셀/CSV에서 추출한 표 텍스트)를 구조화하는 도우미입니다.
            표에서 "재료" 항목들을 찾아 아래 JSON 스키마로만 출력하세요. 마크다운 코드블록이나 설명을 붙이지 마세요.

            {
              "ingredients": [
                {
                  "name": "재료명",
                  "productInfo": "구매처/브랜드 등 부가정보 (없으면 null)",
                  "price": 구매가격(원, 정수),
                  "packageQty": 구매단위 수량(숫자),
                  "unit": "G" | "ML" | "EA",
                  "lossRate": 손실률(0~99 정수, 표에 없으면 0),
                  "category": "분류 (표에 없으면 '기타')",
                  "sourceRow": "원본 위치 (예: 재료시트 3행)"
                }
              ],
              "note": "해석 과정에서 사장님이 확인해야 할 사항 한두 문장 (한국어)"
            }

            규칙:
            1. 단위 환산: kg -> G(x1000), L -> ML(x1000), 개/봉/장/입 등 낱개 단위 -> EA.
               예: "1kg 15000원" -> packageQty 1000, unit "G", price 15000.
            2. "1박스(24입)" 처럼 묶음 안 낱개 수가 명시되면 packageQty는 낱개 수(24), unit은 EA.
            3. 값이 애매하거나 재료가 아닌 행(합계, 메뉴, 빈 행, 헤더)은 결과에서 제외하세요.
            4. 가격에 쉼표나 "원"이 있으면 숫자만 추출하세요.
            5. 확실하지 않은 값을 지어내지 마세요. 필수 값(name, price, packageQty, unit)을 알 수 없는 행은 제외하고
               note에 그 사실을 남기세요.
            """;

    private final SpreadsheetTextExtractor spreadsheetTextExtractor;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final MenuCostService menuCostService;
    private final StoreAuthorizationService storeAuthorizationService;

    public IngredientImportDraftResponse analyze(Long storeId, Long userId, MultipartFile file) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        String tableText = spreadsheetTextExtractor.extract(file);

        String rawResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(tableText)
                .call()
                .content();
        IngredientImportDraft draft = parseDraft(rawResponse);

        List<IngredientDraftItem> accepted = new ArrayList<>();
        List<RejectedIngredientDraftItem> rejected = new ArrayList<>();
        for (ExtractedIngredient extracted : draft.ingredients()) {
            try {
                accepted.add(validate(extracted));
            } catch (InvalidRequestException e) {
                rejected.add(new RejectedIngredientDraftItem(extracted.name(), extracted.sourceRow(),
                        e.getMessage()));
            }
        }
        return new IngredientImportDraftResponse(accepted, rejected, draft.note());
    }

    /** 사용자가 미리보기에서 확인한 목록을 실제 등록한다. 개별 실패는 전체를 막지 않고 rejected로 모아준다. */
    @Transactional
    public ConfirmIngredientImportResult confirm(Long storeId, Long userId,
            ConfirmIngredientImportRequest request) {
        StoreMember manager = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Store store = manager.getStore();
        List<MenuIngredientResponse> created = new ArrayList<>();
        List<RejectedIngredientDraftItem> rejected = new ArrayList<>();
        for (MenuIngredientRequest item : request.items()) {
            try {
                created.add(menuCostService.createIngredientFor(store, item));
            } catch (InvalidRequestException e) {
                rejected.add(new RejectedIngredientDraftItem(item.name(), null, e.getMessage()));
            }
        }
        return new ConfirmIngredientImportResult(created, rejected);
    }

    private IngredientDraftItem validate(ExtractedIngredient extracted) {
        if (extracted.name() == null || extracted.name().isBlank()) {
            throw new InvalidRequestException("재료명이 비어 있습니다.");
        }
        if (extracted.price() == null || extracted.price() < 0) {
            throw new InvalidRequestException("가격을 해석하지 못했습니다.");
        }
        if (extracted.packageQty() == null || extracted.packageQty() <= 0) {
            throw new InvalidRequestException("구매단위 수량을 해석하지 못했습니다.");
        }
        IngredientUnit unit = parseUnit(extracted.unit());
        int lossRate = extracted.lossRate() == null ? 0 : extracted.lossRate();
        if (lossRate < 0 || lossRate > 99) {
            throw new InvalidRequestException("손실률은 0~99 사이여야 합니다.");
        }
        String category = extracted.category() == null || extracted.category().isBlank()
                ? "기타" : extracted.category();
        return new IngredientDraftItem(extracted.name().trim(), extracted.productInfo(), extracted.price(),
                extracted.packageQty(), unit, lossRate, category, extracted.sourceRow());
    }

    private IngredientUnit parseUnit(String unit) {
        if (unit == null) {
            throw new InvalidRequestException("단위를 해석하지 못했습니다.");
        }
        try {
            return IngredientUnit.valueOf(unit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("지원하지 않는 단위입니다: " + unit + " (G/ML/EA만 가능)");
        }
    }

    private IngredientImportDraft parseDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("AI 응답이 비어 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        try {
            IngredientImportDraft draft = objectMapper.readValue(cleaned, IngredientImportDraft.class);
            if (draft.ingredients() == null) {
                return new IngredientImportDraft(List.of(), draft.note());
            }
            return draft;
        } catch (RuntimeException e) {
            throw new InvalidRequestException("AI 응답을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
