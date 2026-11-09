package com.ikae.snowthing.domain.post.repository;

import static com.ikae.snowthing.domain.post.entity.QPost.post;
import static com.ikae.snowthing.domain.post.entity.QPostCategory.postCategory;
import static com.ikae.snowthing.domain.post.entity.QPostScrap.postScrap;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.post.entity.PostScrap;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PostScrapRepositoryCustomImpl implements PostScrapRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<PostScrap> findVisibleByMember(
            Long memberId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        return queryFactory
                .selectFrom(postScrap)
                .join(postScrap.post, post)
                .fetchJoin()
                .join(post.category, postCategory)
                .fetchJoin()
                .leftJoin(post.member)
                .fetchJoin()
                .where(
                        postScrap.member.id.eq(memberId),
                        post.isDeleted.isFalse(),
                        post.status.eq(PostStatus.NORMAL),
                        postCategory.code.notIn(PostCategoryCodes.HIDDEN_FROM_BOARD_LIST),
                        cursorCondition(cursorCreatedAt, cursorId))
                .orderBy(postScrap.createdAt.desc(), postScrap.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression cursorCondition(LocalDateTime createdAt, Long id) {
        if (createdAt == null || id == null) return null;
        return postScrap
                .createdAt
                .lt(createdAt)
                .or(postScrap.createdAt.eq(createdAt).and(postScrap.id.lt(id)));
    }
}
