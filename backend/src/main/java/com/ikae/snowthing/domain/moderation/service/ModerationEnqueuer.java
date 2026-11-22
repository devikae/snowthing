package com.ikae.snowthing.domain.moderation.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;

import lombok.RequiredArgsConstructor;

/**
 * 글, 댓글, 장터 글, 카풀 글, 설질 제보를 쓰거나 고칠 때 같은 트랜잭션에서 검사 대기 줄을 넣는다 (IKA-80).
 *
 * <p>MANDATORY라서 트랜잭션 밖에서 부르면 바로 실패한다. 원문 저장이 롤백되면 줄도 함께 사라지고, 커밋되면 줄도 반드시 남는다. 서버가 재시작돼도 검사가 빠지지
 * 않는다(검색 색인 outbox와 같은 방식). 줄에는 내용 해시만 넣고 본문은 넣지 않는다.
 *
 * <p>넣지 않는 경우
 *
 * <ul>
 *   <li>기능이 꺼져 있을 때 ({@code snowthing.moderation.enabled=false}, 기본).
 *   <li>관리자가 쓴 글 (IKA-96 금칙어 검사와 같다).
 *   <li>1:1 문의 글과 그 댓글. 작성자와 운영자만 보는 대화라 쪽지처럼 외부로 보내지 않는다.
 * </ul>
 *
 * <p>AI 회원은 다른 회원과 똑같이 넣는다. 작성자 종류로 나누는 분기는 없다.
 */
@Component
@RequiredArgsConstructor
public class ModerationEnqueuer {

    private final ContentModerationRepository repository;
    private final ModerationProperties properties;
    private final Clock clock;

    /** 게시판 글. 장터와 카풀은 각자 메서드를 쓴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postWritten(Post post) {
        postWritten(post, ModerationTargetType.POST);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void marketWritten(Post post) {
        postWritten(post, ModerationTargetType.MARKET);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void carpoolWritten(Post post) {
        postWritten(post, ModerationTargetType.CARPOOL);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commentWritten(Comment comment) {
        if (!properties.enabled()
                || isAdmin(comment.getMember())
                || PostCategoryCodes.isInquiry(comment.getPost())) {
            return;
        }
        insert(
                ModerationTargetType.COMMENT,
                comment.getId(),
                comment.getMember(),
                comment.getWriterIp(),
                "",
                comment.getContent());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resortReportWritten(ResortReport report) {
        if (!properties.enabled() || isAdmin(report.getAuthor())) {
            return;
        }
        insert(
                ModerationTargetType.RESORT_REPORT,
                report.getId(),
                report.getAuthor(),
                null,
                "",
                report.getContent());
    }

    private void postWritten(Post post, ModerationTargetType type) {
        if (!properties.enabled()
                || isAdmin(post.getMember())
                || PostCategoryCodes.isInquiry(post)) {
            return;
        }
        insert(
                type,
                post.getId(),
                post.getMember(),
                post.getWriterIp(),
                post.getTitle(),
                post.getContent());
    }

    private void insert(
            ModerationTargetType type,
            Long targetId,
            Member member,
            String writerIp,
            String title,
            String body) {
        repository.insert(
                type,
                targetId,
                member == null ? null : member.getId(),
                writerIp,
                ModerationTextRules.contentHash(title, body),
                LocalDateTime.now(clock));
    }

    private static boolean isAdmin(Member member) {
        return member != null && member.getRole() == Role.ROLE_ADMIN;
    }
}
