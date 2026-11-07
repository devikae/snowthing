import type { Metadata } from "next";

// canonical은 넣지 않는다. 상세 페이지가 물려받으면 모든 글이 목록을 가리키게 된다.
export const metadata: Metadata = {
  title: "게시판 | 눈팅 SnowThing",
  description: "라이더들이 나누는 설질, 장비, 시즌 이야기. 자유게시판, 익명게시판, 질문게시판을 둘러보세요.",
};

export default function PostsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
