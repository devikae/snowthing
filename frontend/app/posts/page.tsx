import type { Metadata } from "next";
import { fetchPublicJson } from "../lib/publicApi";
import { POSTS_DESCRIPTION, PRIVATE_PAGE_ROBOTS, publicPageMetadata, sectionOpenGraph } from "../lib/siteMetadata";
import PostListClient from "./PostListClient";
import { fetchBoards, findBoard } from "../lib/boards";
import { NOTICE_QUERY, postListApiQuery, readPostListParams, type InitialPostList, type PostListPage } from "./postListQuery";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

const INQUIRY_DESCRIPTION = "운영진에게 1:1로 문의하고 답변을 확인합니다.";

// 게시판마다 탭 제목, 설명, canonical을 따로 둔다. canonical은 게시판 주소(/posts?category=코드)로 고정해
// 쪽 번호나 검색어가 붙은 주소가 따로 색인되지 않게 한다. 레이아웃에 두지 않는 이유는 posts/layout.tsx 주석 참고.
export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const raw = await searchParams;
  const value = raw.category;
  const category = (Array.isArray(value) ? value[0] : value) ?? "";
  if (!category) return publicPageMetadata("게시판", POSTS_DESCRIPTION, "/posts");
  // 1:1 문의는 noindex라 canonical을 두지 않는다.
  if (category === "INQUIRY") return { ...sectionOpenGraph("1:1 문의 | 눈팅 SnowThing", INQUIRY_DESCRIPTION), robots: PRIVATE_PAGE_ROBOTS };
  // 닫힌 게시판이나 없는 코드는 열린 목록에 없어 전체 게시판 정보를 쓴다.
  const board = findBoard(await fetchBoards(), category);
  if (!board) return publicPageMetadata("게시판", POSTS_DESCRIPTION, "/posts");
  return publicPageMetadata(board.name, board.description ?? "", `/posts?category=${encodeURIComponent(category)}`);
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
