package com.example.albam.domain.notice.service;

import com.example.albam.domain.notice.dto.CreateNoticeRequest;
import com.example.albam.domain.notice.dto.NoticeReadStatusResponse;
import com.example.albam.domain.notice.dto.NoticeResponse;
import com.example.albam.domain.notice.entity.Notice;
import com.example.albam.domain.notice.entity.NoticeRead;
import com.example.albam.domain.notice.repository.NoticeReadRepository;
import com.example.albam.domain.notice.repository.NoticeRepository;
import com.example.albam.domain.storemember.entity.MemberStatus;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.repository.StoreMemberRepository;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public NoticeResponse createNotice(Long storeId, Long userId, CreateNoticeRequest request) {
        StoreMember author = storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Notice notice = noticeRepository.save(
                new Notice(author.getStore(), author, request.title(), request.content()));
        return NoticeResponse.from(notice, 0, false);
    }

    public List<NoticeResponse> getNotices(Long storeId, Long userId) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        return noticeRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(notice -> NoticeResponse.from(notice,
                        noticeReadRepository.countByNoticeId(notice.getId()),
                        noticeReadRepository.existsByNoticeIdAndStoreMemberId(notice.getId(), me.getId())))
                .toList();
    }

    /** "확인했습니다" 버튼. 이미 확인한 공지에 다시 눌러도 오류 없이 넘어간다 (멱등). */
    @Transactional
    public void markRead(Long storeId, Long noticeId, Long userId) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        Notice notice = getNoticeInStore(storeId, noticeId);
        if (!noticeReadRepository.existsByNoticeIdAndStoreMemberId(notice.getId(), me.getId())) {
            noticeReadRepository.save(new NoticeRead(notice, me));
        }
    }

    /** 공지별 확인 현황: 재직 멤버 전원의 확인 여부·시각 (관리자용). */
    public List<NoticeReadStatusResponse> getReadStatus(Long storeId, Long noticeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Notice notice = getNoticeInStore(storeId, noticeId);
        Map<Long, LocalDateTime> readAtByMemberId = noticeReadRepository.findAllByNoticeId(notice.getId())
                .stream()
                .collect(Collectors.toMap(read -> read.getStoreMember().getId(), NoticeRead::getReadAt));
        return storeMemberRepository.findAllByStoreId(storeId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .map(member -> new NoticeReadStatusResponse(member.getId(), member.getUser().getName(),
                        readAtByMemberId.get(member.getId())))
                .toList();
    }

    @Transactional
    public void deleteNotice(Long storeId, Long noticeId, Long userId) {
        storeAuthorizationService.requireOwnerOrManager(storeId, userId);
        Notice notice = getNoticeInStore(storeId, noticeId);
        noticeReadRepository.deleteByNoticeId(notice.getId());
        noticeRepository.delete(notice);
    }

    private Notice getNoticeInStore(Long storeId, Long noticeId) {
        return noticeRepository.findByIdAndStoreId(noticeId, storeId)
                .orElseThrow(() -> new NotFoundException("공지를 찾을 수 없습니다."));
    }
}
