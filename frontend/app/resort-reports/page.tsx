"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { formatKoreanCalendarDate, formatReportTime, getResortReportDisplayName } from "../lib/resortReports";
import { RESORT_MAP } from "../lib/resortTags";
import { API_ENDPOINTS, OffsetPage, ResortReportItem } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { CompactSelect } from "../components/CompactSelect";

interface ResortMaster {
  id: number;
  name: string;
}

const PAGE_SIZE = 20;

export default function ResortReportsPage() {
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));
  const [reports, setReports] = useState<ResortReportItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedResortId, setSelectedResortId] = useState<number | undefined>();
  const [resortList, setResortList] = useState<ResortMaster[]>([]);
  const [resortListError, setResortListError] = useState(false);
  const [page, setPage] = useState(1);
  const [pageInfo, setPageInfo] = useState<OffsetPage<ResortReportItem>["pageInfo"]>({
    page: 1,
    totalPages: 0,
    totalElements: 0,
    hasNext: false,
    pageSize: PAGE_SIZE,
  });

  useEffect(() => {
    const timer = window.setInterval(() => setKoreanToday(formatKoreanCalendarDate(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    void fetch(API_ENDPOINTS.master.resorts, { credentials: "include" })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data: ResortMaster[]) => {
        setResortList(Array.isArray(data) ? data : []);
        setResortListError(false);
      })
      .catch(() => setResortListError(true));
  }, []);

  const loadReports = useCallback(async () => {
    try {
      const response = await fetch(API_ENDPOINTS.resortReports.today(selectedResortId, page, PAGE_SIZE), { credentials: "include" });
      if (!response.ok) throw new Error(String(response.status));
      const data: OffsetPage<ResortReportItem> = await response.json();
      setReports(Array.isArray(data.content) ? data.content : []);
      setPageInfo(data.pageInfo);
    } catch {
      setReports([]);
      setPageInfo({ page, totalPages: 0, totalElements: 0, hasNext: false, pageSize: PAGE_SIZE });
    } finally {
      setLoading(false);
    }
  }, [page, selectedResortId]);

  useEffect(() => {
    let cancelled = false;
    void fetch(API_ENDPOINTS.resortReports.today(selectedResortId, page, PAGE_SIZE), { credentials: "include" })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: OffsetPage<ResortReportItem>) => {
        if (cancelled) return;
        setReports(Array.isArray(data.content) ? data.content : []);
        setPageInfo(data.pageInfo);
      })
      .catch(() => {
        if (cancelled) return;
        setReports([]);
        setPageInfo({ page, totalPages: 0, totalElements: 0, hasNext: false, pageSize: PAGE_SIZE });
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [page, selectedResortId]);

  const deleteReport = async (reportId: number) => {
    if (!window.confirm("이 설질 제보를 삭제할까요?")) return;
    const response = await csrfFetch(API_ENDPOINTS.resortReports.delete(reportId), { method: "DELETE" });
    if (response.ok) {
      await loadReports();
      return;
    }
    const data = await response.json().catch(() => ({}));
    alert(data.message || "설질 제보 삭제에 실패했습니다.");
  };

  return (
    <div className="community-page">
      <TopNav active="posts" />
      <main className="community-container resort-report-page">
        <header className="resort-report-page-heading">
          <div><h1>❄️ 오늘의 설질</h1><time>{koreanToday}</time></div>
          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            <CompactSelect
              value={selectedResortId == null ? "" : String(selectedResortId)}
              options={[{ value: "", label: "전체 리조트" }, ...resortList.map((resort) => ({ value: String(resort.id), label: getResortReportDisplayName(resort.name) }))]}
              disabled={resortListError}
              onChange={(value) => {
                setLoading(true);
                setPage(1);
                setSelectedResortId(value ? Number(value) : undefined);
              }}
              ariaLabel="리조트 필터"
              className="resort-filter-select"
            />
            <Link href="/">홈으로</Link>
          </div>
        </header>

        {resortListError && <p style={{ color: "#b91c1c", marginBottom: "12px" }}>리조트 목록을 불러오지 못했습니다. 전체 제보는 계속 확인할 수 있습니다.</p>}
        <section className="panel resort-report-board" aria-label={`${koreanToday} 설질 제보 목록`}>
          <header><strong>오늘 등록된 제보</strong><span>{loading ? "불러오는 중..." : `${pageInfo.totalElements}건`}</span></header>
          <div>
            {loading ? (
              <p style={{ padding: "32px 0", textAlign: "center", color: "#888", fontSize: "0.9rem" }}>설질 제보 목록을 불러오는 중입니다...</p>
            ) : reports.length === 0 ? (
              <p style={{ padding: "32px 0", textAlign: "center", color: "#888", fontSize: "0.9rem" }}>오늘 등록된 설질 제보가 없습니다.</p>
            ) : reports.map((report) => {
              const resort = RESORT_MAP[report.resortCode];
              return (
                <article className="snow-report-row" key={report.reportId}>
                  <span className={`snow-report-tag ${resort?.markerClass ?? "bg-[#3f6f8f]"}`}>{getResortReportDisplayName(report.resortName)}</span>
                  <strong className="snow-report-content">{report.content}</strong>
                  <span className="snow-report-author">- {report.authorNickname}</span>
                  <time className="snow-report-time">{formatReportTime(report.createdAt)}</time>
                  {report.canDelete && <button className="snow-report-delete" type="button" onClick={() => void deleteReport(report.reportId)}>삭제</button>}
                </article>
              );
            })}
          </div>
          {pageInfo.totalPages > 1 && (
            <footer style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "12px", padding: "16px" }}>
              <button type="button" disabled={page <= 1 || loading} onClick={() => { setLoading(true); setPage((current) => current - 1); }}>이전</button>
              <span>{page} / {pageInfo.totalPages}</span>
              <button type="button" disabled={!pageInfo.hasNext || loading} onClick={() => { setLoading(true); setPage((current) => current + 1); }}>다음</button>
            </footer>
          )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
