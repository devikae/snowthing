import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "비밀번호 재설정 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function ForgotPasswordLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
