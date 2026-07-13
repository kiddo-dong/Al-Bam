package com.example.albam.domain.payroll.repository;

import com.example.albam.domain.payroll.entity.Payroll;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    Optional<Payroll> findByStoreMemberIdAndTargetYearAndTargetMonth(Long storeMemberId, int targetYear,
            int targetMonth);
}
