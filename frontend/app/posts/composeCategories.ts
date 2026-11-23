import type { Board } from "../lib/boards";

// 글쓰기와 글 수정이 같이 쓰는 게시판 선택지 (IKA-114). 열린 게시판만 나오고 관리자 전용 게시판(공지 등)은 관리자만 고른다.
export interface ComposeOption {
  code: string;
  label: string;
}

export function composeBoards(boards: Board[], isAdmin: boolean, keepCode?: string, keepLabel?: string): ComposeOption[] {
  const options = boards
    .filter((board) => board.writePermission !== "ADMIN" || isAdmin || board.code === keepCode)
    .map((board) => ({ code: board.code, label: board.name }));
  // 글 수정: 지금 게시판이 닫혀 목록에 없어도 그 자리에 두고 고칠 수 있게 선택지에 남긴다.
  if (keepCode && !options.some((option) => option.code === keepCode)) {
    options.unshift({ code: keepCode, label: keepLabel || keepCode });
  }
  return options;
}

/** 비회원이 게시판을 고르지 않고 들어왔을 때 쓸 게시판. 누구나 쓰는 게시판이 없으면 익명게시판 코드를 둔다. */
export function guestBoardCode(boards: Board[]): string {
  return boards.find((board) => board.writePermission === "ALL")?.code ?? "ANONYMOUS";
}
