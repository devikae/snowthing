import type { Metadata } from "next";
import { sectionOpenGraph } from "../lib/siteMetadata";
// 카풀 화면에서만 쓰는 스타일이라 루트가 아닌 카풀 레이아웃에서 불러온다. 다른 화면 첫 CSS에 섞이지 않는다.
import "../carpool.css";

// canonical과 og:url은 넣지 않는다. 상세 페이지가 물려받으면 모든 글이 목록을 가리키게 된다.
export const metadata: Metadata = sectionOpenGraph(
  "카풀과 동행 | 눈팅 SnowThing",
  "출발지와 리조트가 맞는 카풀과 동행을 찾아보세요.",
);

export default function CarpoolLayout({ children }: { children: React.ReactNode }) {
  return children;
}
