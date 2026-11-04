import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";

export const metadata: Metadata = { title: "쪽지함 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function MessagesLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
