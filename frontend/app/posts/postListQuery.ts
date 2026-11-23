import { boardNav, type ActiveNav } from "../components/SiteChrome";
import { findBoard, type Board } from "../lib/boards";

// 게시판 목록 주소(?category=...&page=...)를 API 요청으로 바꾸는 규칙.
// 서버 컴포넌트와 브라우저가 같은 규칙을 써야 서버가 받은 첫 목록을 브라우저가 그대로 이어 쓸 수 있다.

export interface PostItem {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  writerProfileImageUrl?: string | null;
  writerPublicId?: string | null;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  status: string;
  isDeleted: boolean;
  createdAt: string;
  answered?: boolean | null;
}

export interface PostListPage {
  content?: PostItem[];
  pageInfo?: { totalPages?: number };
}

/** 서버가 미리 받은 첫 화면 데이터. query가 지금 주소의 요청과 같을 때만 쓴다. */
export interface InitialPostList {
  query: string;
  posts: PostItem[];
  totalPages: number;
  notices: PostItem[] | null;
}

// 게시판 코드 형식 (IKA-114). 닫힌 게시판도 주소로는 글 목록이 열려야 하므로 열린 목록이 아니라 형식으로 받는다.
// 없는 코드면 백엔드가 빈 목록을 준다.
const CATEGORY_CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,19}$/;

/** 게시판 이름. 닫힌 게시판은 열린 목록에 없으므로 "게시판"으로 보인다. */
export function boardTitle(boards: Board[], categoryCode: string): string {
  if (!categoryCode) return "전체 게시판";
  if (categoryCode === "INQUIRY") return "1:1 문의";
  return findBoard(boards, categoryCode)?.name ?? "게시판";
}

export function boardActiveNav(categoryCode: string): ActiveNav {
  if (!categoryCode) return "posts";
  if (categoryCode === "INQUIRY") return "inquiry";
  return boardNav(categoryCode);
}

export const DEFAULT_SEARCH_TYPE = "TITLE_CONTENT";
export const SEARCH_TYPES = [
  { value: "TITLE_CONTENT", label: "제목+내용" },
  { value: "TITLE", label: "제목" },
  { value: "CONTENT", label: "내용" },
  { value: "WRITER", label: "작성자" },
];

export const NOTICE_QUERY = "categoryCode=NOTICE&page=1&size=3";

export function readPostListParams(get: (key: string) => string | null) {
  const categoryCode = get("category")?.toUpperCase() ?? "";
  const currentCategory = CATEGORY_CODE_PATTERN.test(categoryCode) ? categoryCode : "";
  const currentView = get("view")?.toLowerCase() === "best" ? "best" : "";
  const page = Math.max(0, Number(get("page") ?? "1") - 1 || 0);
  const keyword = get("keyword") ?? "";
  const requestedSearchType = get("searchType") ?? "";
  const searchType = SEARCH_TYPES.some((type) => type.value === requestedSearchType) ? requestedSearchType : DEFAULT_SEARCH_TYPE;
  return { currentCategory, currentView, page, keyword, searchType };
}

export function postListApiQuery({ currentCategory, currentView, page, keyword, searchType }: ReturnType<typeof readPostListParams>) {
  const params = new URLSearchParams({ page: String(page + 1), size: "20" });
  if (currentCategory) params.set("categoryCode", currentCategory);
  if (currentView === "best") params.set("viewType", "BEST");
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
    params.set("searchType", searchType);
  }
  return params.toString();
}
