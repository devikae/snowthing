import type { Metadata } from "next";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata(
  "슬로프 현황",
  "리조트 공식 사이트에 공개된 오늘 운영 슬로프를 한 화면에서 봅니다.",
  "/slopes",
);

export default function SlopesLayout({ children }: { children: React.ReactNode }) {
  return children;
}
