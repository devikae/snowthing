package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMail;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.MailTarget;
import com.ikae.snowthing.domain.dmmail.service.DmMailComposer;

class DmMailComposerTest {

    private final DmMailComposer composer =
            new DmMailComposer(
                    new DmMailProperties(
                            true,
                            false,
                            null,
                            null,
                            null,
                            null,
                            0,
                            null,
                            null,
                            0,
                            null,
                            "https://snowthing.org/",
                            null));

    @Test
    @DisplayName("쪽지 한 개면 보낸 사람과 도착 문구만 쓴다")
    void single() {
        DmMail mail = composer.compose("r@test.org", List.of(target(1, "눈사람", "ACTIVE")));

        assertThat(mail.text()).startsWith("눈사람님에게서 쪽지가 도착했어요.");
        assertThat(mail.text()).contains("https://snowthing.org/messages");
        assertThat(mail.subject()).isEqualTo("[SnowThing] 쪽지가 도착했어요");
    }

    @Test
    @DisplayName("같은 사람이 여러 개 보내면 이름은 한 번, 개수를 쓴다")
    void sameSenderMany() {
        DmMail mail =
                composer.compose(
                        "r@test.org",
                        List.of(target(1, "눈사람", "ACTIVE"), target(2, "눈사람", "ACTIVE")));

        assertThat(mail.text()).startsWith("눈사람님에게서 쪽지 2개가 도착했어요.");
    }

    @Test
    @DisplayName("탈퇴한 보낸 사람은 쪽지함과 같이 탈퇴한 회원으로 쓴다")
    void withdrawnSender() {
        DmMail mail =
                composer.compose("r@test.org", List.of(target(1, "withdrawn_7", "WITHDRAWN")));

        assertThat(mail.text()).startsWith("탈퇴한 회원님에게서");
    }

    @Test
    @DisplayName("HTML 본문은 닉네임을 이스케이프한다")
    void htmlEscapesNickname() {
        DmMail mail = composer.compose("r@test.org", List.of(target(1, "<b>보더</b>", "ACTIVE")));

        assertThat(mail.html()).contains("&lt;b&gt;보더&lt;/b&gt;").doesNotContain("<b>보더");
    }

    private static MailTarget target(long id, String nickname, String status) {
        return new MailTarget(
                id,
                LocalDateTime.now(),
                false,
                nickname,
                status,
                "r@test.org",
                LocalDateTime.now(),
                "ACTIVE",
                true);
    }
}
