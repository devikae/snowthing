import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../../../lib/siteMetadata";

export const metadata: Metadata = { title: "게시글 수정 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function PostEditLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
