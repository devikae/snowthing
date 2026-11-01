import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "중고장터 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function MarketLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
