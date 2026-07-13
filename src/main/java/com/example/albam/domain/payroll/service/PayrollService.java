package com.example.albam.domain.payroll.service;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.payroll.dto.PayrollResponse;
import com.example.albam.domain.payroll.entity.Payroll;
import com.example.albam.domain.payroll.repository.PayrollRepository;
import com.example.albam.domain.payroll.service.PayrollCalculator.PayrollResult;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;
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

        PayrollResult result = PayrollCalculator.calculate(attendances, target.getHourlyWage());

        Payroll payroll = payrollRepository
                .findByStoreMemberIdAndTargetYearAndTargetMonth(target.getId(), year, month)
                .orElseGet(() -> new Payroll(target, year, month));
        payroll.applyResult(result.regularPay(), result.overtimePay(), result.nightPay(),
                result.weeklyHolidayPay());
        payrollRepository.save(payroll);

        return PayrollResponse.from(payroll);
    }
}
