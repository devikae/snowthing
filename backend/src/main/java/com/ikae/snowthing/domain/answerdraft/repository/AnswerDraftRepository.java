package com.ikae.snowthing.domain.answerdraft.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraft;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;

public interface AnswerDraftRepository extends JpaRepository<AnswerDraft, Long> {

    Optional<AnswerDraft> findByPublicId(String publicId);

    /** 상태 전이(승인, 거절, 게시)는 이 잠금 안에서 상태를 다시 확인한다. 동시에 들어온 게시 요청은 여기서 줄을 선다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM AnswerDraft d WHERE d.publicId = :publicId")
    Optional<AnswerDraft> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<AnswerDraft> findByCreatedByIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    long countByCreatedByIdAndStatus(Long memberId, AnswerDraftStatus status);

    // 관리자 목록은 행마다 작성자 닉네임을 쓰므로 작성자를 함께 읽는다(ToOne이라 페이징에 안전).
    @EntityGraph(attributePaths = "createdBy")
    Page<AnswerDraft> findByStatus(AnswerDraftStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query("SELECT d FROM AnswerDraft d")
    Page<AnswerDraft> findAllWithCreator(Pageable pageable);
}
