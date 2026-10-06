"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { formatKoreanCalendarDate, formatReportTime } from "../lib/resortReports";
import { RESORT_MAP } from "../lib/resortTags";
import { API_ENDPOINTS, ResortReportItem } from "../lib/api";

interface ResortMaster {
  id: number;
  name: string;
}

export default function ResortReportsPage() {
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));
  const [reports, setReports] = useState<ResortReportItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedResortId, setSelectedResortId] = useState<number | undefined>(undefined);
  const [resortList, setResortList] = useState<ResortMaster[]>([]);

  useEffect(() => {
    const timer = window.setInterval(() => setKoreanToday(formatKoreanCalendarDate(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    void fetch(API_ENDPOINTS.master.resorts, { credentials: "include" })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: ResortMaster[]) => {
        if (Array.isArray(data)) {
          setResortList(data);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    let cancelled = false;

    void fetch(API_ENDPOINTS.resortReports.today(selectedResortId, 100), { credentials: "include" })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: ResortReportItem[]) => {
        if (!cancelled) {
          setReports(Array.isArray(data) ? data : []);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setReports([]);
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedResortId]);

  return (
    <div className="community-page">
      <TopNav active="posts" />
      <main className="community-container resort-report-page">
        <header className="resort-report-page-heading">
          <div>
            <h1>❄️ 오늘의 설질</h1>
            <time>{koreanToday}</time>
          </div>
          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            <select
              value={selectedResortId ?? ""}
              onChange={(e) => {
                setLoading(true);
                setSelectedResortId(e.target.value ? Number(e.target.value) : undefined);
              }}
              aria-label="리조트 필터"
              style={{
                height: "36px",
                padding: "0 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "13px",
                backgroundColor: "var(--snow-card, #fff)",
                color: "var(--snow-ink, #1e293b)",
              }}
            >
              <option value="">전체 리조트</option>
              {resortList.map((resort) => (
                <option key={resort.id} value={resort.id}>
                  {resort.name}
                </option>
              ))}
            </select>
            <Link href="/">홈으로</Link>
          </div>
        </header>

        <section className="panel resort-report-board" aria-label={`${koreanToday} 설질 제보 목록`}>
          <header>
            <strong>오늘 등록된 제보</strong>
            <span>{loading ? "불러오는 중..." : `${reports.length}건`}</span>
          </header>
          <div>
            {loading ? (
              <p style={{ padding: "32px 0", textAlign: "center", color: "#888", fontSize: "0.9rem" }}>
                설질 제보 목록을 불러오는 중입니다...
              </p>
            ) : reports.length === 0 ? (
              <p style={{ padding: "32px 0", textAlign: "center", color: "#888", fontSize: "0.9rem" }}>
                오늘 등록된 설질 제보가 없습니다.
              </p>
            ) : (
              reports.map((report) => {
                const resortMeta = RESORT_MAP[report.resortCode];
                const markerClass = resortMeta?.markerClass ?? "bg-[#3f6f8f]";
                return (
                  <article key={report.reportId}>
                    <span className={`snow-report-tag ${markerClass}`}>{report.resortName}</span>
                    <div>
                      <strong>{report.content}</strong>
                      {report.authorNickname && (
                        <span style={{ marginLeft: "8px", fontSize: "11px", color: "var(--snow-muted, #94a3b8)" }}>
                          by {report.authorNickname}
                        </span>
                      )}
                    </div>
                    <time>{formatReportTime(report.createdAt)}</time>
                  </article>
                );
              })
            )}
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
