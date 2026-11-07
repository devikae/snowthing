import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "통합 검색 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function SearchLayout({ children }: { children: React.ReactNode }) {
  return children;
}
