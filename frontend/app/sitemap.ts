import type { MetadataRoute } from "next";
import { API_V1_URL } from "./lib/api";
import { SITE_URL } from "./lib/siteMetadata";
import { getResortCameras } from "./resort-cam/data";
import { resortSlug } from "./resort-cam/types";

// 검색엔진이 사이트맵을 부를 때마다 백엔드 목록을 다시 읽지 않게 한 시간 동안 같은 결과를 쓴다.
export const revalidate = 3600;

// 1:1 문의는 본인만 보는 게시판이라 넣지 않는다. 전체 게시판(/posts)은 따로 넣는다.
const PUBLIC_BOARDS = ["NOTICE", "FREE", "ANONYMOUS", "QNA", "FOOD", "CREW", "SEASON_ROOM"];

const STATIC_PATHS = [
  "/",
  "/posts",
  ...PUBLIC_BOARDS.map(code => `/posts?category=${code}`),
  "/market",
  "/carpool",
  "/resort-reports",
  "/resort-cam",
  "/slopes",
  "/tools",
  "/tools/season-pass",
  "/tools/season-pass-prices",
  "/tools/deck-size",
  "/character-test",
  "/terms",
  "/privacy",
];

const LIST_TIMEOUT_MS = 3000;
// 목록 API만 쓴다. 글은 100개씩 5쪽(최근 500개), 중고 거래는 50개씩 2쪽, 카풀은 100개 한 쪽.
const POST_PAGES = 5;
const MARKET_PAGES = 2;

type Entry = MetadataRoute.Sitemap[number];
type PostRow = { publicId: string; categoryCode: string; status: string; isDeleted: boolean; createdAt: string };
type MarketRow = { publicId: string; createdAt: string; updatedAt?: string | null };
type CarpoolRow = { publicId: string; createdAt?: string | null };

async function fetchList<T>(path: string): Promise<T[]> {
  try {
    const response = await fetch(`${API_V1_URL}${path}`, { next: { revalidate }, signal: AbortSignal.timeout(LIST_TIMEOUT_MS) });
    if (!response.ok) return [];
    const body = (await response.json()) as { content?: T[] };
    return Array.isArray(body.content) ? body.content : [];
  } catch {
    // 백엔드가 안 되면 이 목록 없이 정적 주소만 낸다.
    return [];
  }
}

async function fetchPages<T>(pages: number, path: (page: number) => string): Promise<T[]> {
  const results = await Promise.all(Array.from({ length: pages }, (_, index) => fetchList<T>(path(index))));
  return results.flat();
}

// 백엔드 시각은 시간대 없는 KST(LocalDateTime)다. 프론트 컨테이너는 UTC라 그대로 읽으면 9시간 밀린다.
function kstDate(value: string | null | undefined): Date | undefined {
  if (!value) return undefined;
  const date = new Date(/(Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}+09:00`);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

const url = (path: string) => new URL(path, SITE_URL).toString();

async function dynamicEntries(): Promise<Entry[]> {
  const [posts, market, carpools] = await Promise.all([
    fetchPages<PostRow>(POST_PAGES, index => `/posts?page=${index + 1}&size=100`),
    fetchPages<MarketRow>(MARKET_PAGES, index => `/market-listings?page=${index + 1}&size=50`),
    fetchList<CarpoolRow>("/carpools?page=0&size=100"),
  ]);
  // 목록 API가 이미 숨김, 삭제, 1:1 문의를 빼지만 한 번 더 거른다.
  const postEntries = posts
    .filter(post => !post.isDeleted && post.status === "NORMAL" && post.categoryCode !== "INQUIRY")
    .map(post => ({ url: url(`/posts/${post.publicId}`), lastModified: kstDate(post.createdAt) }));
  const marketEntries = market.map(item => ({ url: url(`/market/${item.publicId}`), lastModified: kstDate(item.updatedAt ?? item.createdAt) }));
  const carpoolEntries = carpools.map(item => ({ url: url(`/carpool/${item.publicId}`), lastModified: kstDate(item.createdAt) }));
  // 쪽을 나눠 읽는 사이 새 글이 생기면 같은 글이 두 번 올 수 있다.
  const seen = new Set<string>();
  return [...postEntries, ...marketEntries, ...carpoolEntries].filter(entry => !seen.has(entry.url) && Boolean(seen.add(entry.url)));
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const [resorts, dynamic] = await Promise.all([getResortCameras(), dynamicEntries()]);
  const paths = [...STATIC_PATHS, ...resorts.map(resort => `/resort-cam/${resortSlug(resort.code)}`)];
  return [...paths.map(path => ({ url: url(path) })), ...dynamic];
}
