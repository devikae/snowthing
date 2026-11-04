package com.ikae.snowthing.domain.answerdraft.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 초안이 인용한 근거 한 건과 저장 시점에 확인한 구간 해시 (029). */
@Entity
@Table(
        name = "answer_draft_evidence",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_answer_draft_evidence",
                        columnNames = {"answer_draft_id", "evidence_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerDraftEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_draft_evidence_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "answer_draft_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_answer_draft_evidence_draft"))
    private AnswerDraft draft;

    @Column(name = "evidence_id", nullable = false, length = 80)
    private String evidenceId;

    @Column(name = "chunk_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String chunkHash;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    AnswerDraftEvidence(AnswerDraft draft, String evidenceId, String chunkHash, int sortOrder) {
        this.draft = draft;
        this.evidenceId = evidenceId;
        this.chunkHash = chunkHash;
        this.sortOrder = sortOrder;
    }
}
