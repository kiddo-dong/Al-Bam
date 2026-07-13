package com.example.albam.domain.shift.service;

import com.example.albam.domain.shift.dto.CreateShiftRequest;
import com.example.albam.domain.shift.dto.ShiftResponse;
import com.example.albam.domain.shift.dto.UpdateShiftRequest;
import com.example.albam.domain.shift.entity.Shift;
import com.example.albam.domain.shift.repository.ShiftRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public ShiftResponse createShift(Long storeId, Long userId, CreateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        StoreMember target = getStoreMemberInStore(storeId, request.storeMemberId());
        Shift shift = shiftRepository.save(
                new Shift(target, request.workDate(), request.startTime(), request.endTime()));
        return ShiftResponse.from(shift);
    }

    public List<ShiftResponse> getShifts(Long storeId, Long userId, Long storeMemberId, LocalDate from,
            LocalDate to) {
        storeAuthorizationService.requireMember(storeId, userId);
        List<Shift> shifts;
        if (storeMemberId != null) {
            getStoreMemberInStore(storeId, storeMemberId);
            shifts = shiftRepository.findAllByStoreMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeMemberId, from, to);
        } else {
            shifts = shiftRepository.findAllByStoreMemberStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    storeId, from, to);
        }
        return shifts.stream().map(ShiftResponse::from).toList();
    }

    @Transactional
    public ShiftResponse updateShift(Long storeId, Long shiftId, Long userId, UpdateShiftRequest request) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        shift.update(request.workDate(), request.startTime(), request.endTime(), request.status());
        return ShiftResponse.from(shift);
    }

    @Transactional
    public void deleteShift(Long storeId, Long shiftId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Shift shift = getShiftInStore(storeId, shiftId);
        shiftRepository.delete(shift);
    }

    private StoreMember getStoreMemberInStore(Long storeId, Long storeMemberId) {
        StoreMember member = storeMemberRepository.findById(storeMemberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
        if (!member.getStore().getId().equals(storeId)) {
            throw new InvalidRequestException("해당 매장의 멤버가 아닙니다.");
        }
        return member;
    }

    private Shift getShiftInStore(Long storeId, Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new NotFoundException("스케줄을 찾을 수 없습니다."));
        if (!shift.getStoreMember().getStore().getId().equals(storeId)) {
            throw new NotFoundException("스케줄을 찾을 수 없습니다.");
        }
        return shift;
    }
}
