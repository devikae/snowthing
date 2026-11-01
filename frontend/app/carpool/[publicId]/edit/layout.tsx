import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../../../lib/siteMetadata";

export const metadata: Metadata = { title: "카풀 모집글 수정 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function CarpoolEditLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
