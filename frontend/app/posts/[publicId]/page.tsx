import type { Metadata } from "next";
import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { fetchPublicResult, isPublicId } from "../../lib/publicApi";
import { detailMetadata, fetchPreview, firstImageUrl, plainTextSummary } from "../../lib/sharePreview";
import PostDetailClient, { type PostDetail } from "./PostDetailClient";

interface PostPreview {
  title: string;
  content: string;
  images: string[] | null;
}

type Props = { params: Promise<{ publicId: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  // 상세 API는 viewed_posts 쿠키에 글 ID가 없으면 조회수를 올린다. 미리보기 조회는 조회수에 넣지 않는다.
  const preview = await fetchPreview(
    `/posts/${encodeURIComponent(publicId)}`,
    (post: PostPreview) => ({ title: post.title, description: plainTextSummary(post.content), image: firstImageUrl(post.images, post.content) }),
    { Cookie: `viewed_posts=[${publicId}]` },
  );
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/posts/${encodeURIComponent(publicId)}` });
}

// 검색엔진과 JS를 끈 방문자도 제목과 본문을 볼 수 있게 첫 화면을 서버에서 그린다.
// 쿠키를 넘기지 않으므로 비로그인 기준 응답이다. 조회수가 오르지 않게 viewed_posts 표시를 붙인다.
// 조회수는 기존처럼 브라우저의 상세 요청에서만 오른다. 받지 못하면(1:1 문의, 장애) 브라우저가 기존처럼 부른다.
export default async function PostDetailPage({ params }: Props) {
  const { publicId } = await params;
  if (!isPublicId(publicId)) notFound();
  const { data: initialPost, missing } = await fetchPublicResult<PostDetail>(`/posts/${publicId}`, { Cookie: `viewed_posts=[${publicId}]` });
  // 비로그인 조회가 404면 삭제됐거나 없는 글이라 404 페이지로 답한다. 공유 미리보기와 검색엔진이 빈 글을 담지 않게 한다.
  // 1:1 문의는 작성자에게도 비로그인 조회가 404라서, 로그인 세션 쿠키가 있으면 기존처럼 브라우저가 다시 부르게 둔다.
  if (missing && !(await hasSessionCookie())) notFound();
  if (initialPost?.categoryCode === "CARPOOL") redirect(`/carpool/${publicId}`);
  return <PostDetailClient params={params} initialPost={initialPost} />;
}

async function hasSessionCookie() {
  const store = await cookies();
  return store.has("JSESSIONID") || store.has("SESSION");
}
