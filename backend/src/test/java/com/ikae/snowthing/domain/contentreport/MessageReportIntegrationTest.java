package com.ikae.snowthing.domain.contentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportPageResponse;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse.TargetState;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.service.ContentModerationService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.message.entity.DirectMessage;
import com.ikae.snowthing.domain.message.repository.DirectMessageRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 받은 쪽지 신고 (IKA-123). 기준 문서: docs/conception/039_message_report/01_design.md
 *
 * <p>받은 회원만 신고하고, 관리자는 신고된 쪽지 본문을 보고 처리 완료로만 닫는다. 테스트 트랜잭션 안에서 돌고 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageReportIntegrationTest {

    private static final String REPORTS = "/api/v1/reports";
    private static final String ADMIN_REPORTS = "/api/v1/admin/reports";
    private static final String CONTENT = "불쾌한 쪽지 본문";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberRepository memberRepository;
    @Autowired private DirectMessageRepository directMessageRepository;
    @Autowired private ContentModerationService moderationService;

    private Member sender;
    private Member receiver;
    private Member stranger;
    private Member admin;
    private Long messageId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        sender = saveMember(unique, "s", Role.ROLE_USER);
        receiver = saveMember(unique, "r", Role.ROLE_USER);
        stranger = saveMember(unique, "x", Role.ROLE_USER);
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        messageId =
                directMessageRepository
                        .saveAndFlush(new DirectMessage(sender, receiver, null, CONTENT))
                        .getId();
    }

    @Test
    @DisplayName("받은 회원이 신고하면 MESSAGE 신고가 남는다")
    void receiverCanReport() throws Exception {
        report(receiver, messageId.toString()).andExpect(status().isNoContent());

        assertThat(reportCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("보낸 회원, 제3자, 없는 쪽지 번호는 404이고 신고는 남지 않는다")
    void senderAndStrangerCannotReport() throws Exception {
        report(sender, messageId.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_REPORT_001"));
        report(stranger, messageId.toString()).andExpect(status().isNotFound());
        report(receiver, "not-a-number").andExpect(status().isNotFound());
        report(receiver, String.valueOf(Long.MAX_VALUE)).andExpect(status().isNotFound());

        assertThat(reportCount()).isZero();
    }

    @Test
    @DisplayName("비회원은 쪽지를 신고할 수 없어 401이다")
    void guestCannotReport() throws Exception {
        mockMvc.perform(
                        post(REPORTS)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(body(messageId.toString()))))
                .andExpect(status().isUnauthorized());

        assertThat(reportCount()).isZero();
    }

    @Test
    @DisplayName("내 쪽지함에서 지운 쪽지는 신고할 수 없다")
    void deletedByReceiverIsNotFound() throws Exception {
        deleteByReceiver();

        report(receiver, messageId.toString()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("같은 쪽지를 다시 신고하면 409다")
    void duplicateIsConflict() throws Exception {
        report(receiver, messageId.toString()).andExpect(status().isNoContent());
        entityManager.flush();

        report(receiver, messageId.toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_REPORT_002"));
    }

    @Test
    @DisplayName("관리자 목록에 쪽지 본문과 보낸 회원이 나오고, 받은 회원이 지운 뒤에도 남는다")
    void adminSeesReportedMessage() throws Exception {
        report(receiver, messageId.toString()).andExpect(status().isNoContent());
        deleteByReceiver();
        entityManager.clear();

        AdminContentReportResponse row = adminRow(reportId());

        assertThat(row.targetContent()).isEqualTo(CONTENT);
        assertThat(row.targetPreview()).isEqualTo(CONTENT);
        assertThat(row.senderPublicId()).isEqualTo(sender.getPublicId());
        assertThat(row.senderNickname()).isEqualTo(sender.getNickname());
        assertThat(row.messageSentAt()).isNotNull();
        assertThat(row.postPublicId()).isNull();
        assertThat(row.targetState()).isEqualTo(TargetState.NORMAL);
    }

    @Test
    @DisplayName("쪽지 신고에 임시조치, 복구, 삭제는 409이고 처리 완료만 받으며 알림은 보내지 않는다")
    void onlyDismissIsAllowed() throws Exception {
        report(receiver, messageId.toString()).andExpect(status().isNoContent());
        entityManager.flush();
        Long reportId = reportId();

        act(reportId, "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_REPORT_004"));
        act(reportId, "{\"action\":\"RESTORE\"}").andExpect(status().isConflict());
        act(reportId, "{\"action\":\"DELETE\"}").andExpect(status().isConflict());

        act(reportId, "{\"action\":\"DISMISS\"}").andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM content_report WHERE content_report_id = ?",
                                String.class,
                                reportId))
                .isEqualTo("RESOLVED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notification WHERE receiver_id IN (?, ?)"
                                        + " AND type = 'MODERATION_RESULT'",
                                Integer.class,
                                sender.getId(),
                                receiver.getId()))
                .isZero();
        // 처리 완료해도 쪽지는 그대로 남는다.
        assertThat(directMessageRepository.findById(messageId)).isPresent();
    }

    private void deleteByReceiver() {
        directMessageRepository
                .findById(messageId)
                .orElseThrow()
                .deleteBy(receiver.getId(), LocalDateTime.now());
        entityManager.flush();
    }

    private AdminContentReportResponse adminRow(Long reportId) {
        for (int page = 0; ; page++) {
            AdminContentReportPageResponse response =
                    moderationService.list(ReportStatus.PENDING, page);
            for (AdminContentReportResponse row : response.reports()) {
                if (row.reportId().equals(reportId)) {
                    return row;
                }
            }
            if (page + 1 >= response.totalPages()) {
                throw new AssertionError("신고가 관리자 목록에 없다: " + reportId);
            }
        }
    }

    private Long reportId() {
        return jdbc.queryForObject(
                "SELECT content_report_id FROM content_report"
                        + " WHERE target_type = 'MESSAGE' AND target_id = ? AND reporter_id = ?",
                Long.class,
                messageId,
                receiver.getId());
    }

    private int reportCount() {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_report WHERE target_type = 'MESSAGE'"
                        + " AND target_id = ?",
                Integer.class,
                messageId);
    }

    private ResultActions report(Member member, String targetId) throws Exception {
        return mockMvc.perform(
                post(REPORTS)
                        .with(csrf())
                        .with(user(new CustomUserDetails(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(body(targetId))));
    }

    private ResultActions act(Long reportId, String body) throws Exception {
        return mockMvc.perform(
                post(ADMIN_REPORTS + "/{id}/actions", reportId)
                        .with(csrf())
                        .with(user(new CustomUserDetails(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static Map<String, Object> body(String targetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", "MESSAGE");
        body.put("targetId", targetId);
        body.put("reason", "OTHER");
        body.put("detail", "욕설이 담긴 쪽지");
        return body;
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Member saveMember(String unique, String suffix, Role role) {
        return memberRepository.saveAndFlush(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(unique + "-" + suffix + "@example.com")
                        .password("encoded")
                        .nickname((suffix + unique).substring(0, 12))
                        .role(role)
                        .build());
    }
}
