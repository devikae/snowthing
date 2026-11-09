import type { Metadata } from "next";
import { PRIVATE_PAGE_ROBOTS } from "../lib/siteMetadata";
import { SeasonLogApp } from "./SeasonLogApp";

export const metadata: Metadata = { title: "내 시즌 기록 | 눈팅 SnowThing", robots: PRIVATE_PAGE_ROBOTS };

export default function SeasonLogPage() {
  return <SeasonLogApp />;
}
