package com.example.albam.domain.attendance.repository;

import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByIdAndStoreMemberStoreId(Long id, Long storeId);

    Optional<Attendance> findFirstByStoreMemberIdAndStatus(Long storeMemberId, AttendanceStatus status);

    List<Attendance> findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long storeMemberId, LocalDate from, LocalDate to);

    List<Attendance> findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long storeId, LocalDate from, LocalDate to);

    List<Attendance> findAllByStoreMemberIdAndStatusAndWorkDateBetween(
            Long storeMemberId, AttendanceStatus status, LocalDate from, LocalDate to);
}
