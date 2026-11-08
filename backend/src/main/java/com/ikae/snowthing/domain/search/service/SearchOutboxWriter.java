package com.ikae.snowthing.domain.search.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;

import lombok.RequiredArgsConstructor;

/**
 * 원문 변경과 같은 트랜잭션에서 search_outbox에 이벤트를 쓴다.
 *
 * <p>MANDATORY라서 트랜잭션 밖에서 부르면 바로 실패한다. 원문 변경이 롤백되면 이벤트도 함께 사라지고, 커밋되면 이벤트도 반드시 남는다. 이벤트에는 원문 ID와
 * 버전만 넣고 내용은 넣지 않는다. 처리기는 이벤트가 아니라 현재 원문을 다시 읽어 판단하므로 operation은 기록용이다.
 */
@Component
@RequiredArgsConstructor
public class SearchOutboxWriter {

    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void postChanged(Post post) {
        boolean visible = !post.isDeleted() && post.getStatus() == PostStatus.NORMAL;
        enqueue(
                SearchSourceType.POST,
                post.getId(),
                post.getContentVersion(),
                visible ? UPSERT : DELETE);
    }

    /** 댓글 수정은 flush 뒤에 불러야 증가한 version이 들어간다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commentChanged(Comment comment, boolean deleted) {
        enqueue(
                SearchSourceType.COMMENT,
                comment.getId(),
                comment.getVersion(),
                deleted ? DELETE : UPSERT);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(SearchSourceType type, long sourceId, long version, String operation) {
        enqueueAll(List.of(new NewEvent(type, sourceId, version, operation)));
    }

    /** 대조·재인덱싱이 여러 이벤트를 한 번에 넣을 때 쓴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueAll(List<NewEvent> events) {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.batchUpdate(
                "INSERT INTO search_outbox (source_type, source_id, operation, source_version,"
                        + " status, attempts, created_at, due_at)"
                        + " VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)",
                events,
                200,
                (ps, event) -> {
                    ps.setString(1, event.sourceType().name());
                    ps.setLong(2, event.sourceId());
                    ps.setString(3, event.operation());
                    ps.setLong(4, event.version());
                    ps.setObject(5, now);
                    ps.setObject(6, now);
                });
    }

    public record NewEvent(
            SearchSourceType sourceType, long sourceId, long version, String operation) {}
}
