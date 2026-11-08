import type { Metadata } from "next";
import { sectionOpenGraph } from "../lib/siteMetadata";

// canonical과 og:url은 넣지 않는다. 상세 페이지가 물려받으면 모든 글이 목록을 가리키게 된다.
export const metadata: Metadata = sectionOpenGraph(
  "카풀과 동행 | 눈팅 SnowThing",
  "출발지와 리조트가 맞는 카풀과 동행을 찾아보세요.",
);

export default function CarpoolLayout({ children }: { children: React.ReactNode }) {
  return children;
}
