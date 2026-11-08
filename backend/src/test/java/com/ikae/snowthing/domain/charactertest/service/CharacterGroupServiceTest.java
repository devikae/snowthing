package com.ikae.snowthing.domain.charactertest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinRequest;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterMapResponse;
import com.ikae.snowthing.domain.charactertest.entity.CharacterSport;
import com.ikae.snowthing.domain.charactertest.model.CharacterJob;
import com.ikae.snowthing.domain.charactertest.model.CompatibilityLevel;
import com.ikae.snowthing.domain.charactertest.model.PlanStyle;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@SpringBootTest
class CharacterGroupServiceTest {

    // 찰떡: ENTP와 INFJ, 각자 타요: INFJ와 ESTJ
    private static final String ENTP = "AAABBBAAABBBAAA";
    private static final String INFJ = "BBBBBBBBBAAAAAA";
    private static final String ESTJ_FAR_HABITS = "AAAAAAAAAAAACCC";
    private static final String ESTJ = "AAAAAAAAAAAAAAA";

    @Autowired CharacterGroupService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM character_participant");
        jdbc.update("DELETE FROM character_group");
    }

    @Test
    void 만든_사람이_바로_참여하고_모임_이름은_닉네임으로_붙는다() {
        CharacterJoinResponse created = create("설원대장", ENTP);

        assertThat(created.groupName()).isEqualTo("설원대장의 모임");
        assertThat(created.accessToken()).hasSizeGreaterThanOrEqualTo(43);
        CharacterMapResponse map = map(created);
        assertThat(map.owner()).isTrue();
        assertThat(map.participantCount()).isEqualTo(1);
        assertThat(map.me().nickname()).isEqualTo("설원대장");
        assertThat(map.me().result().character().job()).isEqualTo(CharacterJob.HYPE);
        assertThat(map.me().result().character().planStyle()).isEqualTo(PlanStyle.SPONTANEOUS);
        assertThat(map.levels())
                .extracting(CharacterMapResponse.Level::level)
                .containsExactly(CompatibilityLevel.values());
        assertThat(map.levels()).allSatisfy(level -> assertThat(level.participants()).isEmpty());
    }

    @Test
    void 토큰_원문은_저장하지_않고_해시만_저장한다() {
        CharacterJoinResponse created = create("해시확인", ENTP);

        String stored =
                jdbc.queryForObject(
                        "SELECT access_token_hash FROM character_participant WHERE public_id = ?",
                        String.class,
                        created.participantPublicId());
        assertThat(stored).hasSize(64).isNotEqualTo(created.accessToken());
        assertThat(stored).isEqualTo(CharacterAccessToken.hash(created.accessToken()));
    }

    @Test
    void 각자_자기_기준_지도를_본다() {
        CharacterJoinResponse owner = create("주인장", ENTP);
        CharacterJoinResponse friend = join(owner, "친구", INFJ);
        CharacterJoinResponse third = join(owner, "셋째", ESTJ_FAR_HABITS);

        CharacterMapResponse ownerMap = map(owner);
        CharacterMapResponse friendMap = map(friend);

        assertThat(ownerMap.participantCount()).isEqualTo(3);
        assertThat(nicknamesAt(ownerMap, CompatibilityLevel.BEST)).containsExactly("친구");
        assertThat(friendMap.me().nickname()).isEqualTo("친구");
        assertThat(friendMap.owner()).isFalse();
        assertThat(nicknamesAt(friendMap, CompatibilityLevel.BEST)).containsExactly("주인장");
        assertThat(nicknamesAt(friendMap, CompatibilityLevel.APART)).containsExactly("셋째");
        assertThat(map(third).me().nickname()).isEqualTo("셋째");
    }

    @Test
    void 같은_단계_안에서는_점수가_높은_순서이고_같으면_먼저_들어온_순서다() {
        // 모두 INFJ라 ENTP 기준 찰떡 표 등급이고, 습관이 같아 찰떡 단계에 모인다.
        CharacterJoinResponse owner = create("기준", ENTP);
        join(owner, "일치적음", INFJ);
        join(owner, "일치많음", "BBABBBABBAABAAA");
        join(owner, "일치적음둘", INFJ);

        CharacterMapResponse.Level best = levelOf(map(owner), CompatibilityLevel.BEST);

        assertThat(best.participants())
                .extracting(CharacterMapResponse.Person::nickname)
                .containsExactly("일치많음", "일치적음", "일치적음둘");
        assertThat(best.participants().get(0).score())
                .isGreaterThan(best.participants().get(1).score());
    }

    @Test
    void 토큰이_없거나_틀리거나_다른_모임_소속이면_지도는_404다() {
        CharacterJoinResponse group = create("가모임장", ENTP);
        CharacterJoinResponse other = create("나모임장", ENTP);

        assertNotFound(() -> service.getMap(group.groupPublicId(), null));
        assertNotFound(() -> service.getMap(group.groupPublicId(), " "));
        assertNotFound(() -> service.getMap(group.groupPublicId(), "틀린토큰"));
        assertNotFound(() -> service.getMap(group.groupPublicId(), other.accessToken()));
        assertNotFound(() -> service.getMap(UUID.randomUUID().toString(), group.accessToken()));
    }

    @Test
    void 없는_모임은_미리보기와_참여가_모두_404다() {
        String missing = UUID.randomUUID().toString();

        assertNotFound(() -> service.getPreview(missing));
        assertNotFound(
                () ->
                        service.join(
                                missing,
                                new CharacterJoinRequest("아무나", CharacterSport.SKI, ENTP)));
    }

    @Test
    void 미리보기는_이름과_인원수만_준다() {
        CharacterJoinResponse owner = create("미리보기", ENTP);
        join(owner, "친구", INFJ);

        var preview = service.getPreview(owner.groupPublicId());

        assertThat(preview.name()).isEqualTo("미리보기의 모임");
        assertThat(preview.participantCount()).isEqualTo(2);
        assertThat(preview.maxParticipants()).isEqualTo(50);
    }

    @Test
    void 모임_안에서_닉네임이_겹치면_거절하고_다른_모임에서는_괜찮다() {
        CharacterJoinResponse first = create("같은이름", ENTP);
        CharacterJoinResponse second = create("다른모임", ENTP);

        assertThatErrorCode(
                () -> join(first, "같은이름", INFJ), ErrorCode.CHARACTER_NICKNAME_DUPLICATED);
        assertThat(join(second, "같은이름", INFJ).accessToken()).isNotBlank();
    }

    @Test
    void 닉네임은_대소문자만_달라도_겹친다() {
        CharacterJoinResponse group = create("Rider", ENTP);

        assertThatErrorCode(
                () -> join(group, "rider", INFJ), ErrorCode.CHARACTER_NICKNAME_DUPLICATED);
    }

    @Test
    void 금칙어_닉네임은_거절한다() {
        assertThatErrorCode(() -> create("운영자입니다", ENTP), ErrorCode.CHARACTER_NICKNAME_FORBIDDEN);
        assertThatErrorCode(() -> create("ADMIN1", ENTP), ErrorCode.CHARACTER_NICKNAME_FORBIDDEN);
    }

    @Test
    void 쉰_명이_차면_더_받지_않는다() {
        CharacterJoinResponse owner = create("인원제한", ENTP);
        for (int index = 2; index <= 50; index++) {
            join(owner, "사람" + index, INFJ);
        }

        assertThatErrorCode(() -> join(owner, "쉰한번째", INFJ), ErrorCode.CHARACTER_GROUP_FULL);
        assertThat(map(owner).participantCount()).isEqualTo(50);
    }

    @Test
    void 동시에_참여해도_쉰_명을_넘지_않는다() throws Exception {
        CharacterJoinResponse owner = create("동시참여", ENTP);
        for (int index = 2; index <= 48; index++) {
            join(owner, "사람" + index, INFJ);
        }
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                String nickname = "동시" + index;
                Callable<Boolean> task =
                        () -> {
                            start.await();
                            try {
                                join(owner, nickname, INFJ);
                                return true;
                            } catch (CustomException exception) {
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CHARACTER_GROUP_FULL);
                                return false;
                            }
                        };
                futures.add(executor.submit(task));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
        assertThat(map(owner).participantCount()).isEqualTo(50);
    }

    @Test
    void 나가면_행이_지워지고_토큰은_더_쓸_수_없다() {
        CharacterJoinResponse owner = create("남는사람", ENTP);
        CharacterJoinResponse friend = join(owner, "나가는이", INFJ);

        service.leave(friend.accessToken());

        assertNotFound(() -> map(friend));
        assertThat(map(owner).participantCount()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM character_participant WHERE public_id = ?",
                                Long.class,
                                friend.participantPublicId()))
                .isZero();
    }

    @Test
    void 만든_사람이_나가도_모임은_남는다() {
        CharacterJoinResponse owner = create("떠나는이", ENTP);
        CharacterJoinResponse friend = join(owner, "남는친구", INFJ);
        join(owner, "셋째", ESTJ_FAR_HABITS);

        service.leave(owner.accessToken());

        CharacterMapResponse map = map(friend);
        assertThat(map.participantCount()).isEqualTo(2);
        assertThat(map.owner()).isFalse();
    }

    @Test
    void 마지막_사람이_나가면_모임도_지워진다() {
        CharacterJoinResponse owner = create("혼자", ENTP);

        service.leave(owner.accessToken());

        assertNotFound(() -> service.getPreview(owner.groupPublicId()));
    }

    @Test
    void 나가기는_토큰이_틀리면_404다() {
        assertNotFound(() -> service.leave("없는토큰"));
        assertNotFound(() -> service.leave(null));
    }

    private CharacterJoinResponse create(String nickname, String answers) {
        return service.create(new CharacterJoinRequest(nickname, CharacterSport.SKI, answers));
    }

    private CharacterJoinResponse join(
            CharacterJoinResponse group, String nickname, String answers) {
        return service.join(
                group.groupPublicId(),
                new CharacterJoinRequest(nickname, CharacterSport.BOARD, answers));
    }

    private CharacterMapResponse map(CharacterJoinResponse viewer) {
        return service.getMap(viewer.groupPublicId(), viewer.accessToken());
    }

    private static CharacterMapResponse.Level levelOf(
            CharacterMapResponse map, CompatibilityLevel level) {
        return map.levels().stream().filter(row -> row.level() == level).findFirst().orElseThrow();
    }

    private static List<String> nicknamesAt(CharacterMapResponse map, CompatibilityLevel level) {
        return levelOf(map, level).participants().stream()
                .map(CharacterMapResponse.Person::nickname)
                .toList();
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatErrorCode(call, ErrorCode.CHARACTER_GROUP_NOT_FOUND);
    }

    private static void assertThatErrorCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
