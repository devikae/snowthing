package com.ikae.snowthing.domain.charactertest.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.charactertest.entity.CharacterGroup;
import com.ikae.snowthing.domain.charactertest.repository.CharacterGroupRepository;
import com.ikae.snowthing.domain.charactertest.repository.CharacterParticipantRepository;

import lombok.RequiredArgsConstructor;

/** 배치 하나를 한 트랜잭션으로 지운다. 참여와 같은 순서(모임 먼저)로 잠가 교착을 피한다. */
@Service
@RequiredArgsConstructor
public class CharacterGroupCleanupService {

    private final CharacterGroupRepository groupRepository;
    private final CharacterParticipantRepository participantRepository;

    @Transactional
    public int deleteExpired(List<Long> candidateIds, LocalDateTime cutoff) {
        List<Long> ids =
                groupRepository.lockExpired(candidateIds, cutoff).stream()
                        .map(CharacterGroup::getId)
                        .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        participantRepository.deleteAllByGroupIds(ids);
        return groupRepository.deleteAllByIds(ids);
    }
}
