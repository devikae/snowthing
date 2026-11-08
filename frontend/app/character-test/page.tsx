import type { Metadata } from "next";
import { publicPageMetadata } from "../lib/siteMetadata";
import { CharacterTestApp } from "./CharacterTestApp";

const base = publicPageMetadata("스키장 캐릭터 테스트", "스키장 상황 15개를 고르면 나의 캐릭터 카드가 나오고, 같이 타는 사람들과 어떤 사이인지 지도로 볼 수 있어요. 가입 없이 재미로 해 보세요.", "/character-test");

export const metadata: Metadata = { ...base, openGraph: { ...base.openGraph, images: [{ url: "/character-test/og-test.png", width: 1200, height: 630, alt: "나는 어떤 스키장 캐릭터일까" }] }, twitter: { ...base.twitter, card: "summary_large_image" } };

export default function CharacterTestPage() {
  return <><header className="ct-heading"><h1>스키장 캐릭터 테스트</h1></header><CharacterTestApp /></>;
}
