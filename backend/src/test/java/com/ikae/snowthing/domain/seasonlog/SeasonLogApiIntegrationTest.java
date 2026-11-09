package com.ikae.snowthing.domain.seasonlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberWithdrawalRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitCreateRequest;
import com.ikae.snowthing.domain.seasonlog.model.Season;
import com.ikae.snowthing.domain.seasonlog.service.SeasonLogService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 시즌 방문 기록과 결산 공유를 실제 보안 필터와 MySQL 제약으로 확인한다 (IKA-64).
 *
 * <p>날짜는 실제 오늘(KST)에서 계산한다. 지난 시즌 날짜는 언제 돌려도 과거이고 기록할 수 있는 범위다. 동시 요청 테스트가 다른 트랜잭션의 커밋을 봐야 하므로 테스트
 * 트랜잭션으로 감싸지 않고 만든 행을 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeasonLogApiIntegrationTest {

    private static final String ME = "/api/v1/season-logs/me";
    private static final String VISITS = ME + "/visits";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MemberRepository members;
    @Autowired ResortRepository resorts;
    @Autowired SeasonLogService seasonLogService;
    @Autowired MemberWithdrawalRepository withdrawalRepository;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> resortIds = new ArrayList<>();
    private Member owner;
    private Member other;
    private Resort high1;
    private Resort phoenix;
    private Season previous;

    @BeforeEach
    void setUp() {
        owner = saveMember("기록왕");
        other = saveMember("다른사람");
        high1 = saveResort("하이원", 1);
        phoenix = saveResort("휘닉스", 2);
        previous = Season.latest(LocalDate.now(ZoneId.of("Asia/Seoul"))).previous();
    }

    @AfterEach
    void tearDown() {
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM season_share WHERE member_id = ?", id);
            jdbc.update("DELETE FROM season_visit WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
        for (Long id : resortIds) {
            jdbc.update("DELETE FROM resort WHERE resort_id = ?", id);
        }
    }

    @Test
    void 기록을_남기고_시즌_결산을_본다() throws Exception {
        LocalDate first = previous.startDate().plusDays(30);
        record(owner, first, high1).andExpect(status().isCreated());
        record(owner, first.plusDays(1), phoenix).andExpect(status().isCreated());
        record(owner, first.plusDays(7), high1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitDate").value(first.plusDays(7).toString()))
                .andExpect(jsonPath("$.resortName").value(high1.getName()));
        record(other, first, phoenix).andExpect(status().isCreated());

        mvc.perform(
                        get(ME).param("season", String.valueOf(previous.startYear()))
                                .with(user(details(owner))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.season.label").value(previous.label()))
                .andExpect(jsonPath("$.visits.length()").value(3))
                .andExpect(jsonPath("$.summary.totalDays").value(3))
                .andExpect(jsonPath("$.summary.firstDate").value(first.toString()))
                .andExpect(jsonPath("$.summary.lastDate").value(first.plusDays(7).toString()))
                .andExpect(jsonPath("$.summary.topResort.resortName").value(high1.getName()))
                .andExpect(jsonPath("$.summary.topResort.days").value(2))
                .andExpect(jsonPath("$.shareToken").doesNotExist());
    }

    @Test
    void 하루에_한_번만_기록된다() throws Exception {
        LocalDate day = previous.startDate().plusDays(40);
        record(owner, day, high1).andExpect(status().isCreated());

        record(owner, day, phoenix)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_001"));
        assertThat(visitCount(owner)).isEqualTo(1);
    }

    @Test
    void 같은_날짜를_동시에_보내도_하나만_저장된다() throws Exception {
        LocalDate day = previous.startDate().plusDays(50);
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<String> task =
                        () -> {
                            ready.countDown();
                            start.await();
                            try {
                                seasonLogService.addVisit(
                                        owner.getId(),
                                        new SeasonVisitCreateRequest(day, high1.getId()));
                                return "OK";
                            } catch (CustomException exception) {
                                return exception.getErrorCode().name();
                            }
                        };
                results.add(pool.submit(task));
            }
            ready.await();
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(outcome -> !"OK".equals(outcome))
                    .containsOnly(ErrorCode.SEASON_VISIT_DUPLICATED.name());
        } finally {
            pool.shutdownNow();
        }
        assertThat(visitCount(owner)).isEqualTo(1);
    }

    @Test
    void 미래_날짜와_시즌_밖_날짜와_너무_오래된_날짜는_거절한다() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        record(owner, today.plusDays(1), high1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_002"));
        record(owner, LocalDate.of(today.getYear() - 1, 7, 1), high1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_003"));
        record(owner, previous.previous().startDate().plusDays(30), high1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_004"));
        assertThat(visitCount(owner)).isZero();
    }

    @Test
    void 비활성_리조트와_빈_값은_거절한다() throws Exception {
        Resort closed = saveResort("폐장", 9, false);
        record(owner, previous.startDate().plusDays(30), closed)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESORT_001"));
        mvc.perform(
                        post(VISITS)
                                .with(user(details(owner)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 기록_삭제는_내_기록만_지운다() throws Exception {
        LocalDate day = previous.startDate().plusDays(60);
        record(owner, day, high1).andExpect(status().isCreated());

        mvc.perform(delete(VISITS + "/" + day).with(user(details(other))).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_005"));
        assertThat(visitCount(owner)).isEqualTo(1);

        mvc.perform(delete(VISITS + "/" + day).with(user(details(owner))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(visitCount(owner)).isZero();
    }

    @Test
    void 로그인하지_않으면_내_기록_API를_쓸_수_없고_CSRF_없는_쓰기는_막힌다() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(
                        post(VISITS)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(previous.startDate(), high1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(VISITS)
                                .with(user(details(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(previous.startDate(), high1)))
                .andExpect(status().isForbidden());
        mvc.perform(put(ME + "/shares/" + previous.startYear()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 볼_수_없는_시즌은_거절한다() throws Exception {
        int next = previous.startYear() + 2;
        mvc.perform(get(ME).param("season", String.valueOf(next)).with(user(details(owner))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEASON_006"));
        mvc.perform(put(ME + "/shares/" + next).with(user(details(owner))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 공유를_켜야_공개되고_공개_응답에는_닉네임과_숫자만_있다() throws Exception {
        LocalDate day = previous.startDate().plusDays(30);
        record(owner, day, high1).andExpect(status().isCreated());
        record(owner, day.plusDays(2), high1).andExpect(status().isCreated());

        String token = enableShare(owner);
        assertThat(enableShare(owner)).as("이미 켜져 있으면 같은 토큰").isEqualTo(token);

        String body =
                mvc.perform(get("/api/v1/season-shares/" + token))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.nickname").value(owner.getNickname()))
                        .andExpect(jsonPath("$.seasonLabel").value(previous.label()))
                        .andExpect(jsonPath("$.totalDays").value(2))
                        .andExpect(jsonPath("$.topResort.resortName").value(high1.getName()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body)
                .doesNotContain(owner.getEmail())
                .doesNotContain(owner.getPublicId())
                .doesNotContain(day.toString())
                .doesNotContain("firstDate");

        mvc.perform(
                        get(ME).param("season", String.valueOf(previous.startYear()))
                                .with(user(details(owner))))
                .andExpect(jsonPath("$.shareToken").value(token));

        mvc.perform(
                        delete(ME + "/shares/" + previous.startYear())
                                .with(user(details(owner)))
                                .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/season-shares/" + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEASON_007"));

        String renewed = enableShare(owner);
        assertThat(renewed).as("다시 켜면 새 링크라 예전 링크는 살아나지 않는다").isNotEqualTo(token);
        mvc.perform(get("/api/v1/season-shares/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void 없는_토큰과_정지된_회원의_결산은_보이지_않는다() throws Exception {
        mvc.perform(get("/api/v1/season-shares/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/season-shares/not-a-token")).andExpect(status().isNotFound());

        String token = enableShare(owner);
        jdbc.update(
                "UPDATE member SET status = ? WHERE member_id = ?",
                MemberStatus.SUSPENDED.name(),
                owner.getId());
        mvc.perform(get("/api/v1/season-shares/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void 탈퇴하면_방문_기록과_공유를_지운다() throws Exception {
        record(owner, previous.startDate().plusDays(30), high1).andExpect(status().isCreated());
        enableShare(owner);

        transactions.executeWithoutResult(
                status ->
                        withdrawalRepository.erasePersonalData(
                                owner.getId(),
                                owner.getNickname(),
                                owner.getEmail(),
                                List.of(owner.getEmail()),
                                "withdrawn-" + owner.getId() + "@withdrawn.invalid",
                                LocalDateTime.now()));

        assertThat(visitCount(owner)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM season_share WHERE member_id = ?",
                                Integer.class,
                                owner.getId()))
                .isZero();
    }

    private org.springframework.test.web.servlet.ResultActions record(
            Member member, LocalDate date, Resort resort) throws Exception {
        MockHttpServletRequestBuilder request =
                post(VISITS)
                        .with(user(details(member)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(date, resort));
        return mvc.perform(request);
    }

    private String enableShare(Member member) throws Exception {
        String response =
                mvc.perform(
                                put(ME + "/shares/" + previous.startYear())
                                        .with(user(details(member)))
                                        .with(csrf()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("shareToken").asText();
    }

    private String body(LocalDate date, Resort resort) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("visitDate", date.toString(), "resortId", resort.getId()));
    }

    private int visitCount(Member member) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM season_visit WHERE member_id = ?",
                Integer.class,
                member.getId());
    }

    private CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
    }

    private Member saveMember(String nicknamePrefix) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Member member =
                members.save(
                        Member.builder()
                                .publicId(UUID.randomUUID().toString())
                                .email(unique + "@test.invalid")
                                .nickname(nicknamePrefix + unique.substring(0, 6))
                                .role(Role.ROLE_USER)
                                .build());
        memberIds.add(member.getId());
        return member;
    }

    private Resort saveResort(String prefix, int displayOrder) {
        return saveResort(prefix, displayOrder, true);
    }

    private Resort saveResort(String prefix, int displayOrder, boolean active) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Resort resort =
                resorts.save(
                        Resort.builder()
                                .name(prefix + unique)
                                .regionName("강원")
                                .code("T" + unique.toUpperCase())
                                .displayOrder(displayOrder)
                                .active(active)
                                .build());
        resortIds.add(resort.getId());
        return resort;
    }
}
