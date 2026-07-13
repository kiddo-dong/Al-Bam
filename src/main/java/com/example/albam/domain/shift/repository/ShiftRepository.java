package com.example.albam.domain.shift.repository;

import com.example.albam.domain.shift.entity.Shift;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long storeMemberId, LocalDate from, LocalDate to);

    List<Shift> findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long storeId, LocalDate from, LocalDate to);
}
