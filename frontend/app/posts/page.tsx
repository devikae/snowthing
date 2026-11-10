import type { Metadata } from "next";
import { fetchPublicJson } from "../lib/publicApi";
import { PRIVATE_PAGE_ROBOTS, sectionOpenGraph } from "../lib/siteMetadata";
import PostListClient from "./PostListClient";
import { NOTICE_QUERY, boardConfigs, postListApiQuery, readPostListParams, type InitialPostList, type PostListPage } from "./postListQuery";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

const BOARD_DESCRIPTIONS: Record<string, string> = {
  NOTICE: "눈팅 SnowThing 운영 소식과 이용 안내를 알려 드립니다.",
  FREE: "설질, 장비, 시즌 이야기를 자유롭게 나누는 게시판입니다.",
  ANONYMOUS: "닉네임 없이 편하게 이야기를 나누는 익명게시판입니다.",
  QNA: "장비, 리조트, 기술에 대해 묻고 라이더에게 답을 듣는 질문게시판입니다.",
  FOOD: "리조트 근처 맛집과 식당 정보를 나누는 게시판입니다.",
  CREW: "함께 탈 스키, 보드 동호회 회원을 모집하는 게시판입니다.",
  SEASON_ROOM: "시즌 동안 함께 지낼 시즌방 멤버를 모집하는 게시판입니다.",
  INQUIRY: "운영진에게 1:1로 문의하고 답변을 확인합니다.",
};

// 게시판마다 탭 제목과 설명을 따로 둔다. 레이아웃의 공통 값은 전체 게시판에 쓴다.
export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const raw = await searchParams;
  const value = raw.category;
  const category = (Array.isArray(value) ? value[0] : value) ?? "";
  const board = boardConfigs[category];
  if (!category || !board) return {};
  const metadata = sectionOpenGraph(`${board.name} | 눈팅 SnowThing`, BOARD_DESCRIPTIONS[category] ?? "");
  return category === "INQUIRY" ? { ...metadata, robots: PRIVATE_PAGE_ROBOTS } : metadata;
}

// 검색엔진과 JS를 끈 방문자도 목록을 볼 수 있게 첫 목록과 공지를 서버에서 받아 HTML에 넣는다.
// 1:1 문의는 로그인한 본인 글만 보이므로 서버에서 받지 않고 브라우저가 기존처럼 부른다.
// 검색 결과도 서버에서 받지 않는다. 검색 주소는 검색엔진에 노출할 대상이 아니고, 봇이 임의 검색어로 무거운 검색을 돌리게 하지 않는다.
export default async function PostListPage({ searchParams }: Props) {
  const raw = await searchParams;
  const params = readPostListParams((key) => {
    const value = raw[key];
    return Array.isArray(value) ? (value[0] ?? null) : (value ?? null);
  });
  if (params.currentCategory === "INQUIRY" || params.keyword.trim()) return <PostListClient initial={null} />;

  const query = postListApiQuery(params);
  const [list, notices] = await Promise.all([
    fetchPublicJson<PostListPage>(`/posts?${query}`),
    params.currentCategory === "NOTICE" ? Promise.resolve(null) : fetchPublicJson<PostListPage>(`/posts?${NOTICE_QUERY}`),
  ]);
  const initial: InitialPostList | null = list
    ? {
        query,
        posts: Array.isArray(list.content) ? list.content : [],
        totalPages: Math.max(1, list.pageInfo?.totalPages || 1),
        notices: notices && Array.isArray(notices.content) ? notices.content : null,
      }
    : null;
  return <PostListClient initial={initial} />;
}
