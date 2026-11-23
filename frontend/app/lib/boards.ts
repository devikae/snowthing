import { API_V1_URL } from "./api";

// 게시판 목록 (IKA-114). 관리자가 /admin/boards에서 추가하고 열고 닫고 순서를 정한다.
// 메뉴, 홈 탭, 글 목록, 글쓰기, 게시판 설명, 사이트맵이 모두 이 목록을 쓴다.

export type WritePermission = "ALL" | "MEMBER" | "ADMIN";
export type AnonymousMode = "FORCED" | "OPTIONAL" | "FORBIDDEN";

export interface Board {
  code: string;
  name: string;
  shortName: string;
  description: string | null;
  showOnHome: boolean;
  writePermission: WritePermission;
  anonymousMode: AnonymousMode;
}

// 백엔드에 닿지 못할 때 쓰는 기본 목록. 064 마이그레이션 직후의 게시판과 같다.
// 관리자가 새로 추가한 게시판만 잠깐 빠지고 나머지 화면은 그대로 그려진다.
export const DEFAULT_BOARDS: Board[] = [
  board("NOTICE", "공지사항", "공지", "눈팅 SnowThing 운영 소식과 이용 안내를 알려 드립니다.", "ADMIN", "FORBIDDEN"),
  board("FREE", "자유게시판", "자유", "설질, 장비, 시즌 이야기를 자유롭게 나누는 게시판입니다.", "MEMBER", "OPTIONAL"),
  board("ANONYMOUS", "익명게시판", "익명", "닉네임 없이 편하게 이야기를 나누는 익명게시판입니다.", "ALL", "FORCED"),
  board("QNA", "질문게시판", "질문", "장비, 리조트, 기술에 대해 묻고 라이더에게 답을 듣는 질문게시판입니다.", "MEMBER", "OPTIONAL"),
  board("INFO", "정보게시판", "정보", "보드, 스키 잘 타는 방법과 리조트 개장 같은 소식을 나누는 정보게시판입니다.", "MEMBER", "FORBIDDEN"),
  board("FOOD", "리조트 맛집", "맛집", "리조트 근처 맛집과 식당 정보를 나누는 게시판입니다.", "MEMBER", "OPTIONAL"),
  board("CREW", "동호회 모집", "동호회", "함께 탈 스키, 보드 동호회 회원을 모집하는 게시판입니다.", "MEMBER", "FORBIDDEN"),
  board("SEASON_ROOM", "시즌방 모집", "시즌방", "시즌 동안 함께 지낼 시즌방 멤버를 모집하는 게시판입니다.", "MEMBER", "FORBIDDEN"),
];

function board(code: string, name: string, shortName: string, description: string, writePermission: WritePermission, anonymousMode: AnonymousMode): Board {
  return { code, name, shortName, description, showOnHome: true, writePermission, anonymousMode };
}

// 관리자가 바꾼 내용이 1분 안에 보이도록 60초 동안 같은 응답을 쓴다. 게시판마다 바로 반영하려면 캐시 무효화 경로가 필요해 두지 않았다.
const BOARDS_REVALIDATE_SECONDS = 60;
const BOARDS_FETCH_TIMEOUT_MS = 2000;

/** 서버 컴포넌트 전용. 실패하면 기본 목록을 돌려준다. */
export async function fetchBoards(): Promise<Board[]> {
  try {
    const response = await fetch(`${API_V1_URL}/public/boards`, {
      next: { revalidate: BOARDS_REVALIDATE_SECONDS },
      signal: AbortSignal.timeout(BOARDS_FETCH_TIMEOUT_MS),
    });
    if (!response.ok) return DEFAULT_BOARDS;
    const boards = (await response.json()) as Board[];
    return Array.isArray(boards) && boards.length > 0 ? boards : DEFAULT_BOARDS;
  } catch {
    return DEFAULT_BOARDS;
  }
}

export function findBoard(boards: Board[], code: string): Board | undefined {
  return boards.find((item) => item.code === code);
}

/**
 * 글 목록의 분류 표시. 공지는 눈에 띄게 전체 이름(공지사항)을 쓰고, 나머지는 짧은 이름을 쓴다.
 * 닫힌 게시판 글은 목록에 없으므로 글에 실린 이름에서 "게시판"을 뺀다.
 */
export function categoryLabel(boards: Board[], categoryCode: string, categoryName: string): string {
  if (categoryCode === "NOTICE") return "공지사항";
  return findBoard(boards, categoryCode)?.shortName ?? categoryName.replace(/게시판|\s+/g, "");
}

/** 이 사람이 이 게시판에 새 글을 쓸 수 있는가. 비회원 여부는 글쓰기 화면이 따로 본다. */
export function canWriteTo(board: Board | undefined, isAdmin: boolean): boolean {
  return !!board && (board.writePermission !== "ADMIN" || isAdmin);
}
