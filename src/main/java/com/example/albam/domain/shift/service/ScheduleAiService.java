package com.example.albam.domain.shift.service;

import com.example.albam.domain.shift.dto.AiScheduleDraft;
import com.example.albam.domain.shift.dto.AiShiftProposal;
import com.example.albam.domain.shift.dto.ConfirmScheduleDraftRequest;
import com.example.albam.domain.shift.dto.ConfirmScheduleDraftResult;
import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.RejectedScheduleDraftItem;
import com.example.albam.domain.shift.dto.ScheduleDraftItem;
import com.example.albam.domain.shift.dto.ScheduleDraftRequest;
import com.example.albam.domain.shift.dto.ScheduleDraftResponse;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.store.entity.BusinessHour;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * AI(LLM)로 스케줄 초안을 생성하는 서비스. LLM 응답은 신뢰하지 않는 입력으로 취급하며, 실제 근로기준법 검증은
 * 전부 {@link ShiftService}의 기존 규칙(근무가능요일·영업시간·연소자보호·중복·주간상한)을 그대로 재사용한다.
 * LLM은 "그럴듯한 배치안"만 제시하고, 적법성 판단은 결코 LLM에게 맡기지 않는다.
 *
 * <p>클래스 레벨 트랜잭션을 두지 않는다 — generateDraft가 OpenAI 응답을 기다리는 동안 DB 커넥션을
 * 붙잡지 않기 위함. DB 접근은 각 리포지토리/{@link ShiftService} 호출이 자체 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleAiService {

    private static final int MAX_DRAFT_PERIOD_DAYS = 31;

    private static final String SYSTEM_PROMPT = """
            당신은 소규모 매장의 근무 스케줄 초안을 제안하는 도우미입니다.
            사용자가 JSON으로 매장 영업시간, 재직 중인 멤버 목록(storeMemberId, 근무가능요일, 주휴일, 연소자 여부),
            기간, 요구사항을 제공합니다.

            규칙:
            1. storeMemberId는 반드시 제공된 목록에 있는 값만 사용하세요. 새로 만들어내지 마세요.
            2. 각 멤버의 근무가능요일과 매장 영업시간을 최대한 지키세요. 다만 최종 적법성 검증(연소자 보호,
               주간 근로시간 상한, 중복 배정 등)은 서버가 별도로 수행하니 당신은 합리적인 초안만 제시하면 됩니다.
            3. 응답은 아래 JSON 스키마만 출력하세요. 마크다운 코드블록이나 설명 문장을 앞뒤에 붙이지 마세요.

            {
              "shifts": [
                { "storeMemberId": 1, "workDate": "2026-08-01", "startTime": "09:00", "endTime": "18:00" }
              ],
              "note": "이 초안에 대한 한 줄 설명 (한국어)"
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ShiftService shiftService;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    public ScheduleDraftResponse generateDraft(Long storeId, Long userId, ScheduleDraftRequest request) {
        StoreMember requester = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new InvalidRequestException("종료일은 시작일 이후여야 합니다.");
        }
        long periodDays = ChronoUnit.DAYS.between(request.periodStart(), request.periodEnd()) + 1;
        if (periodDays > MAX_DRAFT_PERIOD_DAYS) {
            throw new InvalidRequestException("AI 스케줄 초안 생성 기간은 최대 " + MAX_DRAFT_PERIOD_DAYS + "일까지 가능합니다.");
        }

        Store store = requester.getStore();
        List<StoreMember> members = storeMemberRepository.findAllByStoreId(storeId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .toList();
        if (members.isEmpty()) {
            throw new InvalidRequestException("재직 중인 멤버가 없어 스케줄 초안을 생성할 수 없습니다.");
        }
        Map<Long, StoreMember> memberById = members.stream()
                .collect(Collectors.toMap(StoreMember::getId, member -> member));

        String contextJson = buildContextJson(store, members, request);
        String rawResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(contextJson)
                .call()
                .content();
        AiScheduleDraft draft = parseDraft(rawResponse);

        List<ScheduleDraftItem> accepted = new ArrayList<>();
        List<RejectedScheduleDraftItem> rejected = new ArrayList<>();
        for (AiShiftProposal proposal : draft.shifts()) {
            StoreMember member = proposal.storeMemberId() == null ? null : memberById.get(proposal.storeMemberId());
            String memberName = member != null ? member.getUser().getName() : null;
            try {
                if (member == null) {
                    throw new InvalidRequestException("존재하지 않거나 재직 중이 아닌 멤버입니다.");
                }
                if (proposal.workDate() == null || proposal.workDate().isBefore(request.periodStart())
                        || proposal.workDate().isAfter(request.periodEnd())) {
                    throw new InvalidRequestException("요청한 기간을 벗어난 날짜입니다.");
                }
                if (proposal.startTime() == null || proposal.endTime() == null) {
                    throw new InvalidRequestException("시작/종료 시각이 비어 있습니다.");
                }
                int breakMinutes = shiftService.validateDraftShift(storeId, userId, proposal.storeMemberId(),
                        proposal.workDate(), proposal.startTime(), proposal.endTime());
                accepted.add(new ScheduleDraftItem(proposal.storeMemberId(), memberName, proposal.workDate(),
                        proposal.startTime(), proposal.endTime(), breakMinutes));
            } catch (InvalidRequestException e) {
                rejected.add(new RejectedScheduleDraftItem(proposal.storeMemberId(), memberName,
                        proposal.workDate(), proposal.startTime(), proposal.endTime(), e.getMessage()));
            }
        }
        return new ScheduleDraftResponse(accepted, rejected, draft.note());
    }

    /** 사용자가 확인(수정 가능)한 초안을 실제로 저장한다. 여기서도 각 항목은 ShiftService.createShift를 그대로 통과해야 한다. */
    @Transactional
    public ConfirmScheduleDraftResult confirmDraft(Long storeId, Long userId, ConfirmScheduleDraftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        List<ShiftResponse> created = new ArrayList<>();
        List<RejectedScheduleDraftItem> rejected = new ArrayList<>();
        for (ScheduleDraftItem item : request.items()) {
            try {
                CreateShiftRequest createRequest = new CreateShiftRequest(item.storeMemberId(), item.workDate(),
                        item.startTime(), item.endTime(), item.breakMinutes());
                created.add(shiftService.createShift(storeId, userId, createRequest));
            } catch (InvalidRequestException | NotFoundException e) {
                rejected.add(new RejectedScheduleDraftItem(item.storeMemberId(), item.memberName(),
                        item.workDate(), item.startTime(), item.endTime(), e.getMessage()));
            }
        }
        return new ConfirmScheduleDraftResult(created, rejected);
    }

    private String buildContextJson(Store store, List<StoreMember> members, ScheduleDraftRequest request) {
        Map<String, Object> businessHours = new LinkedHashMap<>();
        for (Map.Entry<DayOfWeek, BusinessHour> entry : store.getBusinessHours().entrySet()) {
            BusinessHour hour = entry.getValue();
            businessHours.put(entry.getKey().name(),
                    hour.isClosed() ? "휴무" : hour.getOpenTime() + "~" + hour.getCloseTime());
        }

        List<Map<String, Object>> memberContexts = members.stream().map(member -> {
            Map<String, Object> memberContext = new LinkedHashMap<>();
            memberContext.put("storeMemberId", member.getId());
            memberContext.put("name", member.getUser().getName());
            memberContext.put("availableDays", member.getAvailableDays().isEmpty()
                    ? "제한없음" : member.getAvailableDays().stream().map(Enum::name).toList());
            memberContext.put("weeklyHolidayDay",
                    member.getWeeklyHolidayDay() == null ? null : member.getWeeklyHolidayDay().name());
            memberContext.put("isMinor",
                    LaborStandards.isMinor(member.getUser().getBirthDate(), request.periodStart()));
            return memberContext;
        }).toList();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("periodStart", request.periodStart().toString());
        context.put("periodEnd", request.periodEnd().toString());
        context.put("storeBusinessHours", businessHours);
        context.put("members", memberContexts);
        context.put("requirement", request.requirement() == null || request.requirement().isBlank()
                ? "특별 요구사항 없음. 각 멤버의 근무가능요일 내에서 매장 영업시간 안에 골고루 배정." : request.requirement());

        try {
            return objectMapper.writeValueAsString(context);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("스케줄 컨텍스트 생성에 실패했습니다.");
        }
    }

    private AiScheduleDraft parseDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("AI 응답이 비어 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        try {
            return objectMapper.readValue(cleaned, AiScheduleDraft.class);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("AI 응답을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
