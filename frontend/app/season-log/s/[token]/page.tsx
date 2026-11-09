import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SITE_NAME } from "../../../lib/siteMetadata";
import { cardHeadline, shareHeadline, topResortLine } from "../../types";
import { fetchSharedSeason } from "./sharedSeason";

// 공유 결산은 검색에 나오지 않게 한다. 링크 미리보기 문구만 만든다. 카드 이미지는 같은 폴더의 opengraph-image가 만든다.
export async function generateMetadata({ params }: { params: Promise<{ token: string }> }): Promise<Metadata> {
  const { token } = await params;
  const shared = await fetchSharedSeason(token);
  if (!shared) return { title: "시즌 결산 | 눈팅 SnowThing", robots: { index: false, follow: false } };
  const title = `${shared.nickname} 님의 ${shared.seasonLabel} 결산 | 눈팅 SnowThing`;
  const description = `${shareHeadline(shared.nickname, shared.totalDays)}. ${topResortLine(shared.topResort)}.`;
  return {
    title,
    description,
    robots: { index: false, follow: false },
    openGraph: { type: "website", title, description, siteName: SITE_NAME, locale: "ko_KR" },
    twitter: { card: "summary_large_image", title, description },
  };
}

export default async function SharedSeasonPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const shared = await fetchSharedSeason(token);
  if (!shared) notFound();
  return <section className="season-share-page" aria-label="시즌 결산">
    <div className="season-share-card">
      <span>{shared.seasonLabel}</span>
      <h2>{cardHeadline(shared.nickname)}</h2>
      <strong className="season-share-days">{shared.totalDays}일</strong>
      <p className="season-share-top">{topResortLine(shared.topResort)}</p>
      {shared.resorts.length > 0 && <ul>
        {shared.resorts.map((resort) => <li key={resort.resortId}><span>{resort.resortName}</span><span>{resort.days}일</span></li>)}
      </ul>}
    </div>
    <div className="season-share-cta">
      <Link href="/season-log">내 시즌도 기록하기</Link>
      <Link href="/resort-reports">오늘의 설질 보기</Link>
    </div>
  </section>;
}
