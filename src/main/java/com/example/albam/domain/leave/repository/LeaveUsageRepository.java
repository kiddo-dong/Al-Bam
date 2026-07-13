package com.example.albam.domain.leave.repository;

import com.example.albam.domain.leave.entity.LeaveUsage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveUsageRepository extends JpaRepository<LeaveUsage, Long> {

    List<LeaveUsage> findAllByStoreMemberIdOrderByLeaveDateDesc(Long storeMemberId);

    long countByStoreMemberId(Long storeMemberId);

    long countByStoreMemberIdAndLeaveDateBetween(Long storeMemberId, LocalDate from, LocalDate to);

    boolean existsByStoreMemberIdAndLeaveDate(Long storeMemberId, LocalDate leaveDate);

    Optional<LeaveUsage> findByIdAndStoreMemberStoreId(Long id, Long storeId);

    List<LeaveUsage> findAllByStoreMemberIdAndLeaveDateBetween(Long storeMemberId, LocalDate from, LocalDate to);

    List<LeaveUsage> findAllByStoreMemberStoreIdAndLeaveDateBetween(Long storeId, LocalDate from, LocalDate to);
}
