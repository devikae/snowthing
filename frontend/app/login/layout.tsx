import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("로그인", "이메일이나 카카오, 네이버, Google 계정으로 눈팅 SnowThing에 로그인하세요.");

export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
