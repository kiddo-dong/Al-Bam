package com.example.albam.domain.handover.service;

import com.example.albam.domain.handover.dto.CreateHandoverNoteRequest;
import com.example.albam.domain.handover.dto.HandoverNoteResponse;
import com.example.albam.domain.handover.entity.HandoverNote;
import com.example.albam.domain.handover.repository.HandoverNoteRepository;
import com.example.albam.domain.storemember.entity.StoreMember;
import com.example.albam.domain.storemember.service.StoreAuthorizationService;
import com.example.albam.global.exception.ForbiddenException;
import com.example.albam.global.exception.NotFoundException;
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
    private final StoreAuthorizationService storeAuthorizationService;

    @Transactional
    public HandoverNoteResponse createNote(Long storeId, Long userId, CreateHandoverNoteRequest request) {
        StoreMember author = storeAuthorizationService.requireMember(storeId, userId);
        LocalDate workDate = request.workDate() == null ? LocalDate.now() : request.workDate();
        HandoverNote note = handoverNoteRepository.save(
                new HandoverNote(author.getStore(), author, request.content(), workDate));
        return HandoverNoteResponse.from(note);
    }

    public List<HandoverNoteResponse> getNotes(Long storeId, Long userId, LocalDate from, LocalDate to) {
        storeAuthorizationService.requireMember(storeId, userId);
        return handoverNoteRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByCreatedAtDesc(storeId, from, to).stream()
                .map(HandoverNoteResponse::from)
                .toList();
    }

    /** 삭제는 작성자 본인 또는 관리자만. */
    @Transactional
    public void deleteNote(Long storeId, Long noteId, Long userId) {
        StoreMember me = storeAuthorizationService.requireMember(storeId, userId);
        HandoverNote note = handoverNoteRepository.findByIdAndStoreId(noteId, storeId)
                .orElseThrow(() -> new NotFoundException("인수인계 노트를 찾을 수 없습니다."));
        if (!note.getAuthor().getId().equals(me.getId()) && !me.isOwnerOrManager()) {
            throw new ForbiddenException("작성자 본인 또는 매장 관리자만 삭제할 수 있습니다.");
        }
        handoverNoteRepository.delete(note);
    }
}
