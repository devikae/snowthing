package com.ikae.snowthing.domain.charactertest.dto;

import java.util.List;

import com.ikae.snowthing.domain.charactertest.entity.CharacterSport;
import com.ikae.snowthing.domain.charactertest.model.CompatibilityLevel;

/** 토큰 소유자 기준 지도. levels는 위에서 아래 순서로 5단계가 언제나 모두 들어 있고 단계 안은 점수 순이다. 다른 사람의 답변은 담지 않는다. */
public record CharacterMapResponse(
        String groupPublicId,
        String groupName,
        long participantCount,
        int maxParticipants,
        boolean owner,
        Me me,
        List<Level> levels) {

    public CharacterMapResponse {
        levels = List.copyOf(levels);
    }

    /** answers는 본인에게만 돌려준다. 다른 기기에서 복귀해도 카드와 내 모임 만들기가 되게 하려는 것이다. */
    public record Me(
            String participantPublicId,
            String nickname,
            CharacterSport sport,
            String answers,
            CharacterResultResponse result) {}

    public record Level(CompatibilityLevel level, List<Person> participants) {
        public Level {
            participants = List.copyOf(participants);
        }
    }

    public record Person(
            String participantPublicId,
            String nickname,
            CharacterSport sport,
            CharacterLabel character,
            int score,
            List<Integer> habitGaps) {
        public Person {
            habitGaps = List.copyOf(habitGaps);
        }
    }
}
