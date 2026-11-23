package com.ikae.snowthing.domain.post.repository;

import static com.ikae.snowthing.domain.comment.entity.QComment.comment;
import static com.ikae.snowthing.domain.post.entity.QPost.post;
import static com.ikae.snowthing.domain.post.entity.QPostCategory.postCategory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.QMember;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.domain.post.dto.PostViewType;
import com.ikae.snowthing.domain.post.dto.SearchType;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.common.dto.CursorPageResponse.PageInfo;
import com.ikae.snowthing.global.util.CursorUtils;
import com.ikae.snowthing.global.util.CursorUtils.CursorValue;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private static final int BEST_MINIMUM_LIKE_COUNT = 30;
    private static final QMember writer = new QMember("writer");

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
                                visibleCategory(request),
                                writerEq(request.writerPublicId()),
                                bestEligibility(request.viewType()),
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
                                visibleCategory(request),
                                writerEq(request.writerPublicId()),
                                bestEligibility(request.viewType()),
                                searchCondition(request.searchType(), request.keyword()),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .orderBy(getLatestSortOrders())
                        .offset(offset)
                        .limit(size)
                        .fetch();

        // 3. PK 리스트 기반 목록 컬럼 조회. 본문(LOB)은 목록에 쓰지 않으므로 읽지 않는다.
        List<PostListResponse> content =
                selectListRows()
                        .where(
                                post.id.in(postIds),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .orderBy(getLatestSortOrders())
                        .fetch()
                        .stream()
                        .map(PostRepositoryCustomImpl::toListResponse)
                        .toList();

        boolean hasNext = page < totalPages;

        return new CursorPageResponse<>(
                content, PageInfo.ofOffset(page, totalPages, total, hasNext, size));
    }

    @Override
    public CursorPageResponse<PostListResponse> findPostsByCursor(PostSearchRequest request) {
        int size = request.size();
        CursorValue cursorValue = CursorUtils.decode(request.cursor());

        // 1. Keyset Cursor 쿼리 (size + 1 개 요청)
        List<Tuple> rows =
                selectListRows()
                        .where(
                                categoryEq(request.categoryCode()),
                                visibleCategory(request),
                                writerEq(request.writerPublicId()),
                                bestEligibility(request.viewType()),
                                searchCondition(request.searchType(), request.keyword()),
                                cursorCondition(cursorValue),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                post.isDeleted.isFalse())
                        .orderBy(getLatestSortOrders())
                        .limit(size + 1)
                        .fetch();

        boolean hasNext = rows.size() > size;
        List<Tuple> resultRows = hasNext ? rows.subList(0, size) : rows;

        List<PostListResponse> content =
                resultRows.stream().map(PostRepositoryCustomImpl::toListResponse).toList();

        String nextCursor = null;
        if (hasNext && !resultRows.isEmpty()) {
            PostListResponse last = content.get(content.size() - 1);
            nextCursor =
                    CursorUtils.encode(
                            (long) last.likeCount(),
                            last.createdAt(),
                            resultRows.get(resultRows.size() - 1).get(post.id));
        }

        return new CursorPageResponse<>(content, PageInfo.ofCursor(nextCursor, hasNext, size));
    }

    // --- 공통 BooleanExpression 검색 조건절 ---

    private BooleanExpression categoryEq(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) return null;
        return postCategory.code.equalsIgnoreCase(categoryCode);
    }

    /**
     * 1:1 문의를 직접 지정한 조회만 문의를 포함한다. 작성자 조건은 서비스가 붙인다. 그 외 조회는 비공개 카테고리를 제외하고, 게시판을 고르지 않은 전체 목록은 카풀도
     * 제외한다. 키워드 검색에는 카풀이 나온다.
     */
    private BooleanExpression visibleCategory(PostSearchRequest request) {
        if (PostCategoryCodes.isInquiry(request.categoryCode())) return null;
        if (StringUtils.hasText(request.categoryCode()) || StringUtils.hasText(request.keyword())) {
            return postCategory.code.notIn(PostCategoryCodes.HIDDEN_FROM_PUBLIC);
        }
        return postCategory.code.notIn(PostCategoryCodes.HIDDEN_FROM_BOARD_LIST);
    }

    private BooleanExpression writerEq(String writerPublicId) {
        if (!StringUtils.hasText(writerPublicId)) return null;
        return post.member.publicId.eq(writerPublicId);
    }

    private BooleanExpression bestEligibility(PostViewType viewType) {
        if (viewType != PostViewType.BEST) return null;
        return post.likeCount.goe(BEST_MINIMUM_LIKE_COUNT);
    }

    private BooleanExpression searchCondition(SearchType type, String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        // 화면은 검색어만 보내므로 검색 종류가 없으면 제목과 본문에서 찾는다.
        SearchType effectiveType = type != null ? type : SearchType.TITLE_CONTENT;
        return switch (effectiveType) {
            case TITLE -> post.title.containsIgnoreCase(keyword);
                // 익명 글은 닉네임으로 찾히면 작성자가 드러나므로 제외한다.
            case WRITER ->
                    post.member
                            .nickname
                            .containsIgnoreCase(keyword)
                            .and(post.isAnonymous.isFalse())
                            .and(post.member.status.ne(MemberStatus.WITHDRAWN));
            case CONTENT -> contentAsString().containsIgnoreCase(keyword);
            case TITLE_CONTENT ->
                    post.title
                            .containsIgnoreCase(keyword)
                            .or(contentAsString().containsIgnoreCase(keyword));
        };
    }

    // content는 @Lob이라 Hibernate가 lower()를 거부한다. 문자열로 변환해 비교한다.
    private StringExpression contentAsString() {
        return Expressions.stringTemplate("cast({0} as string)", post.content);
    }

    private BooleanExpression cursorCondition(CursorValue cursor) {
        if (cursor == null || cursor.id() == null || cursor.createdAt() == null) return null;

        return post.createdAt
                .lt(cursor.createdAt())
                .or(post.createdAt.eq(cursor.createdAt()).and(post.id.lt(cursor.id())));
    }

    @Override
    public Optional<Post> findAdjacentInCategory(Post base, boolean older) {
        // 목록과 같은 (작성 시각, ID) 순서다. idx_post_category_list 인덱스를 그대로 탄다.
        BooleanExpression position =
                older
                        ? post.createdAt
                                .lt(base.getCreatedAt())
                                .or(
                                        post.createdAt
                                                .eq(base.getCreatedAt())
                                                .and(post.id.lt(base.getId())))
                        : post.createdAt
                                .gt(base.getCreatedAt())
                                .or(
                                        post.createdAt
                                                .eq(base.getCreatedAt())
                                                .and(post.id.gt(base.getId())));
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(post)
                        .where(
                                post.category.id.eq(base.getCategory().getId()),
                                post.isDeleted.isFalse(),
                                post.status.eq(
                                        com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL),
                                position)
                        .orderBy(
                                older
                                        ? getLatestSortOrders()
                                        : new OrderSpecifier<?>[] {
                                            post.createdAt.asc(), post.id.asc()
                                        })
                        .limit(1)
                        .fetchFirst());
    }

    /**
     * 목록에 필요한 컬럼만 고른다. 엔티티 전체를 읽으면 본문(LOB, 최대 10만 자, 예전 글은 base64 사진 포함)이 한 페이지 글 수만큼 함께 실려 와 목록마다
     * 메모리와 DB 전송량을 쓴다.
     */
    private JPAQuery<Tuple> selectListRows() {
        return queryFactory
                .select(
                        post.id,
                        post.publicId,
                        postCategory.name,
                        postCategory.code,
                        post.title,
                        post.isAnonymous,
                        writer,
                        post.writerIp,
                        post.hasImage,
                        post.viewCount,
                        post.commentCount,
                        post.likeCount,
                        post.dislikeCount,
                        post.status,
                        post.isDeleted,
                        post.createdAt)
                .from(post)
                .join(post.category, postCategory)
                .leftJoin(post.member, writer);
    }

    private static PostListResponse toListResponse(Tuple row) {
        return PostListResponse.of(
                row.get(post.publicId),
                row.get(postCategory.name),
                row.get(postCategory.code),
                row.get(post.title),
                Boolean.TRUE.equals(row.get(post.isAnonymous)),
                row.get(writer),
                row.get(post.writerIp),
                Boolean.TRUE.equals(row.get(post.hasImage)),
                intValue(row.get(post.viewCount)),
                intValue(row.get(post.commentCount)),
                intValue(row.get(post.likeCount)),
                intValue(row.get(post.dislikeCount)),
                row.get(post.status),
                Boolean.TRUE.equals(row.get(post.isDeleted)),
                row.get(post.createdAt));
    }

    private static int intValue(Integer value) {
        return value != null ? value : 0;
    }

    private OrderSpecifier<?>[] getLatestSortOrders() {
        return new OrderSpecifier<?>[] {post.createdAt.desc(), post.id.desc()};
    }

    @Override
    public Set<String> findPublicIdsAnsweredByAdmin(Collection<String> postPublicIds) {
        if (postPublicIds.isEmpty()) return Set.of();
        return new HashSet<>(
                queryFactory
                        .select(comment.post.publicId)
                        .distinct()
                        .from(comment)
                        .where(
                                comment.post.publicId.in(postPublicIds),
                                comment.member.role.eq(Role.ROLE_ADMIN),
                                comment.isDeleted.isFalse())
                        .fetch());
    }
}
