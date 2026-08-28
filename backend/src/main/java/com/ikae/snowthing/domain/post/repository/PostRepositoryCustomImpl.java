package com.ikae.snowthing.domain.post.repository;

import static com.ikae.snowthing.domain.post.entity.QPost.post;
import static com.ikae.snowthing.domain.post.entity.QPostCategory.postCategory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.domain.post.dto.SearchType;
import com.ikae.snowthing.domain.post.dto.SortType;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.common.dto.CursorPageResponse.PageInfo;
import com.ikae.snowthing.global.util.CursorUtils;
import com.ikae.snowthing.global.util.CursorUtils.CursorValue;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorPageResponse<PostListResponse> findPostsByOffset(PostSearchRequest request) {
        int page = (request.page() != null && request.page() > 0) ? request.page() : 1;
        int size = request.size();
        int offset = (page - 1) * size;

        // 1. 전체 개수 Count 쿼리
        Long totalCount =
                queryFactory
                        .select(post.count())
                        .from(post)
                        .join(post.category, postCategory)
                        .where(
                                categoryEq(request.categoryCode()),
                                searchCondition(request.searchType(), request.keyword()),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .fetchOne();

        long total = totalCount != null ? totalCount : 0L;
        int totalPages = (int) Math.ceil((double) total / size);

        if (total == 0 || offset >= total) {
            return new CursorPageResponse<>(
                    List.of(), PageInfo.ofOffset(page, totalPages, total, false, size));
        }

        // 2. 커버링 인덱스 (Deferred Join) 개념의 PK 선별 쿼리
        List<Long> postIds =
                queryFactory
                        .select(post.id)
                        .from(post)
                        .join(post.category, postCategory)
                        .where(
                                categoryEq(request.categoryCode()),
                                searchCondition(request.searchType(), request.keyword()),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .orderBy(getSortOrders(request.sortType()))
                        .offset(offset)
                        .limit(size)
                        .fetch();

        // 3. PK 리스트 기반 본문 데이터 Fetch
        List<Post> posts =
                queryFactory
                        .selectFrom(post)
                        .join(post.category, postCategory)
                        .fetchJoin()
                        .leftJoin(post.member)
                        .fetchJoin()
                        .where(post.id.in(postIds))
                        .orderBy(getSortOrders(request.sortType()))
                        .fetch();

        List<PostListResponse> content = posts.stream().map(PostListResponse::from).toList();

        boolean hasNext = page < totalPages;

        return new CursorPageResponse<>(
                content, PageInfo.ofOffset(page, totalPages, total, hasNext, size));
    }

    @Override
    public CursorPageResponse<PostListResponse> findPostsByCursor(PostSearchRequest request) {
        int size = request.size();
        CursorValue cursorValue = CursorUtils.decode(request.cursor());

        // 1. Keyset Cursor 쿼리 (size + 1 개 요청)
        List<Post> posts =
                queryFactory
                        .selectFrom(post)
                        .join(post.category, postCategory)
                        .fetchJoin()
                        .leftJoin(post.member)
                        .fetchJoin()
                        .where(
                                categoryEq(request.categoryCode()),
                                searchCondition(request.searchType(), request.keyword()),
                                cursorCondition(request.sortType(), cursorValue),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .orderBy(getSortOrders(request.sortType()))
                        .limit(size + 1)
                        .fetch();

        boolean hasNext = posts.size() > size;
        List<Post> resultPosts = hasNext ? posts.subList(0, size) : posts;

        List<PostListResponse> content = resultPosts.stream().map(PostListResponse::from).toList();

        String nextCursor = null;
        if (hasNext && !resultPosts.isEmpty()) {
            Post lastPost = resultPosts.get(resultPosts.size() - 1);
            nextCursor =
                    CursorUtils.encode(
                            (long) lastPost.getLikeCount(),
                            lastPost.getCreatedAt(),
                            lastPost.getId());
        }

        return new CursorPageResponse<>(content, PageInfo.ofCursor(nextCursor, hasNext, size));
    }

    // --- 공통 BooleanExpression 검색 조건절 ---

    private BooleanExpression categoryEq(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) return null;
        return postCategory.code.equalsIgnoreCase(categoryCode);
    }

    private BooleanExpression searchCondition(SearchType type, String keyword) {
        if (!StringUtils.hasText(keyword) || type == null) return null;
        return switch (type) {
            case TITLE -> post.title.containsIgnoreCase(keyword);
            case WRITER -> post.member.nickname.containsIgnoreCase(keyword);
            case CONTENT -> post.content.containsIgnoreCase(keyword);
            case TITLE_CONTENT ->
                    post.title
                            .containsIgnoreCase(keyword)
                            .or(post.content.containsIgnoreCase(keyword));
        };
    }

    private BooleanExpression cursorCondition(SortType sortType, CursorValue cursor) {
        if (cursor == null || cursor.id() == null) return null;

        if (sortType == SortType.POPULAR) {
            // 인기순 복합 커서: (likeCount < lastLike) OR (likeCount == lastLike AND id < lastId)
            return post.likeCount
                    .lt(cursor.likeCount().intValue())
                    .or(
                            post.likeCount
                                    .eq(cursor.likeCount().intValue())
                                    .and(post.id.lt(cursor.id())));
        } else {
            // 최신순 커서: id < lastId
            return post.id.lt(cursor.id());
        }
    }

    private OrderSpecifier<?>[] getSortOrders(SortType sortType) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        if (sortType == SortType.POPULAR) {
            orders.add(post.likeCount.desc());
        }
        orders.add(post.id.desc());
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
