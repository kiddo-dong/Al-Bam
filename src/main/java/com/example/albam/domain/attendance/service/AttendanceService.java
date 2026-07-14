package com.example.albam.domain.attendance.service;

import com.example.albam.domain.attendance.dto.AttendanceResponse;
import com.example.albam.domain.attendance.dto.CorrectAttendanceRequest;
import com.example.albam.domain.attendance.dto.CreateAttendanceRequest;
import com.example.albam.domain.attendance.entity.Attendance;
import com.example.albam.domain.attendance.entity.AttendanceStatus;
import com.example.albam.domain.attendance.repository.AttendanceRepository;
import com.example.albam.domain.store.entity.BreakPolicy;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.labor.LaborStandards;
import java.time.Duration;
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
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    /** 관리자가 누락된 근태를 수동으로 등록한다 (직원이 출근을 아예 찍지 못한 경우). */
    @Transactional
    public AttendanceResponse createAttendance(Long storeId, Long userId, CreateAttendanceRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = storeMemberRepository.findById(request.storeMemberId())
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!target.getStore().getId().equals(storeId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다.");
        }
        if (target.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 근태를 등록할 수 없습니다.");
        }
        Attendance attendance = new Attendance(target, request.clockInAt());
        if (request.clockOutAt() != null) {
            int breakMinutes = resolveBreakMinutes(target, request.clockInAt(), request.clockOutAt(),
                    request.breakMinutes());
            attendance.correctTimes(request.clockInAt(), request.clockOutAt(), breakMinutes);
        }
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

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
        LocalDateTime now = LocalDateTime.now();
        attendance.clockOut(now, resolveBreakMinutes(member, attendance.getClockInAt(), now, null));
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

    @Transactional
    public AttendanceResponse correctAttendance(Long storeId, Long attendanceId, Long userId,
            CorrectAttendanceRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Attendance attendance = getAttendanceInStore(storeId, attendanceId);
        int breakMinutes = request.clockOutAt() == null ? 0
                : resolveBreakMinutes(attendance.getStoreMember(), request.clockInAt(), request.clockOutAt(),
                        request.breakMinutes());
        attendance.correctTimes(request.clockInAt(), request.clockOutAt(), breakMinutes);
        return AttendanceResponse.from(attendance);
    }

    @Transactional
    public void deleteAttendance(Long storeId, Long attendanceId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        attendanceRepository.delete(getAttendanceInStore(storeId, attendanceId));
    }

    private Attendance getAttendanceInStore(Long storeId, Long attendanceId) {
        return attendanceRepository.findByIdAndStoreMemberStoreId(attendanceId, storeId)
                .orElseThrow(() -> new NotFoundException("근태 기록을 찾을 수 없습니다."));
    }

    private int resolveBreakMinutes(StoreMember member, LocalDateTime clockInAt, LocalDateTime clockOutAt,
            Integer requested) {
        boolean statutory = member.getStore().getBreakPolicy() == BreakPolicy.STATUTORY;
        long spanMinutes = Duration.between(clockInAt, clockOutAt).toMinutes();
        return LaborStandards.resolveBreakMinutes(statutory, spanMinutes, requested);
    }
}
