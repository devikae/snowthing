"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { Footer, TopNav } from "../components/SiteChrome";
import { formatKoreanCalendarDate, formatReportTime, getResortReportDisplayName } from "../lib/resortReports";
import { RESORT_MAP } from "../lib/resortTags";
import { API_ENDPOINTS, OffsetPage, ResortReportItem } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { CompactSelect } from "../components/CompactSelect";
import { readErrorMessage } from "../lib/http";
import { confirm, toast } from "../lib/feedback";

interface ResortMaster {
  id: number;
  name: string;
}

const PAGE_SIZE = 20;

export default function ResortReportsPage() {
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));
  const [reports, setReports] = useState<ResortReportItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
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

  // 실패를 빈 목록으로 그리면 장애가 "제보 없음"으로 보인다. 실패는 따로 표시하고 다시 시도를 둔다.
  // 필터나 페이지를 빠르게 바꾸면 늦게 온 이전 응답이 새 목록을 덮지 않게 signal로 끊는다.
  const loadReports = useCallback(async (signal?: AbortSignal) => {
    try {
      const response = await fetch(API_ENDPOINTS.resortReports.today(selectedResortId, page, PAGE_SIZE), { credentials: "include", signal });
      if (!response.ok) throw new Error(String(response.status));
      const data: OffsetPage<ResortReportItem> = await response.json();
      if (signal?.aborted) return;
      setReports(Array.isArray(data.content) ? data.content : []);
      setPageInfo(data.pageInfo);
      setLoadFailed(false);
    } catch {
      if (signal?.aborted) return;
      setReports([]);
      setLoadFailed(true);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [page, selectedResortId]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadReports(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [loadReports]);

  const retry = () => {
    setLoading(true);
    void loadReports();
  };

  const deleteReport = async (reportId: number) => {
    if (deletingId !== null) return;
    if (!(await confirm({ title: "이 설질 제보를 삭제할까요?", description: "삭제하면 되돌릴 수 없습니다.", confirmLabel: "삭제", danger: true }))) return;
    setDeletingId(reportId);
    try {
      const response = await csrfFetch(API_ENDPOINTS.resortReports.delete(reportId), { method: "DELETE" });
      if (response.ok) {
        await loadReports();
        return;
      }
      toast.error(await readErrorMessage(response, "설질 제보를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    } catch {
      toast.error("서버에 연결하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container resort-report-page">
        <PageHeading title="오늘의 설질" description="라이더가 직접 전하는 리조트 현장 소식입니다."
          meta={<time className="snow-date-badge">{koreanToday}</time>}
          actions={<>
            <CompactSelect
              value={selectedResortId == null ? "" : String(selectedResortId)}
              options={[
                { value: "", label: "전체 리조트" },
                ...resortList.map((resort) => ({ value: String(resort.id), label: getResortReportDisplayName(resort.name) })),
              ]}
              disabled={resortListError}
              onChange={(value) => {
                setLoading(true);
                setPage(1);
                setSelectedResortId(value ? Number(value) : undefined);
              }}
              ariaLabel="리조트 필터"
              className="resort-filter-select"
            />
            <Link href="/">
              ← 홈으로
            </Link>
          </>} />

        {resortListError && (
          <p className="compose-error" style={{ marginBottom: "12px" }}>
            리조트 목록을 불러오지 못했습니다. 전체 제보는 계속 확인할 수 있습니다.
          </p>
        )}
        <section className="panel resort-report-board" aria-label={`${koreanToday} 설질 제보 목록`}>
          <header>
            <strong>오늘 등록된 제보</strong>
            {!loading && !loadFailed && <span>{`${pageInfo.totalElements}건`}</span>}
          </header>
          <div>
            {loading ? (
              <div className="snow-empty-state vertical">
                <span className="snow-empty-icon">
                  <span data-nosnippet className="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
                </span>
                <div className="snow-empty-copy">
                  <strong>설질 제보 목록을 불러오는 중입니다.</strong>
                </div>
              </div>
            ) : loadFailed ? (
              <div className="snow-empty-state vertical" role="alert">
                <span className="snow-empty-icon">
                  <span className="material-symbols-outlined" aria-hidden="true">cloud_off</span>
                </span>
                <div className="snow-empty-copy">
                  <strong>설질 제보를 불러오지 못했습니다.</strong>
                  <span>잠시 후 다시 시도해 주세요.</span>
                </div>
                <button type="button" className="home-widget-retry" onClick={retry}>
                  다시 시도
                </button>
              </div>
            ) : reports.length === 0 ? (
              <div className="snow-empty-state vertical">
                <span className="snow-empty-icon">
                  <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">ac_unit</span>
                </span>
                <div className="snow-empty-copy">
                  <strong>오늘 등록된 설질 제보가 아직 없습니다</strong>
                  <span>제보는 홈 화면의 오늘의 설질 칸에서 남길 수 있어요.</span>
                </div>
                <Link href="/" className="home-widget-retry">
                  홈에서 제보 남기기
                </Link>
              </div>
            ) : (
              reports.map((report) => {
                const resort = RESORT_MAP[report.resortCode];
                return (
                  <article className="snow-report-row" key={report.reportId}>
                    <span className={`snow-report-tag ${resort?.markerClass ?? "bg-[#3f6f8f]"}`}>
                      {getResortReportDisplayName(report.resortName)}
                    </span>
                    <strong className="snow-report-content">{report.content}</strong>
                    <span className="snow-report-author">{report.authorNickname}</span>
                    <time className="snow-report-time">{formatReportTime(report.createdAt)}</time>
                    {report.canDelete && (
                      <button className="snow-report-delete" type="button" disabled={deletingId !== null} onClick={() => void deleteReport(report.reportId)}>
                        {deletingId === report.reportId ? "삭제 중" : "삭제"}
                      </button>
                    )}
                  </article>
                );
              })
            )}
          </div>
          {!loadFailed && pageInfo.totalPages > 1 && (
            <footer className="board-pagination" style={{ justifyContent: "center", padding: "16px" }}>
              <button
                type="button"
                disabled={page <= 1 || loading}
                onClick={() => {
                  setLoading(true);
                  setPage((current) => current - 1);
                }}
              >
                이전
              </button>
              <span style={{ padding: "0 8px", color: "var(--snow-muted)", fontSize: "12px" }}>
                {page} / {pageInfo.totalPages}
              </span>
              <button
                type="button"
                disabled={!pageInfo.hasNext || loading}
                onClick={() => {
                  setLoading(true);
                  setPage((current) => current + 1);
                }}
              >
                다음
              </button>
            </footer>
          )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
