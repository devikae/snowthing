import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "회원가입 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function SignupLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
