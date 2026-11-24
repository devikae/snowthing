import type { Metadata } from "next";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata(
  "슬로프맵",
  "국내외 스키장 리조트별 코스 맵과 공식 슬로프 길이, 경사를 보고 슬로프끼리 비교합니다.",
  "/slopes",
);

export default function SlopesLayout({ children }: { children: React.ReactNode }) {
  return children;
}
