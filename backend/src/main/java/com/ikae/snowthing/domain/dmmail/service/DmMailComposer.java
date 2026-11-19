package com.ikae.snowthing.domain.dmmail.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMail;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.MailTarget;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.message.dto.MessageMemberResponse;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 도착 메일 문구. 쪽지 본문과 연락처는 넣지 않고 보낸 사람 이름, 쪽지 수, 쪽지함 링크만 넣는다.
 *
 * <p>보낸 사람은 처음 보낸 순서로 최대 두 명까지 이름을 쓰고 나머지는 "외 N명"으로 줄인다. 탈퇴한 회원은 쪽지함과 같이 "탈퇴한 회원"으로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class DmMailComposer {

    static final String SUBJECT = "[SnowThing] 쪽지가 도착했어요";
    private static final int MAX_NAMED_SENDERS = 2;

    private final DmMailProperties properties;

    public DmMail compose(String to, List<MailTarget> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        String headline = headline(targets);
        String inbox = trimSlash(properties.linkBaseUrl()) + "/messages";
        String text =
                headline
                        + "\n\n쪽지함에서 확인해 주세요: "
                        + inbox
                        + "\n\n쪽지 메일 알림은 쪽지함에서 끌 수 있어요. 알림 설정 바꾸기: "
                        + inbox;
        String html =
                "<p>"
                        + HtmlUtils.htmlEscape(headline)
                        + "</p><p><a href=\""
                        + HtmlUtils.htmlEscape(inbox)
                        + "\">쪽지함에서 확인하기</a></p>"
                        + "<p style=\"color:#666;font-size:12px\">쪽지 메일 알림은 쪽지함에서 끌 수 있어요."
                        + " <a href=\""
                        + HtmlUtils.htmlEscape(inbox)
                        + "\">알림 설정 바꾸기</a></p>";
        return new DmMail(to, SUBJECT, text, html);
    }

    static String headline(List<MailTarget> targets) {
        Set<String> senders = new LinkedHashSet<>();
        for (MailTarget target : targets) {
            senders.add(displayName(target));
        }
        List<String> names = List.copyOf(senders);
        StringBuilder who = new StringBuilder();
        for (int i = 0; i < Math.min(names.size(), MAX_NAMED_SENDERS); i++) {
            if (i > 0) {
                who.append(", ");
            }
            who.append(names.get(i)).append("님");
        }
        int others = names.size() - MAX_NAMED_SENDERS;
        if (others > 0) {
            who.append(" 외 ").append(others).append("명");
        }
        if (targets.size() == 1) {
            return who + "에게서 쪽지가 도착했어요.";
        }
        return who + "에게서 쪽지 " + targets.size() + "개가 도착했어요.";
    }

    private static String displayName(MailTarget target) {
        if (MemberStatus.WITHDRAWN.name().equals(target.senderStatus())) {
            return MessageMemberResponse.WITHDRAWN_NICKNAME;
        }
        return target.senderNickname();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
