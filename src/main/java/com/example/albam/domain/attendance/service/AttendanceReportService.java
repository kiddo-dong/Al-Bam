package com.example.albam.domain.attendance.service;

import com.example.albam.domain.attendance.dto.AttendanceReportEntry;
import com.example.albam.domain.attendance.dto.WorkComplianceStatus;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.leave.entity.LeaveUsage;
import com.example.albam.domain.leave.repository.LeaveUsageRepository;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ForbiddenException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스케줄(Shift)과 실제 근태(Attendance)를 날짜별로 대조해 지각·조퇴·무단결근·스케줄 외 근무를 판정한다.
 *
 * <p>매칭 규칙: 같은 멤버·같은 날짜에서 스케줄 시간대와 겹치는 출근 기록 중 겹침이 가장 큰 것을 매칭한다.
 * 아직 시작 전이거나 진행 중인 스케줄(종료 시각이 지나지 않음)은 출근 기록이 없어도 결근으로 판정하지 않는다.
 * 해당 날짜에 연차 사용 기록이 있으면 결근 대신 연차로 표시한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReportService {

    private final ShiftRepository shiftRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveUsageRepository leaveUsageRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    public List<AttendanceReportEntry> getReport(Long storeId, Long userId, Long storeMemberId,
            LocalDate from, LocalDate to) {
        StoreMember requester = storeAuthorizationService.requireMember(storeId, userId);
        boolean selfOnly = storeMemberId != null && requester.getId().equals(storeMemberId);
        if (!selfOnly && !requester.isOwnerOrManager()) {
            throw new ForbiddenException("매장 관리자만 다른 멤버의 근태 리포트를 조회할 수 있습니다.");
        }

        List<Shift> shifts;
        List<Attendance> attendances;
        List<LeaveUsage> leaves;
        if (storeMemberId != null) {
            shifts = shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeMemberId, from, to);
            attendances = attendanceRepository
                    .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(storeMemberId, from, to);
            leaves = leaveUsageRepository.findAllByStoreMemberIdAndLeaveDateBetween(storeMemberId, from, to);
        } else {
            shifts = shiftRepository.findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeId, from, to);
            attendances = attendanceRepository
                    .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, from, to);
            leaves = leaveUsageRepository.findAllByStoreMemberStoreIdAndLeaveDateBetween(storeId, from, to);
        }
        Set<String> leaveKeys = leaves.stream()
                .map(leave -> dayKey(leave.getStoreMember().getId(), leave.getLeaveDate()))
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        Set<Long> matchedAttendanceIds = new HashSet<>();
        List<AttendanceReportEntry> entries = new ArrayList<>();

        for (Shift shift : shifts) {
            if (shift.getStatus() == ShiftStatus.CANCELED || shift.startDateTime().isAfter(now)) {
                continue;
            }
            Attendance matched = findBestMatch(shift, attendances, matchedAttendanceIds, now);
            if (matched == null) {
                boolean onLeave = leaveKeys.contains(
                        dayKey(shift.getStoreMember().getId(), shift.getWorkDate()));
                if (onLeave) {
                    entries.add(entryOf(shift, null, WorkComplianceStatus.ON_LEAVE, 0, 0));
                } else if (shift.endDateTime().isBefore(now)) {
                    entries.add(entryOf(shift, null, WorkComplianceStatus.ABSENT, 0, 0));
                }
                // 아직 진행 중인 스케줄은 판단 유보
                continue;
            }
            matchedAttendanceIds.add(matched.getId());
            long lateMinutes = Math.max(0,
                    Duration.between(shift.startDateTime(), matched.getClockInAt()).toMinutes());
            if (matched.getClockOutAt() == null) {
                entries.add(entryOf(shift, matched, WorkComplianceStatus.WORKING, lateMinutes, 0));
                continue;
            }
            long earlyLeaveMinutes = Math.max(0,
                    Duration.between(matched.getClockOutAt(), shift.endDateTime()).toMinutes());
            entries.add(entryOf(shift, matched, judge(lateMinutes, earlyLeaveMinutes), lateMinutes,
                    earlyLeaveMinutes));
        }

        for (Attendance attendance : attendances) {
            if (!matchedAttendanceIds.contains(attendance.getId())) {
                entries.add(new AttendanceReportEntry(null, attendance.getId(),
                        attendance.getStoreMember().getId(), attendance.getStoreMember().getUser().getName(),
                        attendance.getWorkDate(), null, null, attendance.getClockInAt(),
                        attendance.getClockOutAt(), WorkComplianceStatus.EXTRA, 0, 0));
            }
        }

        entries.sort(Comparator.comparing(AttendanceReportEntry::workDate)
                .thenComparing(AttendanceReportEntry::storeMemberId));
        return entries;
    }

    private Attendance findBestMatch(Shift shift, List<Attendance> attendances,
            Set<Long> matchedAttendanceIds, LocalDateTime now) {
        Attendance best = null;
        long bestOverlap = 0;
        for (Attendance attendance : attendances) {
            if (matchedAttendanceIds.contains(attendance.getId())
                    || !attendance.getStoreMember().getId().equals(shift.getStoreMember().getId())
                    || !attendance.getWorkDate().equals(shift.getWorkDate())) {
                continue;
            }
            LocalDateTime attendanceEnd = attendance.getClockOutAt() == null ? now : attendance.getClockOutAt();
            LocalDateTime overlapStart = max(shift.startDateTime(), attendance.getClockInAt());
            LocalDateTime overlapEnd = min(shift.endDateTime(), attendanceEnd);
            if (overlapStart.isBefore(overlapEnd)) {
                long overlap = Duration.between(overlapStart, overlapEnd).toMinutes();
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    best = attendance;
                }
            }
        }
        return best;
    }

    private WorkComplianceStatus judge(long lateMinutes, long earlyLeaveMinutes) {
        if (lateMinutes > 0 && earlyLeaveMinutes > 0) {
            return WorkComplianceStatus.LATE_AND_EARLY_LEAVE;
        }
        if (lateMinutes > 0) {
            return WorkComplianceStatus.LATE;
        }
        if (earlyLeaveMinutes > 0) {
            return WorkComplianceStatus.EARLY_LEAVE;
        }
        return WorkComplianceStatus.NORMAL;
    }

    private AttendanceReportEntry entryOf(Shift shift, Attendance attendance, WorkComplianceStatus status,
            long lateMinutes, long earlyLeaveMinutes) {
        return new AttendanceReportEntry(
                shift.getId(),
                attendance == null ? null : attendance.getId(),
                shift.getStoreMember().getId(),
                shift.getStoreMember().getUser().getName(),
                shift.getWorkDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                attendance == null ? null : attendance.getClockInAt(),
                attendance == null ? null : attendance.getClockOutAt(),
                status,
                lateMinutes,
                earlyLeaveMinutes);
    }

    private String dayKey(Long storeMemberId, LocalDate date) {
        return storeMemberId + ":" + date;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
