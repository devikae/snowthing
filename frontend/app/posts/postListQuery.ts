import type { ActiveNav } from "../components/SiteChrome";

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

interface BoardConfig {
  name: string;
  navKey: ActiveNav;
}

export const boardConfigs: Record<string, BoardConfig> = {
  "": { name: "전체 게시판", navKey: "posts" },
  NOTICE: { name: "공지사항", navKey: "notice" },
  FREE: { name: "자유게시판", navKey: "free" },
  ANONYMOUS: { name: "익명게시판", navKey: "anonymous" },
  QNA: { name: "질문게시판", navKey: "gear" },
  FOOD: { name: "리조트 맛집", navKey: "food" },
  CREW: { name: "동호회 모집", navKey: "crew" },
  SEASON_ROOM: { name: "시즌방 모집", navKey: "seasonRoom" },
  INQUIRY: { name: "1:1 문의", navKey: "inquiry" },
};

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
  const currentCategory = boardConfigs[categoryCode] ? categoryCode : "";
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
