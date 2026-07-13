package com.example.albam.domain.attendance.service;

import com.example.albam.domain.attendance.dto.AttendanceResponse;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public AttendanceResponse clockIn(Long storeId, Long userId) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        attendanceRepository.findFirstByStoreMemberIdAndStatus(member.getId(), AttendanceStatus.WORKING)
                .ifPresent(a -> {
                    throw new InvalidRequestException("이미 출근 중입니다.");
                });
        Attendance attendance = attendanceRepository.save(new Attendance(member, LocalDateTime.now()));
        return AttendanceResponse.from(attendance);
    }

    @Transactional
    public AttendanceResponse clockOut(Long storeId, Long userId) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        Attendance attendance = attendanceRepository
                .findFirstByStoreMemberIdAndStatus(member.getId(), AttendanceStatus.WORKING)
                .orElseThrow(() -> new InvalidRequestException("출근 중인 근무가 없습니다."));
        attendance.clockOut(LocalDateTime.now());
        return AttendanceResponse.from(attendance);
    }

    public List<AttendanceResponse> getMyAttendance(Long storeId, Long userId, LocalDate from, LocalDate to) {
        StoreMember member = storeAuthorizationService.requireMember(storeId, userId);
        return attendanceRepository
                .findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateDesc(member.getId(), from, to).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    public List<AttendanceResponse> getStoreAttendance(Long storeId, Long userId, LocalDate from, LocalDate to) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        return attendanceRepository
                .findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, from, to).stream()
                .map(AttendanceResponse::from)
                .toList();
    }
}
