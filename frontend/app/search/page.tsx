"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { type FormEvent, type ReactNode, Suspense, useEffect, useState } from "react";
import { Footer, TopNav } from "../components/SiteChrome";
import { formatCarpoolDateTime } from "../carpool/format";
import { API_ENDPOINTS } from "../lib/api";
import styles from "./search.module.css";

const PREVIEW_SIZE = 5;
// 게시글 검색에는 카풀 글도 섞여 나온다. 카풀 칸과 겹치지 않게 넉넉히 받아 거른다.
const POST_FETCH_SIZE = 20;

interface PostRow { publicId: string; title: string; categoryName: string; categoryCode: string; writerNickname: string; createdAt: string }
interface CarpoolRow { publicId: string; title: string; departureRegion: string; destinationResortName: string; departureAt: string }
interface MarketRow { publicId: string; title: string; price: number; free: boolean; category: { name: string } }

interface Results {
  keyword: string;
  posts: PostRow[] | null;
  carpools: CarpoolRow[] | null;
  carpoolTotal: number;
  market: MarketRow[] | null;
  marketTotal: number;
  marketLocked: boolean;
}

async function fetchJson(url: string) {
  const response = await fetch(url, { credentials: "include" });
  return { status: response.status, data: response.ok ? await response.json() : null };
}

async function search(keyword: string): Promise<Results> {
  const q = encodeURIComponent(keyword);
  const [posts, carpools, market] = await Promise.allSettled([
    fetchJson(`${API_ENDPOINTS.posts.list}?page=1&size=${POST_FETCH_SIZE}&keyword=${q}`),
    fetchJson(`${API_ENDPOINTS.carpool.list}?page=0&size=${PREVIEW_SIZE}&keyword=${q}`),
    fetchJson(`${API_ENDPOINTS.market.list}?page=1&size=${PREVIEW_SIZE}&keyword=${q}`),
  ]);
  const data = (result: PromiseSettledResult<{ status: number; data: { content?: unknown[]; totalElements?: number; pageInfo?: { totalElements?: number } } | null }>) =>
    result.status === "fulfilled" ? result.value.data : null;
  const marketData = data(market);
  return {
    keyword,
    posts: data(posts) ? ((data(posts)?.content ?? []) as PostRow[]).filter((row) => row.categoryCode !== "CARPOOL").slice(0, PREVIEW_SIZE) : null,
    carpools: data(carpools) ? ((data(carpools)?.content ?? []) as CarpoolRow[]) : null,
    carpoolTotal: data(carpools)?.totalElements ?? 0,
    market: marketData ? ((marketData.content ?? []) as MarketRow[]) : null,
    marketTotal: marketData?.pageInfo?.totalElements ?? marketData?.totalElements ?? 0,
    marketLocked: market.status === "fulfilled" && market.value.status === 401,
  };
}

function Section({ title, count, more, children }: { title: string; count?: number; more?: string; children: ReactNode }) {
  return (
    <section className={styles.section}>
      <header>
        <h2>{title}{count !== undefined && <span>{count.toLocaleString("ko-KR")}건</span>}</h2>
        {more && <Link href={more}>더보기</Link>}
      </header>
      {children}
    </section>
  );
}

function SearchContent() {
  const keyword = (useSearchParams().get("q") ?? "").trim();
  const [input, setInput] = useState(keyword);
  const [results, setResults] = useState<Results | null>(null);
  const q = encodeURIComponent(keyword);

  useEffect(() => {
    if (!keyword) return;
    let stale = false;
    void search(keyword).then((next) => {
      if (!stale) setResults(next);
    });
    return () => {
      stale = true;
    };
  }, [keyword]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next = input.trim();
    if (next) window.location.assign(`/search?q=${encodeURIComponent(next)}`);
  };
  const loading = Boolean(keyword) && results?.keyword !== keyword;
  const failed = "불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
  const nothing = "검색 결과가 없습니다.";

  return (
    <>
      <h1 className={styles.title}>{keyword ? <><strong>{keyword}</strong> 검색 결과</> : "통합 검색"}</h1>
      <form className={styles.form} onSubmit={submit} role="search">
        <input aria-label="통합 검색" placeholder="게시글, 카풀, 중고장터 검색" value={input} maxLength={100} onChange={(event) => setInput(event.target.value)} />
        <button type="submit">검색</button>
      </form>
      {!keyword ? <p className={styles.state}>찾을 단어를 입력해 주세요.</p> : loading || !results ? <p className={styles.state} aria-busy="true">검색 중입니다.</p> : (
        <>
          <Section title="게시글" more={`/posts?keyword=${q}`}>
            {results.posts === null ? <p className={styles.state}>{failed}</p> : results.posts.length === 0 ? <p className={styles.state}>{nothing}</p> : (
              <ul className={styles.list}>
                {results.posts.map((row) => (
                  <li key={row.publicId}>
                    <Link href={`/posts/${row.publicId}`}>
                      <em>{row.categoryName}</em>
                      <strong>{row.title}</strong>
                      <small>{row.writerNickname}</small>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Section>
          <Section title="카풀" count={results.carpools ? results.carpoolTotal : undefined} more={`/carpool?keyword=${q}`}>
            {results.carpools === null ? <p className={styles.state}>{failed}</p> : results.carpools.length === 0 ? <p className={styles.state}>{nothing}</p> : (
              <ul className={styles.list}>
                {results.carpools.map((row) => (
                  <li key={row.publicId}>
                    <Link href={`/carpool/${row.publicId}`}>
                      <em>{row.destinationResortName}</em>
                      <strong>{row.title}</strong>
                      <small>{row.departureRegion}, {formatCarpoolDateTime(row.departureAt)}</small>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Section>
          <Section title="중고장터" count={results.market ? results.marketTotal : undefined} more={results.marketLocked ? undefined : `/market?keyword=${q}`}>
            {results.marketLocked ? (
              <p className={styles.state}>중고장터는 로그인한 회원만 볼 수 있어요. <Link href={`/login?returnUrl=${encodeURIComponent(`/search?q=${keyword}`)}`}>로그인</Link></p>
            ) : results.market === null ? <p className={styles.state}>{failed}</p> : results.market.length === 0 ? <p className={styles.state}>{nothing}</p> : (
              <ul className={styles.list}>
                {results.market.map((row) => (
                  <li key={row.publicId}>
                    <Link href={`/market/${row.publicId}`}>
                      <em>{row.category.name}</em>
                      <strong>{row.title}</strong>
                      <small>{row.free ? "무료 나눔" : `${row.price.toLocaleString("ko-KR")}원`}</small>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        </>
      )}
    </>
  );
}

export default function SearchPage() {
  return (
    <div className="community-page">
      <TopNav active="home" />
      <main className={`community-container ${styles.page}`}>
        <Suspense fallback={<p className={styles.state} aria-busy="true">검색 중입니다.</p>}>
          <SearchContent />
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}
