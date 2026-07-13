package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.payroll.dto.PayrollResponse;
import com.example.albam.domain.payroll.entity.Payroll;
import com.example.albam.domain.payroll.repository.PayrollRepository;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.entity.ShiftStatus;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public PayrollResponse getPayroll(Long storeId, Long memberId, Long userId, int year, int month) {
        StoreMember requester = storeAuthorizationService.requireMember(storeId, userId);
        StoreMember target = storeMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        if (!requester.getId().equals(target.getId()) && !requester.isOwnerOrManager()) {
            throw new InvalidRequestException("본인 또는 매장 관리자만 조회할 수 있습니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        List<Attendance> attendances = attendanceRepository.findAllByStoreMemberIdAndStatusAndWorkDateBetween(
                target.getId(), AttendanceStatus.DONE, from, to);
        // 주휴수당 개근 판정용: 해당 월의 비취소 스케줄 날짜
        Set<LocalDate> scheduledDates = shiftRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(target.getId(), from, to)
                .stream()
                .filter(shift -> shift.getStatus() != ShiftStatus.CANCELED)
                .map(Shift::getWorkDate)
                .collect(Collectors.toSet());

        PayrollResult result = PayrollCalculator.calculate(attendances, target.getHourlyWage(),
                target.getStore().isSmallBusiness(), target.getWeeklyHolidayDay(), scheduledDates);

        Payroll payroll = payrollRepository
                .findByStoreMemberIdAndTargetYearAndTargetMonth(target.getId(), year, month)
                .orElseGet(() -> new Payroll(target, year, month));
        payroll.applyResult(result.regularPay(), result.overtimePay(), result.nightPay(),
                result.holidayWorkPay(), result.weeklyHolidayPay());
        payrollRepository.save(payroll);

        return PayrollResponse.from(payroll);
    }
}
