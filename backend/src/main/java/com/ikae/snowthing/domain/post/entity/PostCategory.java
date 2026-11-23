package com.ikae.snowthing.domain.post.entity;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시판 카테고리. 이름, 순서, 공개 상태, 글쓰기 규칙을 관리자가 화면에서 바꾼다 (IKA-114).
 *
 * <p>중고장터, 카풀, 1:1 문의는 전용 화면과 전용 규칙이 있어 관리 대상이 아니다({@link PostCategoryCodes#isManagedBoard}).
 */
@Entity
@Table(name = "post_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCategory {

    /** 064 마이그레이션 전부터 있던 게시판 중 익명을 막는 곳. 빌더 기본값이 마이그레이션 값과 같도록 둔다. */
    private static final Set<String> ANONYMOUS_FORBIDDEN_CODES =
            Set.of(
                    PostCategoryCodes.NOTICE,
                    "CREW",
                    "SEASON_ROOM",
                    "INFO",
                    PostCategoryCodes.INQUIRY);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "short_name", nullable = false, length = 10)
    private String shortName;

    @Column(length = 200)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BoardStatus status;

    @Column(name = "show_on_home", nullable = false)
    private boolean showOnHome;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_permission", nullable = false, length = 10)
    private WritePermission writePermission;

    @Enumerated(EnumType.STRING)
    @Column(name = "anonymous_mode", nullable = false, length = 10)
    private AnonymousMode anonymousMode;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 규칙을 넘기지 않으면 코드로 064 마이그레이션과 같은 기본값을 정한다. 공지는 관리자만, 익명게시판은 누구나 항상 익명, 동호회와 시즌방과 정보와 문의는 익명 금지,
     * 나머지는 회원이 익명을 고른다. 로컬 시드와 테스트가 만든 게시판이 운영 규칙과 같게 하려는 것이다.
     */
    @Builder
    public PostCategory(
            String name,
            String code,
            String shortName,
            String description,
            Integer sortOrder,
            BoardStatus status,
            Boolean showOnHome,
            WritePermission writePermission,
            AnonymousMode anonymousMode) {
        this.name = name;
        this.code = code;
        this.shortName = shortName != null ? shortName : defaultShortName(name);
        this.description = description;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.status = status != null ? status : BoardStatus.OPEN;
        this.showOnHome = showOnHome != null && showOnHome;
        this.writePermission =
                writePermission != null ? writePermission : defaultWritePermission(code);
        this.anonymousMode = anonymousMode != null ? anonymousMode : defaultAnonymousMode(code);
    }

    /** 이름과 코드만 주면 규칙과 순서는 위 기본값을 쓴다. */
    public PostCategory(String name, String code) {
        this(name, code, null, null, null, null, null, null, null);
    }

    private static String defaultShortName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.replace("게시판", "").strip();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed;
    }

    private static WritePermission defaultWritePermission(String code) {
        if (PostCategoryCodes.NOTICE.equalsIgnoreCase(code)) {
            return WritePermission.ADMIN;
        }
        if (PostCategoryCodes.ANONYMOUS.equalsIgnoreCase(code)) {
            return WritePermission.ALL;
        }
        return WritePermission.MEMBER;
    }

    private static AnonymousMode defaultAnonymousMode(String code) {
        if (PostCategoryCodes.ANONYMOUS.equalsIgnoreCase(code)) {
            return AnonymousMode.FORCED;
        }
        if (code != null && ANONYMOUS_FORBIDDEN_CODES.contains(code.toUpperCase())) {
            return AnonymousMode.FORBIDDEN;
        }
        return AnonymousMode.OPTIONAL;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 새 글을 받거나 다른 글을 옮겨 올 수 있는가. 닫히거나 삭제한 게시판은 기존 글만 남는다. */
    public boolean acceptsNewPosts() {
        return status == BoardStatus.OPEN && !isDeleted();
    }

    public boolean isAdminOnly() {
        return writePermission == WritePermission.ADMIN;
    }

    public boolean allowsGuest() {
        return writePermission == WritePermission.ALL;
    }

    public boolean isAlwaysAnonymous() {
        return anonymousMode == AnonymousMode.FORCED;
    }

    public boolean forbidsAnonymous() {
        return anonymousMode == AnonymousMode.FORBIDDEN;
    }

    public void update(
            String name,
            String shortName,
            String description,
            boolean showOnHome,
            WritePermission writePermission,
            AnonymousMode anonymousMode) {
        this.name = name;
        this.shortName = shortName;
        this.description = description;
        this.showOnHome = showOnHome;
        this.writePermission = writePermission;
        this.anonymousMode = anonymousMode;
    }

    public void changeStatus(BoardStatus status) {
        this.status = status;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 복구한 게시판은 닫힌 상태로 돌아온다. 관리자가 확인하고 연다. */
    public void restore() {
        this.deletedAt = null;
        this.status = BoardStatus.HIDDEN;
    }
}
