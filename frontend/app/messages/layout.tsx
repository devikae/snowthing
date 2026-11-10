import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("쪽지함", "회원끼리 주고받은 쪽지를 확인합니다.");

export default function MessagesLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
