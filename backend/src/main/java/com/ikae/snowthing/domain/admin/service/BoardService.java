package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminBoardRequest;
import com.ikae.snowthing.domain.admin.dto.AdminBoardResponse;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.post.dto.BoardResponse;
import com.ikae.snowthing.domain.post.entity.AnonymousMode;
import com.ikae.snowthing.domain.post.entity.BoardStatus;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.post.entity.WritePermission;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

import lombok.RequiredArgsConstructor;

/**
 * 게시판 목록 조회와 관리자 게시판 관리 (IKA-114). 설계: docs/conception/037_board_admin/01_design.md
 *
 * <p>중고장터, 카풀, 1:1 문의는 관리 대상이 아니라 공개 목록과 관리자 목록 모두에서 빠지고 바꿀 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,19}$");
    // BEST는 글 목록 보기 종류(인기글) 이름과 겹쳐 헷갈리므로 막는다.
    private static final Set<String> RESERVED_CODES =
            Set.of(
                    PostCategoryCodes.MARKET,
                    PostCategoryCodes.CARPOOL,
                    PostCategoryCodes.INQUIRY,
                    "BEST");
    private static final int SORT_GAP = 10;

    private final PostCategoryRepository categoryRepository;
    private final AdminActionLogger actionLogger;

    /** 메뉴, 홈 탭, 글쓰기에 보일 열린 게시판. */
    public List<BoardResponse> getOpenBoards() {
        return managedBoards().stream()
                .filter(PostCategory::acceptsNewPosts)
                .map(BoardResponse::from)
                .toList();
    }

    /** 관리자 화면 목록. 닫힌 게시판과 삭제한 게시판도 준다. */
    public List<AdminBoardResponse> getAdminBoards() {
        return managedBoards().stream().map(AdminBoardResponse::from).toList();
    }

    @Transactional
    public AdminBoardResponse create(AdminBoardRequest request) {
        String code = request.code() == null ? "" : request.code().strip();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new CustomAuthException(ErrorCode.BOARD_CODE_INVALID);
        }
        if (RESERVED_CODES.contains(code) || categoryRepository.findByCode(code).isPresent()) {
            throw new CustomAuthException(ErrorCode.BOARD_CODE_DUPLICATED);
        }
        validateRules(request.writePermission(), request.anonymousMode());

        int lastOrder =
                managedBoards().stream().mapToInt(PostCategory::getSortOrder).max().orElse(0);
        PostCategory board =
                PostCategory.builder()
                        .name(request.name().strip())
                        .code(code)
                        .shortName(request.shortName().strip())
                        .description(blankToNull(request.description()))
                        .sortOrder(lastOrder + SORT_GAP)
                        .status(BoardStatus.OPEN)
                        .showOnHome(request.showOnHome())
                        .writePermission(request.writePermission())
                        .anonymousMode(request.anonymousMode())
                        .build();
        try {
            // 두 관리자가 같은 코드를 동시에 만들면 UNIQUE 제약이 막는다. 화면에는 중복 코드로 알린다.
            categoryRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException e) {
            throw new CustomAuthException(ErrorCode.BOARD_CODE_DUPLICATED);
        }
        actionLogger.record(AdminActionType.BOARD_CREATE, AdminTargetType.BOARD, code, null);
        return AdminBoardResponse.from(board);
    }

    @Transactional
    public AdminBoardResponse update(String code, AdminBoardRequest request) {
        PostCategory board = managedBoard(code);
        validateRules(request.writePermission(), request.anonymousMode());
        board.update(
                request.name().strip(),
                request.shortName().strip(),
                blankToNull(request.description()),
                request.showOnHome(),
                request.writePermission(),
                request.anonymousMode());
        actionLogger.record(
                AdminActionType.BOARD_UPDATE, AdminTargetType.BOARD, board.getCode(), null);
        return AdminBoardResponse.from(board);
    }

    @Transactional
    public AdminBoardResponse changeStatus(String code, BoardStatus status) {
        PostCategory board = managedBoard(code);
        if (board.isDeleted()) {
            throw new CustomAuthException(ErrorCode.BOARD_NOT_MANAGED);
        }
        board.changeStatus(status);
        actionLogger.record(
                AdminActionType.BOARD_STATUS,
                AdminTargetType.BOARD,
                board.getCode(),
                status.name());
        return AdminBoardResponse.from(board);
    }

    /** 바로 위나 아래 게시판(삭제한 게시판 제외)과 순서를 맞바꾼다. 끝에 있으면 그대로 둔다. */
    @Transactional
    public List<AdminBoardResponse> move(String code, boolean up) {
        PostCategory board = managedBoard(code);
        List<PostCategory> active =
                managedBoards().stream().filter(category -> !category.isDeleted()).toList();
        int index = active.indexOf(board);
        int neighborIndex = up ? index - 1 : index + 1;
        if (index < 0 || neighborIndex < 0 || neighborIndex >= active.size()) {
            return getAdminBoards();
        }
        PostCategory neighbor = active.get(neighborIndex);
        int boardOrder = board.getSortOrder();
        int neighborOrder = neighbor.getSortOrder();
        if (boardOrder == neighborOrder) {
            // 순서 값이 같으면 ID 순으로 놓였던 것이다. 맞바꿔도 그대로라 한 칸 차이를 만든다.
            neighborOrder = up ? boardOrder - 1 : boardOrder + 1;
        }
        board.changeSortOrder(neighborOrder);
        neighbor.changeSortOrder(boardOrder);
        actionLogger.record(
                AdminActionType.BOARD_MOVE,
                AdminTargetType.BOARD,
                board.getCode(),
                up ? "UP" : "DOWN");
        return getAdminBoards();
    }

    @Transactional
    public void delete(String code) {
        PostCategory board = managedBoard(code);
        if (board.isDeleted()) {
            return;
        }
        board.delete(LocalDateTime.now());
        actionLogger.record(
                AdminActionType.BOARD_DELETE, AdminTargetType.BOARD, board.getCode(), null);
    }

    @Transactional
    public AdminBoardResponse restore(String code) {
        PostCategory board = managedBoard(code);
        if (board.isDeleted()) {
            board.restore();
            actionLogger.record(
                    AdminActionType.BOARD_RESTORE, AdminTargetType.BOARD, board.getCode(), null);
        }
        return AdminBoardResponse.from(board);
    }

    private List<PostCategory> managedBoards() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(category -> PostCategoryCodes.isManagedBoard(category.getCode()))
                .toList();
    }

    private PostCategory managedBoard(String code) {
        return categoryRepository
                .findByCode(code)
                .filter(category -> PostCategoryCodes.isManagedBoard(category.getCode()))
                .orElseThrow(() -> new CustomAuthException(ErrorCode.BOARD_NOT_MANAGED));
    }

    /** 비회원 글은 익명 비밀번호로 관리하므로, 누구나 쓰는 게시판은 항상 익명이어야 한다. */
    private static void validateRules(WritePermission permission, AnonymousMode anonymousMode) {
        if (permission == WritePermission.ALL && anonymousMode != AnonymousMode.FORCED) {
            throw new CustomAuthException(ErrorCode.BOARD_RULE_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
