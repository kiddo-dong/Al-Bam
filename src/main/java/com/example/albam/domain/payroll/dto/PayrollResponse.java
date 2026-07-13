package com.example.albam.domain.payroll.dto;

import com.example.albam.domain.payroll.entity.Payroll;
import com.example.albam.domain.storemember.entity.TaxMode;

public record PayrollResponse(
        Long storeMemberId,
        String userName,
        int targetYear,
        int targetMonth,
        long regularPay,
        long overtimePay,
        long nightPay,
        long holidayWorkPay,
        long weeklyHolidayPay,
        long leavePay,
        long totalPay,
        TaxMode taxMode,
        long deduction,
        long netPay
) {
    public static PayrollResponse from(Payroll payroll) {
        return new PayrollResponse(
                payroll.getStoreMember().getId(),
                payroll.getStoreMember().getUser().getName(),
                payroll.getTargetYear(),
                payroll.getTargetMonth(),
                payroll.getRegularPay(),
                payroll.getOvertimePay(),
                payroll.getNightPay(),
                payroll.getHolidayWorkPay(),
                payroll.getWeeklyHolidayPay(),
                payroll.getLeavePay(),
                payroll.getTotalPay(),
                payroll.getStoreMember().getTaxMode(),
                payroll.getDeduction(),
                payroll.getNetPay()
        );
    }
}
