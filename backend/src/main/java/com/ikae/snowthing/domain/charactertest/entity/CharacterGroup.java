package com.ikae.snowthing.domain.charactertest.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.*;

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캐릭터 테스트 모임 (036). updated_at은 새 참여 때만 저장소 쿼리로 바꾸며 30일 보관 판단 기준이다. */
@Entity
@Table(
        name = "character_group",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_character_group_public_id", columnNames = "public_id"),
        indexes = {
            @Index(name = "idx_character_group_updated_at", columnList = "updated_at"),
            @Index(name = "idx_character_group_owner", columnList = "owner_participant_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterGroup extends BaseTimeEntity {

    public static final int MAX_PARTICIPANTS = 50;
    private static final String NAME_SUFFIX = "의 모임";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_group_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    // 참여자와 서로를 가리키므로 연관 대신 ID만 둔다. 운영 FK는 ON DELETE SET NULL이다.
    @Column(name = "owner_participant_id")
    private Long ownerParticipantId;

    public CharacterGroup(String ownerNickname) {
        this.publicId = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(ownerNickname) + NAME_SUFFIX;
    }

    public void assignOwner(Long participantId) {
        this.ownerParticipantId = Objects.requireNonNull(participantId);
    }

    public boolean isOwner(Long participantId) {
        return participantId != null && participantId.equals(ownerParticipantId);
    }
}
