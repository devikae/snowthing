import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("내 정보 수정", "닉네임, 프로필 사진, 비밀번호와 소셜 계정 연결을 관리합니다.");

export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
