import type { Metadata } from "next";
import { detailMetadata, fetchPreview, firstImageUrl, plainTextSummary } from "../../lib/sharePreview";
import PostDetailClient from "./PostDetailClient";

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

export default function PostDetailPage({ params }: Props) {
  return <PostDetailClient params={params} />;
}
