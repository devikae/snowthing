import type { Metadata } from "next";
import { cache } from "react";
import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { fetchPublicResult, isPublicId } from "../../lib/publicApi";
import { RECENT_WRITE_COOKIE } from "../../lib/recentWrite";
import { detailMetadata, firstImageUrl, plainTextSummary } from "../../lib/sharePreview";
import { renderMarkdownHtml } from "../../lib/markdownHtml.server";
import { hasSessionCookie } from "../../lib/sessionCookie.server";
import PostDetailClient, { type PostDetail } from "./PostDetailClient";

type Props = { params: Promise<{ publicId: string }> };

// 메타데이터와 첫 화면이 같은 글을 따로 부르면 글을 열 때마다 백엔드를 두 번 부른다. 한 요청 안에서는 한 번만 부르게 묶는다.
// 상세 API는 viewed_posts 쿠키에 글 ID가 없으면 조회수를 올린다. 서버 조회는 조회수에 넣지 않게 표시를 붙인다.
const loadPost = cache((publicId: string) =>
  fetchPublicResult<PostDetail>(`/posts/${encodeURIComponent(publicId)}`, { Cookie: `viewed_posts=[${publicId}]` }),
);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  if (!isPublicId(publicId)) return {};
  const { data: post } = await loadPost(publicId);
  if (!post) return {};
  return detailMetadata({
    title: post.title,
    description: plainTextSummary(post.content),
    image: firstImageUrl(post.images, post.content),
    path: `/posts/${encodeURIComponent(publicId)}`,
  });
}

// 검색엔진과 JS를 끈 방문자도 제목과 본문을 볼 수 있게 첫 화면을 서버에서 그린다.
// 쿠키를 넘기지 않으므로 비로그인 기준 응답이다. 조회수가 오르지 않게 viewed_posts 표시를 붙인다.
// 조회수는 기존처럼 브라우저의 상세 요청에서만 오른다. 받지 못하면(1:1 문의, 장애) 브라우저가 기존처럼 부른다.
export default async function PostDetailPage({ params }: Props) {
  const { publicId } = await params;
  if (!isPublicId(publicId)) notFound();
  const { data: initialPost, missing } = await loadPost(publicId);
  // 비로그인 조회가 404면 삭제됐거나 없는 글이라 404 페이지로 답한다. 공유 미리보기와 검색엔진이 빈 글을 담지 않게 한다.
  // 1:1 문의는 작성자에게도 비로그인 조회가 404라서, 로그인 세션 쿠키나 로그인 유지 쿠키가 있으면 기존처럼 브라우저가 다시 부르게 둔다.
  // 방금 이 글을 쓴 브라우저도 브라우저가 다시 부르게 둔다. 비회원 작성자는 세션이 없지만 작성자 기준 응답을 받아야 한다 (IKA-96).
  if (missing && !(await hasSessionCookie()) && !(await wroteRecently(publicId))) notFound();
  if (initialPost?.categoryCode === "CARPOOL") redirect(`/carpool/${publicId}`);
  // 본문도 첫 HTML에 넣는다. 브라우저 뷰어가 준비되면 같은 자리를 이어 받는다.
  const initialBodyHtml = initialPost ? await renderMarkdownHtml(initialPost.content) : null;
  return <PostDetailClient params={params} initialPost={initialPost} initialBodyHtml={initialBodyHtml} />;
}

async function wroteRecently(publicId: string) {
  const store = await cookies();
  return store.get(RECENT_WRITE_COOKIE)?.value === publicId;
}
