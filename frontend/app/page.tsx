import type { Metadata } from "next";
import HomePageClient from "./HomePageClient";
import { HOME_CARPOOL_SIZE, HOME_FEED_SIZE, HOME_MARKET_SIZE, type HomeCarpoolItem, type HomeInitialData, type HomePost, type MarketPreviewItem } from "./lib/homeFeed";
import { fetchPublicJson } from "./lib/publicApi";
import { SITE_URL, websiteStructuredData } from "./lib/siteMetadata";

export const metadata: Metadata = { alternates: { canonical: `${SITE_URL}/` } };

// 검색엔진과 첫 방문자가 빈 목록 대신 글 제목을 바로 보도록 "전체" 탭 글, 카풀, 장터 첫 목록을 서버에서 받는다.
// 셋 다 비로그인 기준 공개 목록이라 쿠키를 넘기지 않는다(fetchPublicJson). 하나가 실패하면 그 위젯만 브라우저가 기존처럼 다시 부른다.
// 오늘의 설질은 서버에서 받지 않고 브라우저가 받는다. 제보 시각은 formatReportTime이 KST 숫자로 조립해 어디서 그려도 같다.
export default async function HomePage() {
  const [posts, carpools, market, notices] = await Promise.all([
    fetchPublicJson<{ content?: HomePost[] }>(`/posts?page=1&size=${HOME_FEED_SIZE}`),
    fetchPublicJson<{ content?: HomeCarpoolItem[] }>(`/carpools?page=0&size=${HOME_CARPOOL_SIZE}`),
    fetchPublicJson<{ items?: MarketPreviewItem[] }>("/market-listings/preview"),
    fetchPublicJson<{ content?: HomePost[] }>("/posts?categoryCode=NOTICE&page=1&size=1"),
  ]);
  const initial: HomeInitialData = {
    posts: posts ? (Array.isArray(posts.content) ? posts.content.slice(0, HOME_FEED_SIZE) : []) : null,
    carpools: carpools ? (Array.isArray(carpools.content) ? carpools.content : []) : null,
    marketPreviews: market ? (Array.isArray(market.items) ? market.items.slice(0, HOME_MARKET_SIZE) : []) : null,
    latestNotice: Array.isArray(notices?.content) ? (notices.content[0] ?? null) : null,
  };
  return <>
    <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteStructuredData).replace(/</g, "\\u003c") }} />
    <HomePageClient initial={initial} />
  </>;
}
