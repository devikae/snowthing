import type { Metadata } from "next";
import { POSTS_DESCRIPTION, sectionOpenGraph } from "../lib/siteMetadata";

// canonical과 og:url은 넣지 않는다. 상세 페이지가 물려받으면 모든 글이 목록을 가리키게 된다.
// 목록(/posts)의 canonical은 posts/page.tsx의 generateMetadata가 넣는다.
export const metadata: Metadata = sectionOpenGraph("게시판 | 눈팅 SnowThing", POSTS_DESCRIPTION);

export default function PostsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
