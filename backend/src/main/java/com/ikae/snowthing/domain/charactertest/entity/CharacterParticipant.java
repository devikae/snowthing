package com.ikae.snowthing.domain.charactertest.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.charactertest.model.CharacterType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캐릭터 테스트 참여자 (036). 회원과 연결하지 않고, 토큰 원문과 IP를 저장하지 않는다. */
@Entity
@Table(
        name = "character_participant",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_character_participant_public_id",
                    columnNames = "public_id"),
            @UniqueConstraint(
                    name = "uk_character_participant_token",
                    columnNames = "access_token_hash"),
            @UniqueConstraint(
                    name = "uk_character_participant_group_nickname",
                    columnNames = {"character_group_id", "nickname"})
        },
        indexes =
                @Index(
                        name = "idx_character_participant_group_id",
                        columnList = "character_group_id, character_participant_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_participant_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_group_id", nullable = false)
    private CharacterGroup group;

    @Column(name = "nickname", nullable = false, length = 10)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport", nullable = false, length = 10)
    private CharacterSport sport;

    @Column(name = "answers", nullable = false, length = 15, columnDefinition = "CHAR(15)")
    private String answers;

    @Column(name = "type_code", nullable = false, length = 4, columnDefinition = "CHAR(4)")
    private String typeCode;

    @Column(
            name = "access_token_hash",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)")
    private String accessTokenHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CharacterParticipant(
            CharacterGroup group,
            String nickname,
            CharacterSport sport,
            CharacterAnswers answers,
            String accessTokenHash) {
        this.publicId = UUID.randomUUID().toString();
        this.group = Objects.requireNonNull(group);
        this.nickname = Objects.requireNonNull(nickname);
        this.sport = Objects.requireNonNull(sport);
        this.answers = answers.value();
        this.typeCode = answers.type().name();
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash);
    }

    public CharacterAnswers answersValue() {
        return new CharacterAnswers(answers);
    }

    public CharacterType type() {
        return CharacterType.valueOf(typeCode);
    }
}
