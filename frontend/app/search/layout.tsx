import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("통합 검색", "게시판 글을 제목과 내용으로 찾아보세요.");

export default function SearchLayout({ children }: { children: React.ReactNode }) {
  return children;
}
