package com.example.albam.domain.invite.service;

import com.example.albam.domain.invite.dto.ApproveJoinRequestRequest;
import com.example.albam.domain.invite.dto.JoinRequestResponse;
import com.example.albam.domain.invite.entity.JoinRequest;
import com.example.albam.domain.invite.entity.JoinRequestStatus;
import com.example.albam.domain.invite.repository.JoinRequestRepository;
import com.example.albam.domain.store.entity.Store;
import com.example.albam.domain.store.repository.StoreRepository;
import com.example.albam.domain.storemember.entity.MemberRole;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.domain.user.entity.User;
import com.example.albam.domain.user.repository.UserRepository;
import com.example.albam.global.exception.ConflictException;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.InvalidRequestException;
import com.example.albam.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JoinRequestService {

    private static final int DEFAULT_WAGE = 0;

    private final JoinRequestRepository joinRequestRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final UserRepository userRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public JoinRequestResponse requestJoin(Long userId, String code) {
        Store store = storeRepository.findByInviteCode(code)
                .orElseThrow(() -> new NotFoundException("유효하지 않은 초대코드입니다."));
        if (storeMemberRepository.existsByStoreIdAndUserIdAndStatus(store.getId(), userId,
                MemberStatus.ACTIVE)) {
            throw new ConflictException("이미 해당 매장의 멤버입니다.");
        }
        if (joinRequestRepository.existsByStoreIdAndUserIdAndStatus(store.getId(), userId,
                JoinRequestStatus.PENDING)) {
            throw new ConflictException("이미 가입 신청 중입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        JoinRequest joinRequest = joinRequestRepository.save(new JoinRequest(store, user));
        return JoinRequestResponse.from(joinRequest);
    }

    public List<JoinRequestResponse> getMyRequests(Long userId) {
        return joinRequestRepository.findAllByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(JoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public void cancelMyRequest(Long requestId, Long userId) {
        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("가입 신청을 찾을 수 없습니다."));
        if (!joinRequest.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인의 가입 신청만 취소할 수 있습니다.");
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new InvalidRequestException("대기 중인 신청만 취소할 수 있습니다.");
        }
        joinRequestRepository.delete(joinRequest);
    }

    public List<JoinRequestResponse> getPendingRequests(Long storeId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        return joinRequestRepository
                .findAllByStoreIdAndStatusOrderByRequestedAtAsc(storeId, JoinRequestStatus.PENDING).stream()
                .map(JoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public JoinRequestResponse approve(Long storeId, Long requestId, Long userId,
            ApproveJoinRequestRequest request) {
        storeAuthorizationService.requireOwner(storeId, userId);
        if (request.role() == MemberRole.OWNER) {
            throw new InvalidRequestException("OWNER 역할로는 승인할 수 없습니다.");
        }
        JoinRequest joinRequest = getJoinRequest(storeId, requestId);
        joinRequest.approve(request.role());
        // 퇴사했던 멤버가 다시 합류하면 기존 행(근무 이력 포함)을 재활성화한다
        StoreMember existing = storeMemberRepository
                .findByStoreIdAndUserId(storeId, joinRequest.getUser().getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == MemberStatus.ACTIVE) {
                throw new ConflictException("이미 해당 매장의 멤버입니다.");
            }
            existing.rejoin(request.role());
        } else {
            storeMemberRepository.save(
                    new StoreMember(joinRequest.getStore(), joinRequest.getUser(), request.role(), DEFAULT_WAGE));
        }
        return JoinRequestResponse.from(joinRequest);
    }

    @Transactional
    public JoinRequestResponse reject(Long storeId, Long requestId, Long userId) {
        storeAuthorizationService.requireOwner(storeId, userId);
        JoinRequest joinRequest = getJoinRequest(storeId, requestId);
        joinRequest.reject();
        return JoinRequestResponse.from(joinRequest);
    }

    private JoinRequest getJoinRequest(Long storeId, Long requestId) {
        return joinRequestRepository.findByIdAndStoreId(requestId, storeId)
                .orElseThrow(() -> new NotFoundException("가입 신청을 찾을 수 없습니다."));
    }
}
