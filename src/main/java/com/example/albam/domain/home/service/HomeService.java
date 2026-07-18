package com.example.albam.domain.home.service;

import com.example.albam.domain.attendance.dto.AttendanceReportEntry;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.attendance.service.AttendanceReportService;
import com.example.albam.domain.checklist.entity.ChecklistItem;
import com.example.albam.domain.checklist.entity.ChecklistType;
import com.example.albam.domain.checklist.repository.ChecklistCompletionRepository;
import com.example.albam.domain.checklist.repository.ChecklistItemRepository;
import com.example.albam.domain.handover.dto.HandoverNoteResponse;
import com.example.albam.domain.handover.repository.HandoverNoteRepository;
import com.example.albam.domain.home.dto.HomeResponse;
import com.example.albam.domain.home.dto.HomeResponse.ChecklistProgress;
import com.example.albam.domain.home.dto.HomeResponse.ManagerSection;
import com.example.albam.domain.home.dto.HomeResponse.MyDaySection;
import com.example.albam.domain.home.dto.HomeResponse.OwnerSection;
import com.example.albam.domain.home.dto.HomeResponse.TodayOrderItem;
import com.example.albam.domain.home.dto.HomeResponse.TodayRosterEntry;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import com.example.albam.domain.invite.repository.JoinRequestRepository;
import com.example.albam.domain.notice.repository.NoticeReadRepository;
import com.example.albam.domain.notice.repository.NoticeRepository;
import com.example.albam.domain.payroll.dto.DashboardResponse;
import com.example.albam.domain.payroll.service.PayrollService;
import com.example.albam.domain.payroll.service.StoreDashboardService;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.supplier.entity.Supplier;
import com.example.albam.domain.supplier.repository.SupplierItemRepository;
import com.example.albam.domain.supplier.repository.SupplierRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매장 홈 화면 집계. 역할에 따라 공통(myDay) / 관리자 / 오너 섹션을 채운다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int RECENT_HANDOVER_LIMIT = 5;

    private final StoreAuthorizationService storeAuthorizationService;
    private final ShiftRepository shiftRepository;
    private final AttendanceRepository attendanceRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistCompletionRepository checklistCompletionRepository;
    private final HandoverNoteRepository handoverNoteRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierItemRepository supplierItemRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final PayrollService payrollService;
    private final StoreDashboardService storeDashboardService;
    private final AttendanceReportService attendanceReportService;

    public HomeResponse getHome(Long storeId, Long userId) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        LocalDate today = LocalDate.now();

        MyDaySection myDay = buildMyDay(storeId, userId, me, today);
        ManagerSection managerSection = me.isOwnerOrManager()
                ? buildManagerSection(storeId, userId, today) : null;
        OwnerSection ownerSection = me.getRole() == MemberRole.OWNER
                ? buildOwnerSection(storeId, userId, today) : null;

        return new HomeResponse(me.getRole(), me.getId(), myDay, managerSection, ownerSection);
    }

    private MyDaySection buildMyDay(Long storeId, Long userId, StoreMember me, LocalDate today) {
        List<ShiftResponse> todayShifts = shiftRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(me.getId(), today, today)
                .stream()
                .filter(shift -> shift.getStatus() != ShiftStatus.CANCELED)
                .map(ShiftResponse::from)
                .toList();

        Attendance workingAttendance = attendanceRepository
                .findFirstByStoreMemberIdAndStatus(me.getId(), AttendanceStatus.WORKING)
                .orElse(null);

        YearMonth thisMonth = YearMonth.from(today);
        long estimatedNetPay = payrollService
                .estimateMyPayroll(storeId, userId, thisMonth.getYear(), thisMonth.getMonthValue())
                .netPay();

        long unreadNoticeCount = noticeRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(notice -> !noticeReadRepository.existsByNoticeIdAndStoreMemberId(notice.getId(),
                        me.getId()))
                .count();

        List<ChecklistItem> items =
                checklistItemRepository.findAllByStoreIdOrderByTypeAscDisplayOrderAscIdAsc(storeId);
        Set<Long> checkedItemIds = checklistCompletionRepository
                .findAllByItemStoreIdAndWorkDate(storeId, today).stream()
                .map(completion -> completion.getItem().getId())
                .collect(Collectors.toSet());

        List<HandoverNoteResponse> recentNotes = handoverNoteRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(storeId, today.minusDays(1), today)
                .stream()
                .limit(RECENT_HANDOVER_LIMIT)
                .map(HandoverNoteResponse::from)
                .toList();

        return new MyDaySection(todayShifts,
                workingAttendance != null,
                workingAttendance == null ? null : workingAttendance.getClockInAt(),
                estimatedNetPay, unreadNoticeCount,
                checklistProgress(items, checkedItemIds, ChecklistType.OPEN),
                checklistProgress(items, checkedItemIds, ChecklistType.CLOSE),
                recentNotes);
    }

    private ChecklistProgress checklistProgress(List<ChecklistItem> items, Set<Long> checkedItemIds,
            ChecklistType type) {
        List<ChecklistItem> ofType = items.stream().filter(item -> item.getType() == type).toList();
        int done = (int) ofType.stream().filter(item -> checkedItemIds.contains(item.getId())).count();
        return new ChecklistProgress(done, ofType.size());
    }

    private ManagerSection buildManagerSection(Long storeId, Long userId, LocalDate today) {
        Map<Long, AttendanceReportEntry> reportByShiftId = attendanceReportService
                .getReport(storeId, userId, null, today, today).stream()
                .filter(entry -> entry.shiftId() != null)
                .collect(Collectors.toMap(AttendanceReportEntry::shiftId, Function.identity()));

        List<TodayRosterEntry> roster = new ArrayList<>();
        for (Shift shift : shiftRepository
                .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                        storeId, today, today)) {
            if (shift.getStatus() == ShiftStatus.CANCELED) {
                continue;
            }
            AttendanceReportEntry entry = reportByShiftId.get(shift.getId());
            roster.add(new TodayRosterEntry(shift.getId(), shift.getStoreMember().getId(),
                    shift.getStoreMember().getUser().getName(), shift.getStartTime(), shift.getEndTime(),
                    entry == null ? null : entry.status(),
                    entry == null ? null : entry.clockInAt(),
                    entry == null ? null : entry.clockOutAt()));
        }

        DayOfWeek todayDow = today.getDayOfWeek();
        List<TodayOrderItem> orderItems = new ArrayList<>();
        for (Supplier supplier : supplierRepository
                .findAllByStoreIdOrderByCategoryAscDisplayOrderAscIdAsc(storeId)) {
            supplierItemRepository.findAllBySupplierIdOrderByDisplayOrderAscIdAsc(supplier.getId()).stream()
                    .filter(item -> item.getWeeklyQuantities().containsKey(todayDow))
                    .forEach(item -> orderItems.add(new TodayOrderItem(supplier.getName(), item.getName(),
                            item.getSpec(), item.getWeeklyQuantities().get(todayDow))));
        }

        return new ManagerSection(roster, orderItems);
    }

    private OwnerSection buildOwnerSection(Long storeId, Long userId, LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        DashboardResponse dashboard = storeDashboardService.getDashboard(storeId, userId,
                thisMonth.getYear(), thisMonth.getMonthValue());
        long pendingJoinRequests = joinRequestRepository.countByStoreIdAndStatus(storeId,
                JoinRequestStatus.PENDING);
        return new OwnerSection(dashboard.totalLaborCost(), dashboard.totalNetPay(), pendingJoinRequests);
    }
}
