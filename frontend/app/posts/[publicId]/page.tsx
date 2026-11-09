import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { fetchPublicJson, isPublicId } from "../../lib/publicApi";
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
  const initialPost = isPublicId(publicId)
    ? await fetchPublicJson<PostDetail>(`/posts/${publicId}`, { Cookie: `viewed_posts=[${publicId}]` })
    : null;
  if (initialPost?.categoryCode === "CARPOOL") redirect(`/carpool/${publicId}`);
  return <PostDetailClient params={params} initialPost={initialPost} />;
}
