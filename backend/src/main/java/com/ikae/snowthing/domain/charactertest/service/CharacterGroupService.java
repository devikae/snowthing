package com.ikae.snowthing.domain.charactertest.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.charactertest.dto.CharacterGroupPreviewResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinRequest;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterLabel;
import com.ikae.snowthing.domain.charactertest.dto.CharacterMapResponse;
import com.ikae.snowthing.domain.charactertest.entity.CharacterGroup;
import com.ikae.snowthing.domain.charactertest.entity.CharacterParticipant;
import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.charactertest.model.CharacterCompatibility;
import com.ikae.snowthing.domain.charactertest.model.CharacterCompatibilityTable;
import com.ikae.snowthing.domain.charactertest.model.CompatibilityLevel;
import com.ikae.snowthing.domain.charactertest.repository.CharacterGroupRepository;
import com.ikae.snowthing.domain.charactertest.repository.CharacterParticipantRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 캐릭터 테스트 모임. 설계: docs/conception/024_character_test/01_design.md
 *
 * <p>토큰이 없거나 맞지 않거나 소속이 아니면 지도와 나가기는 모두 404다. 숨긴 리소스의 존재를 알리지 않는 기존 규칙을 따른다. 참여와 나가기는 모임 행을 잠가 차례로
 * 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CharacterGroupService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int GROUP_NAME_MIN = 2;
    private static final int GROUP_NAME_MAX = 20;
    private static final Pattern GROUP_NAME_CHARS = Pattern.compile("[가-힣a-zA-Z0-9 ]+");

    private final CharacterGroupRepository groupRepository;
    private final CharacterParticipantRepository participantRepository;
    private final CharacterCompatibilityTable compatibilityTable;
    private final CharacterResultService resultService;

    @Transactional
    public CharacterJoinResponse create(CharacterJoinRequest request) {
        validateNickname(request.nickname());
        CharacterGroup group =
                groupRepository.save(
                        new CharacterGroup(request.nickname(), groupName(request.groupName())));
        String token = CharacterAccessToken.generate();
        CharacterParticipant owner =
                participantRepository.save(toParticipant(group, request, token));
        group.assignOwner(owner.getId());
        return toJoinResponse(group, owner, token);
    }

    @Transactional(readOnly = true)
    public CharacterGroupPreviewResponse getPreview(String groupPublicId) {
        CharacterGroup group =
                groupRepository
                        .findByPublicId(groupPublicId)
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND));
        return new CharacterGroupPreviewResponse(
                group.getPublicId(),
                group.getName(),
                participantRepository.countByGroup_Id(group.getId()),
                CharacterGroup.MAX_PARTICIPANTS);
    }

    @Transactional
    public CharacterJoinResponse join(String groupPublicId, CharacterJoinRequest request) {
        validateNickname(request.nickname());
        CharacterGroup group =
                groupRepository
                        .findLockedByPublicId(groupPublicId)
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND));
        if (participantRepository.countByGroup_Id(group.getId())
                >= CharacterGroup.MAX_PARTICIPANTS) {
            throw new CustomException(ErrorCode.CHARACTER_GROUP_FULL);
        }
        // 대소문자 구분은 DB 콜레이션을 따른다. 같은 잠금 안이라 검사와 저장 사이에 끼어들 수 없다.
        if (participantRepository.existsByGroup_IdAndNickname(group.getId(), request.nickname())) {
            throw new CustomException(ErrorCode.CHARACTER_NICKNAME_DUPLICATED);
        }
        String token = CharacterAccessToken.generate();
        CharacterParticipant participant =
                participantRepository.save(toParticipant(group, request, token));
        groupRepository.touchJoinedAt(group.getId(), LocalDateTime.now(KST));
        return toJoinResponse(group, participant, token);
    }

    @Transactional(readOnly = true)
    public CharacterMapResponse getMap(String groupPublicId, String accessToken) {
        CharacterParticipant me = requireParticipant(accessToken);
        CharacterGroup group = me.getGroup();
        if (!group.getPublicId().equals(groupPublicId)) {
            throw new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND);
        }
        List<CharacterParticipant> everyone =
                participantRepository.findAllByGroup_IdOrderByIdAsc(group.getId());
        return new CharacterMapResponse(
                group.getPublicId(),
                group.getName(),
                everyone.size(),
                CharacterGroup.MAX_PARTICIPANTS,
                group.isOwner(me.getId()),
                new CharacterMapResponse.Me(
                        me.getPublicId(),
                        me.getNickname(),
                        me.getSport(),
                        me.getAnswers(),
                        resultService.compute(me.answersValue())),
                toLevels(me, everyone));
    }

    /** 마지막 사람이 나가면 모임도 지운다. 아무도 볼 수 없는 행을 30일 동안 남길 이유가 없다. */
    @Transactional
    public void leave(String accessToken) {
        CharacterParticipant me = requireParticipant(accessToken);
        CharacterGroup group = lockGroup(me.getGroup().getId());
        if (group.isOwner(me.getId())) {
            groupRepository.clearOwner(group.getId());
        }
        participantRepository.deleteOneById(me.getId());
        if (participantRepository.countByGroup_Id(group.getId()) == 0) {
            groupRepository.deleteAllByIds(List.of(group.getId()));
        }
    }

    private List<CharacterMapResponse.Level> toLevels(
            CharacterParticipant me, List<CharacterParticipant> everyone) {
        Map<CompatibilityLevel, List<Ranked>> grouped = new EnumMap<>(CompatibilityLevel.class);
        for (CompatibilityLevel level : CompatibilityLevel.values()) {
            grouped.put(level, new ArrayList<>());
        }
        CharacterAnswers myAnswers = me.answersValue();
        for (CharacterParticipant other : everyone) {
            if (other.getId().equals(me.getId())) {
                continue;
            }
            CharacterCompatibility compatibility =
                    CharacterCompatibility.of(
                            compatibilityTable.levelOf(me.type(), other.type()),
                            myAnswers,
                            other.answersValue());
            grouped.get(compatibility.level()).add(new Ranked(other, compatibility));
        }
        List<CharacterMapResponse.Level> levels = new ArrayList<>();
        for (CompatibilityLevel level : CompatibilityLevel.values()) {
            levels.add(
                    new CharacterMapResponse.Level(
                            level,
                            grouped.get(level).stream()
                                    .sorted(
                                            Comparator.comparingInt(Ranked::score)
                                                    .reversed()
                                                    .thenComparing(
                                                            ranked -> ranked.participant().getId()))
                                    .map(Ranked::toPerson)
                                    .toList()));
        }
        return levels;
    }

    private CharacterParticipant requireParticipant(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND);
        }
        return participantRepository
                .findWithGroupByTokenHash(CharacterAccessToken.hash(accessToken))
                .orElseThrow(() -> new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND));
    }

    private CharacterGroup lockGroup(Long groupId) {
        return groupRepository
                .findLockedById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHARACTER_GROUP_NOT_FOUND));
    }

    /** 비었으면 null(기본 이름을 쓴다). 앞뒤 공백을 빼고, 연속 공백은 하나로 줄여 길이와 글자, 금칙어를 본다. */
    private static String groupName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.strip().replaceAll("\\s+", " ");
        int length = name.codePointCount(0, name.length());
        if (length < GROUP_NAME_MIN
                || length > GROUP_NAME_MAX
                || !GROUP_NAME_CHARS.matcher(name).matches()) {
            throw new CustomException(ErrorCode.CHARACTER_GROUP_NAME_INVALID);
        }
        // 띄어 써서 금칙어를 피하지 못하게 공백을 빼고도 본다.
        if (CharacterNicknameFilter.isForbidden(name)
                || CharacterNicknameFilter.isForbidden(name.replace(" ", ""))) {
            throw new CustomException(ErrorCode.CHARACTER_GROUP_NAME_FORBIDDEN);
        }
        return name;
    }

    private static void validateNickname(String nickname) {
        if (CharacterNicknameFilter.isForbidden(nickname)) {
            throw new CustomException(ErrorCode.CHARACTER_NICKNAME_FORBIDDEN);
        }
    }

    private static CharacterParticipant toParticipant(
            CharacterGroup group, CharacterJoinRequest request, String token) {
        return new CharacterParticipant(
                group,
                request.nickname(),
                request.sport(),
                new CharacterAnswers(request.answers()),
                CharacterAccessToken.hash(token));
    }

    private static CharacterJoinResponse toJoinResponse(
            CharacterGroup group, CharacterParticipant participant, String token) {
        return new CharacterJoinResponse(
                group.getPublicId(), group.getName(), participant.getPublicId(), token);
    }

    private record Ranked(CharacterParticipant participant, CharacterCompatibility compatibility) {
        int score() {
            return compatibility.score();
        }

        CharacterMapResponse.Person toPerson() {
            return new CharacterMapResponse.Person(
                    participant.getPublicId(),
                    participant.getNickname(),
                    participant.getSport(),
                    CharacterLabel.of(participant.type()),
                    compatibility.score(),
                    compatibility.habitGaps());
        }
    }
}
