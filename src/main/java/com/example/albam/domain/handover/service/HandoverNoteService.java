package com.example.albam.domain.handover.service;

import com.example.albam.domain.handover.dto.CreateHandoverNoteRequest;
import com.example.albam.domain.handover.dto.HandoverNoteResponse;
import com.example.albam.domain.handover.entity.HandoverNote;
import com.example.albam.domain.handover.repository.HandoverNoteRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.NotFoundException;
import com.example.albam.global.file.ProfileImageUrls;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverNoteService {

    private final HandoverNoteRepository handoverNoteRepository;
    private final ProfileImageUrls profileImageUrls;
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public HandoverNoteResponse createNote(Long storeId, Long userId, CreateHandoverNoteRequest request) {
        StoreMember author = storeAuthorizationService.requireMember(storeId, userId);
        LocalDate workDate = request.workDate() == null ? LocalDate.now() : request.workDate();
        HandoverNote note = handoverNoteRepository.save(
                new HandoverNote(author.getStore(), author, request.content(), workDate));
        return HandoverNoteResponse.from(note, profileImageUrls.of(note.getAuthor().getUser()));
    }

    public List<HandoverNoteResponse> getNotes(Long storeId, Long userId, LocalDate from, LocalDate to) {
        storeAuthorizationService.requireMember(storeId, userId);
        return handoverNoteRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(storeId, from, to).stream()
                .map(note -> HandoverNoteResponse.from(note, profileImageUrls.of(note.getAuthor().getUser())))
                .toList();
    }

    /** 수정은 삭제와 같은 권한 — 작성자 본인 또는 관리자만. */
    @Transactional
    public HandoverNoteResponse updateNote(Long storeId, Long noteId, Long userId,
            CreateHandoverNoteRequest request) {
        HandoverNote note = requireEditableNote(storeId, noteId, userId, "수정");
        note.update(request.content(),
                request.workDate() == null ? note.getWorkDate() : request.workDate());
        return HandoverNoteResponse.from(note, profileImageUrls.of(note.getAuthor().getUser()));
    }

    /** 삭제는 작성자 본인 또는 관리자만. */
    @Transactional
    public void deleteNote(Long storeId, Long noteId, Long userId) {
        handoverNoteRepository.delete(requireEditableNote(storeId, noteId, userId, "삭제"));
    }

    private HandoverNote requireEditableNote(Long storeId, Long noteId, Long userId, String action) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        HandoverNote note = handoverNoteRepository.findByIdAndStoreId(noteId, storeId)
                .orElseThrow(() -> new NotFoundException("인수인계 노트를 찾을 수 없습니다."));
        if (!note.getAuthor().getId().equals(me.getId()) && !me.isOwnerOrManager()) {
            throw new ForbiddenException("작성자 본인 또는 매장 관리자만 " + action + "할 수 있습니다.");
        }
        return note;
    }
}
