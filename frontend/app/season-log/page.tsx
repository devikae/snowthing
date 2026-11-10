import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";
import { SeasonLogApp } from "./SeasonLogApp";

export const metadata: Metadata = privatePageMetadata("내 시즌 기록", "이번 시즌 다녀온 리조트와 방문 날짜를 기록합니다.");

export default function SeasonLogPage() {
  return <SeasonLogApp />;
}
