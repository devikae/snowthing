package com.ikae.snowthing.domain.answerdraft.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAudit;

public interface AnswerDraftAuditRepository extends JpaRepository<AnswerDraftAudit, Long> {

    List<AnswerDraftAudit> findByDraftIdOrderByCreatedAtAscIdAsc(Long draftId);
}
