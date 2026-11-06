package com.ikae.snowthing.domain.charactertest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 공개 경로, CSRF, 응답 계약을 실제 보안 필터 체인으로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class CharacterTestApiIntegrationTest {

    private static final String TOKEN_HEADER = CharacterTestController.ACCESS_TOKEN_HEADER;
    private static final String ANSWERS = "AAABBBAAABBBAAA";
    private static final Pattern FORBIDDEN_WORDS =
            Pattern.compile("MBTI|\\b[EI][SN][TF][JP]\\b", Pattern.CASE_INSENSITIVE);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM character_participant");
        jdbc.update("DELETE FROM character_group");
    }

    @Test
    void 결과_계산은_비회원이_쓸_수_있고_저장하지_않으며_유형_코드를_내보내지_않는다() throws Exception {
        String body =
                mvc.perform(
                                post("/api/v1/character-test/result")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"answers\":\"" + ANSWERS + "\"}"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.character.job").value("HYPE"))
                        .andExpect(jsonPath("$.character.planStyle").value("SPONTANEOUS"))
                        .andExpect(jsonPath("$.stats.social").value(3))
                        .andExpect(jsonPath("$.stats.adventure").value(3))
                        .andExpect(jsonPath("$.stats.warmth").value(0))
                        .andExpect(jsonPath("$.stats.planning").value(0))
                        .andExpect(jsonPath("$.highlightQuestions.length()").value(4))
                        .andExpect(jsonPath("$.bestMatch.job").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(FORBIDDEN_WORDS.matcher(body).find()).isFalse();
        assertThat(count("character_group")).isZero();
        assertThat(count("character_participant")).isZero();
    }

    @Test
    void CSRF_토큰이_없는_쓰기는_비회원_경로에서도_막힌다() throws Exception {
        mvc.perform(
                        post("/api/v1/character-test/result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":\"" + ANSWERS + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/character-groups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(joinBody("씨에스알")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/character-participants/me").header(TOKEN_HEADER, "x"))
                .andExpect(status().isForbidden());
        assertThat(count("character_group")).isZero();
    }

    @Test
    void 공개_경로는_메서드와_경로가_정확히_맞을_때만_열린다() throws Exception {
        String group = "/api/v1/character-groups/" + UUID.randomUUID();
        // 지정하지 않은 메서드는 인증 규칙으로 떨어진다.
        mvc.perform(get("/api/v1/character-groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/character-test/result")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/character-participants/me")).andExpect(status().isUnauthorized());
        mvc.perform(put(group).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch(group).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(group).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get(group + "/participants")).andExpect(status().isUnauthorized());
        mvc.perform(post(group + "/map").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get(group + "/map/extra")).andExpect(status().isUnauthorized());
    }

    @Test
    void 모임_생성부터_지도까지_응답에_유형_코드와_다른_사람의_답변이_없다() throws Exception {
        Map<?, ?> owner = createGroup("모임장");
        String groupId = (String) owner.get("groupPublicId");
        assertThat(owner.get("groupName")).isEqualTo("모임장의 모임");

        String friendAnswers = "BBBBBBBBBAAAAAA";
        Map<?, ?> friend =
                read(
                        mvc.perform(
                                        post("/api/v1/character-groups/"
                                                        + groupId
                                                        + "/participants")
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(joinBody("친구", friendAnswers)))
                                .andExpect(status().isCreated())
                                .andExpect(header().string("Cache-Control", "no-store")));

        String map =
                mvc.perform(
                                get("/api/v1/character-groups/" + groupId + "/map")
                                        .header(TOKEN_HEADER, owner.get("accessToken")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.groupName").value("모임장의 모임"))
                        .andExpect(jsonPath("$.participantCount").value(2))
                        .andExpect(jsonPath("$.owner").value(true))
                        .andExpect(jsonPath("$.levels.length()").value(5))
                        .andExpect(jsonPath("$.levels[0].level").value("BEST"))
                        .andExpect(jsonPath("$.levels[0].participants[0].nickname").value("친구"))
                        .andExpect(jsonPath("$.levels[0].participants[0].score").isNumber())
                        .andExpect(
                                jsonPath("$.levels[0].participants[0].habitGaps.length()").value(3))
                        .andExpect(
                                jsonPath("$.levels[0].participants[0].character.job")
                                        .value("RESORT_OUTSIDER"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(FORBIDDEN_WORDS.matcher(map).find()).isFalse();
        assertThat(map).doesNotContain(friendAnswers).doesNotContain("accessToken");
        assertThat(map).contains(ANSWERS); // 본인 답변만 돌려준다.

        mvc.perform(
                        get("/api/v1/character-groups/" + groupId)
                                .header(TOKEN_HEADER, friend.get("accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("모임장의 모임"))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.maxParticipants").value(50))
                .andExpect(jsonPath("$.nickname").doesNotExist());
    }

    @Test
    void 토큰이_없으면_지도는_404이고_숨긴_리소스_코드를_쓴다() throws Exception {
        String groupId = (String) createGroup("토큰주인").get("groupPublicId");

        mvc.perform(get("/api/v1/character-groups/" + groupId + "/map"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_001"));
        mvc.perform(
                        get("/api/v1/character-groups/" + groupId + "/map")
                                .header(TOKEN_HEADER, "다른사람토큰"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/character-participants/me").with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/character-groups/not-a-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void 나가기는_본문_없이_204이고_다른_사람을_지우는_경로는_없다() throws Exception {
        Map<?, ?> owner = createGroup("모임장둘");
        String groupId = (String) owner.get("groupPublicId");
        Map<?, ?> first =
                read(
                        mvc.perform(
                                        post("/api/v1/character-groups/"
                                                        + groupId
                                                        + "/participants")
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(joinBody("첫친구")))
                                .andExpect(status().isCreated()));
        mvc.perform(
                        delete(
                                        "/api/v1/character-groups/"
                                                + groupId
                                                + "/participants/"
                                                + first.get("participantPublicId"))
                                .with(csrf())
                                .header(TOKEN_HEADER, owner.get("accessToken")))
                .andExpect(status().is4xxClientError());
        assertThat(count("character_participant")).isEqualTo(2);

        mvc.perform(
                        delete("/api/v1/character-participants/me")
                                .with(csrf())
                                .header(TOKEN_HEADER, first.get("accessToken")))
                .andExpect(status().isNoContent());
        assertThat(count("character_participant")).isEqualTo(1);
    }

    @Test
    void 잘못된_입력은_400이다() throws Exception {
        for (String body :
                new String[] {
                    "{\"nickname\":\"a\",\"sport\":\"SKI\",\"answers\":\"" + ANSWERS + "\"}",
                    "{\"nickname\":\"공 백\",\"sport\":\"SKI\",\"answers\":\"" + ANSWERS + "\"}",
                    "{\"nickname\":\"열한글자가넘는닉네임이다\",\"sport\":\"SKI\",\"answers\":\""
                            + ANSWERS
                            + "\"}",
                    "{\"sport\":\"SKI\",\"answers\":\"" + ANSWERS + "\"}",
                    "{\"nickname\":\"정상\",\"sport\":\"SLED\",\"answers\":\"" + ANSWERS + "\"}",
                    "{\"nickname\":\"정상\",\"answers\":\"" + ANSWERS + "\"}",
                    "{\"nickname\":\"정상\",\"sport\":\"SKI\",\"answers\":\"AAA\"}",
                    "{\"nickname\":\"정상\",\"sport\":\"SKI\"}"
                }) {
            mvc.perform(
                            post("/api/v1/character-groups")
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(count("character_group")).isZero();
    }

    @Test
    void 테이블에는_IP_컬럼이_없다() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.columns"
                                        + " WHERE table_schema = DATABASE()"
                                        + " AND table_name IN ('character_group', 'character_participant')"
                                        + " AND LOWER(column_name) LIKE '%ip%'"
                                        + " AND LOWER(column_name) NOT LIKE '%participant%'",
                                Long.class))
                .isZero();
    }

    private Map<?, ?> createGroup(String nickname) throws Exception {
        return read(
                mvc.perform(
                                post("/api/v1/character-groups")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(joinBody(nickname)))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Cache-Control", "no-store")));
    }

    private Map<?, ?> read(ResultActions actions) throws Exception {
        return objectMapper.readValue(
                actions.andReturn().getResponse().getContentAsString(), Map.class);
    }

    private static String joinBody(String nickname) {
        return joinBody(nickname, ANSWERS);
    }

    private static String joinBody(String nickname, String answers) {
        return "{\"nickname\":\""
                + nickname
                + "\",\"sport\":\"BOARD\",\"answers\":\""
                + answers
                + "\"}";
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
