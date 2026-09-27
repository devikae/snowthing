"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { formatKoreanCalendarDate, RESORT_REPORT_PREVIEWS } from "../lib/resortReports";
import { RESORT_MAP } from "../lib/resortTags";

export default function ResortReportsPage() {
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));

  useEffect(() => {
    const timer = window.setInterval(() => setKoreanToday(formatKoreanCalendarDate(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="community-page">
      <TopNav active="posts" />
      <main className="community-container resort-report-page">
        <header className="resort-report-page-heading">
          <div>
            <h1>❄️ 오늘의 설질</h1>
            <time>{koreanToday}</time>
          </div>
          <Link href="/">홈으로</Link>
        </header>

        <section className="panel resort-report-board" aria-label={`${koreanToday} 설질 제보 목록`}>
          <header><strong>오늘 등록된 제보</strong><span>{RESORT_REPORT_PREVIEWS.length}건</span></header>
          <div>
            {RESORT_REPORT_PREVIEWS.map((report) => {
              const resort = RESORT_MAP[report.resortCode];
              return (
                <article key={report.id}>
                  <span className={`snow-report-tag ${resort.markerClass}`}>{resort.koreanName}</span>
                  <strong>{report.content}</strong>
                  <time>{report.time}</time>
                </article>
              );
            })}
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
