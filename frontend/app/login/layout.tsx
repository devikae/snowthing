import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "로그인 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function LoginLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
