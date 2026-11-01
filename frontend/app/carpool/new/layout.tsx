import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../../lib/siteMetadata";

export const metadata: Metadata = { title: "카풀 모집글 등록 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function CarpoolCreateLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
