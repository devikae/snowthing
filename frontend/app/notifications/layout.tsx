import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("알림", "내 글의 댓글, 카풀 요청 같은 새 소식을 확인합니다.");

export default function NotificationsLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
