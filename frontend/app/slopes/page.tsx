"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { PageHeading } from "../components/PageHeading";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";
import { upcomingOpening } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";
import { resortSlug } from "../resort-cam/types";
import styles from "./slopes.module.css";

type SessionStatus = "OPEN" | "CLOSED" | null;

type Slope = {
  name: string;
  difficulty: string | null;
  open: boolean;
  day: SessionStatus;
  night: SessionStatus;
  lateNight: SessionStatus;
  note: string | null;
};

type ResortSlopes = {
  resortCode: string;
  resortName: string;
  regionName: string;
  supported: boolean;
  sourceUrl: string | null;
  collectedAt: string | null;
  lastAttemptAt: string | null;
  stale: boolean;
  openSlopeCount: number | null;
  totalSlopeCount: number | null;
  operatingNote: string | null;
  slopes: Slope[];
};

type BoardState = { status: "loading" } | { status: "error" } | { status: "ready"; resorts: ResortSlopes[] };

const KST_STAMP = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

const SESSIONS: { key: "day" | "night" | "lateNight"; label: string }[] = [
  { key: "day", label: "주간" },
  { key: "night", label: "야간" },
  { key: "lateNight", label: "심야" },
];

function formatStamp(value: string): string {
  return KST_STAMP.format(new Date(value));
}

export default function SlopesPage() {
  const [board, setBoard] = useState<BoardState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    fetch(API_ENDPOINTS.slopeStatus.board, { signal: controller.signal })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: { resorts?: ResortSlopes[] }) => {
        setBoard({ status: "ready", resorts: Array.isArray(data.resorts) ? data.resorts : [] });
      })
      .catch(() => {
        if (!controller.signal.aborted) setBoard({ status: "error" });
      });
    return () => controller.abort();
  }, []);

  // 카드는 데이터를 받은 뒤에 그려지므로 주소의 #리조트로 직접 옮긴다(슬로프캠 헤더에서 들어올 때).
  useEffect(() => {
    if (board.status !== "ready" || !window.location.hash) return;
    const target = document.getElementById(decodeURIComponent(window.location.hash.slice(1)));
    target?.scrollIntoView({ block: "start" });
  }, [board.status]);

  const supported = board.status === "ready" ? board.resorts.filter((resort) => resort.supported) : [];
  const unsupported = board.status === "ready" ? board.resorts.filter((resort) => !resort.supported) : [];

  return (
    <div className="community-page">
      <TopNav active="resort" />
      <main className="community-container">
        <PageHeading
          title="슬로프 현황"
          description="리조트 공식 사이트에 공개된 운영 현황을 하루 몇 번 읽어 모았습니다. 현장 사정으로 바뀔 수 있으니 출발 전 공식 페이지에서 한 번 더 확인하세요."
          actions={<Link href="/resort-cam">슬로프캠 보기</Link>}
        />

        {board.status === "loading" && <p className={styles.notice}>현황을 불러오는 중입니다...</p>}
        {board.status === "error" && <p className={styles.notice}>현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>}

        {board.status === "ready" && (
          <>
            {supported.length === 0 ? (
              <p className={styles.notice}>공식 현황을 받을 수 있는 리조트가 아직 없습니다.</p>
            ) : (
              <div className={styles.grid}>
                {supported.map((resort) => (
                  <ResortCard key={resort.resortCode} resort={resort} />
                ))}
              </div>
            )}
            {unsupported.length > 0 && (
              <p className={styles.unsupported}>
                공식 사이트에서 현황을 받을 수 없어 아직 보여 드리지 못하는 리조트:{" "}
                {unsupported.map((resort) => resort.resortName).join(", ")}
              </p>
            )}
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}

function ResortCard({ resort }: { resort: ResortSlopes }) {
  const today = useKstToday();
  const beforeOpening = today ? upcomingOpening(resort.resortCode, today) !== null : false;
  const hasData = resort.collectedAt !== null;
  const openCount = resort.openSlopeCount ?? 0;
  const total = resort.totalSlopeCount ?? resort.slopes.length;

  let headline: string;
  if (!hasData) headline = beforeOpening ? "시즌 개장 전" : "정보 준비 중";
  else if (openCount === 0) headline = beforeOpening ? "시즌 개장 전" : "운영 중인 슬로프 없음";
  else headline = "운영 중";

  return (
    <article id={resortSlug(resort.resortCode)} className={styles.card}>
      <header className={styles.cardHeader}>
        <div>
          <span className={styles.region}>{resort.regionName}</span>
          <h2>{resort.resortName}</h2>
        </div>
        {hasData && (
          <div className={styles.count} aria-label={`전체 ${total}면 중 ${openCount}면 운영`}>
            <strong data-open={openCount > 0}>{openCount}</strong>
            <span>/ {total}</span>
          </div>
        )}
      </header>

      <p className={styles.headline} data-open={hasData && openCount > 0}>
        {headline}
      </p>
      {resort.operatingNote && <p className={styles.note}>운영 시간: {resort.operatingNote}</p>}

      {hasData && resort.slopes.length > 0 && (
        <details className={styles.details}>
          <summary>슬로프별 보기</summary>
          <ul className={styles.slopeList}>
            {resort.slopes.map((slope, index) => (
              <li key={`${slope.name}-${index}`} data-open={slope.open}>
                <div className={styles.slopeName}>
                  <strong>{slope.name}</strong>
                  {slope.difficulty && <small>{slope.difficulty}</small>}
                </div>
                <div className={styles.sessions}>
                  {SESSIONS.filter(({ key }) => slope[key] !== null).map(({ key, label }) => (
                    <span key={key} className={styles.session} data-open={slope[key] === "OPEN"}>
                      {label} {slope[key] === "OPEN" ? "운영" : "휴장"}
                    </span>
                  ))}
                </div>
                {slope.note && <p className={styles.slopeNote}>{slope.note}</p>}
              </li>
            ))}
          </ul>
        </details>
      )}

      <footer className={styles.cardFooter}>
        <span>
          {hasData && resort.collectedAt ? `${formatStamp(resort.collectedAt)} 갱신` : "아직 받은 현황이 없습니다"}
          {resort.stale && <em className={styles.stale}> 최근 갱신에 실패해 이전 값을 보여 줍니다</em>}
        </span>
        {resort.sourceUrl && (
          <a href={resort.sourceUrl} target="_blank" rel="noopener noreferrer">
            공식 페이지
            <span className="material-symbols-outlined" aria-hidden="true">
              open_in_new
            </span>
          </a>
        )}
      </footer>
    </article>
  );
}
