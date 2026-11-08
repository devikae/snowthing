import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";
import { AdminGate } from "./AdminGate";
import "./admin.css";

export const metadata: Metadata = { title: "관리자 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AdminGate>{children}</AdminGate>;
}
