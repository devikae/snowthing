// 글쓰기와 글 수정이 같이 쓰는 게시판 목록. 공지사항은 관리자만 고른다.
export const COMPOSE_CATEGORIES = [
  { code: "NOTICE", label: "공지사항", adminOnly: true },
  { code: "FREE", label: "자유게시판", adminOnly: false },
  { code: "ANONYMOUS", label: "익명게시판", adminOnly: false },
  { code: "QNA", label: "질문게시판", adminOnly: false },
  { code: "INFO", label: "정보게시판", adminOnly: false },
  { code: "FOOD", label: "리조트 맛집", adminOnly: false },
  { code: "CREW", label: "동호회 모집", adminOnly: false },
  { code: "SEASON_ROOM", label: "시즌방 모집", adminOnly: false },
] as const;
