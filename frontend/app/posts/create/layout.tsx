import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../../lib/siteMetadata";

export const metadata: Metadata = { title: "글쓰기 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function PostCreateLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
