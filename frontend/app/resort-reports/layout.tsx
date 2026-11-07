import type { Metadata } from "next";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("오늘의 설질", "라이더가 직접 전하는 리조트 현장 설질 소식입니다.", "/resort-reports");

export default function ResortReportsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
