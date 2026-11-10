import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("회원가입", "이메일 인증으로 눈팅 SnowThing에 가입하고 게시판, 카풀, 중고장터를 이용하세요.");

export default function SignupLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
