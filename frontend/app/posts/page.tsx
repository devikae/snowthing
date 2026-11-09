import { fetchPublicJson } from "../lib/publicApi";
import PostListClient from "./PostListClient";
import { NOTICE_QUERY, postListApiQuery, readPostListParams, type InitialPostList, type PostListPage } from "./postListQuery";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

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
