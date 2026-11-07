"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { formatDateTime, readErrorMessage } from "../adminTypes";

type Action = "TEMPORARY_HOLD" | "RESTORE" | "DELETE" | "DISMISS";
interface ReportItem {
  reportId: number; targetType: "POST" | "COMMENT"; targetState: "NORMAL" | "HELD" | "HIDDEN" | "DELETED";
  targetPreview: string | null; targetContent: string | null; postPublicId: string | null; postCategoryCode: string | null;
  reason: string; detail: string | null; reporterNickname: string; status: "PENDING" | "RESOLVED"; createdAt: string; resolvedAt: string | null;
}
interface ReportPage { reports: ReportItem[]; page: number; totalPages: number; totalElements: number; }

const REASON_LABEL: Record<string, string> = { RIGHTS_INFRINGEMENT: "권리 침해", ILLEGAL_CONTENT: "불법 정보", FRAUD: "사기", SPAM: "도배, 광고", OTHER: "기타" };
const STATE_LABEL: Record<ReportItem["targetState"], string> = { NORMAL: "게시 중", HELD: "임시조치 중", HIDDEN: "숨김", DELETED: "삭제됨" };
const STATE_STATUS: Record<ReportItem["targetState"], string> = { NORMAL: "DRAFT", HELD: "REVIEW_REQUIRED", HIDDEN: "REVIEW_REQUIRED", DELETED: "REJECTED" };

function targetHref(item: ReportItem) {
  if (!item.postPublicId) return null;
  const base = item.postCategoryCode === "MARKET" ? "/market" : item.postCategoryCode === "CARPOOL" ? "/carpool" : "/posts";
  return `${base}/${encodeURIComponent(item.postPublicId)}`;
}

export default function AdminReportsPage() {
  const [status, setStatus] = useState<ReportItem["status"]>("PENDING");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ReportPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [holdDays, setHoldDays] = useState(30);

  const load = useCallback(async () => {
    const response = await fetch(API_ENDPOINTS.admin.reports(status, page), { credentials: "include" });
    if (!response.ok) throw new Error(await readErrorMessage(response, "신고 목록을 불러오지 못했습니다."));
    setData(await response.json());
  }, [status, page]);

  useEffect(() => {
    const timer = window.setTimeout(() => { load().catch((cause: Error) => setError(cause.message)); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const apply = async (reportId: number, action: Action) => {
    const memo = window.prompt("조치 메모 (관리자 기록, 선택)") ;
    if (memo === null) return;
    setBusy(true); setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.reportAction(reportId), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ action, memo: memo.trim() || null, holdDays: action === "TEMPORARY_HOLD" ? holdDays : null }) });
      if (!response.ok) throw new Error(await readErrorMessage(response, "조치를 처리하지 못했습니다."));
      await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "조치를 처리하지 못했습니다."); }
    finally { setBusy(false); }
  };

  return (
    <>
      <PageHeading title="신고 처리" description="권리 침해 요청은 지체 없이 처리하고 결과는 신고자와 작성자에게 자동으로 알립니다." />
      {error && <p className="admin-meta" role="alert">{error}</p>}
      <section className="panel admin-section" aria-label="신고 목록">
        <div className="admin-actions">
          <button className={`admin-button${status === "PENDING" ? " primary" : ""}`} type="button" onClick={() => { setStatus("PENDING"); setPage(0); }}>대기</button>
          <button className={`admin-button${status === "RESOLVED" ? " primary" : ""}`} type="button" onClick={() => { setStatus("RESOLVED"); setPage(0); }}>처리 완료</button>
          <label className="admin-meta">임시조치 기간 (일, 1~30) <input type="number" min={1} max={30} value={holdDays} onChange={(event) => setHoldDays(Math.min(30, Math.max(1, Number(event.target.value) || 1)))} /></label>
        </div>
        <div className="admin-list">
          {data?.reports.length === 0 && <p className="admin-meta">신고가 없습니다.</p>}
          {data?.reports.map((item) => {
            const href = targetHref(item);
            return (
              <div className="admin-row" key={item.reportId}>
                <span className="admin-status" data-status={STATE_STATUS[item.targetState]}>{STATE_LABEL[item.targetState]}</span>
                <span style={{ minWidth: 0 }}>
                  <p>{item.targetType === "COMMENT" ? "[댓글] " : "[글] "}{href ? <Link href={href} target="_blank">{item.targetPreview}</Link> : item.targetPreview ?? "대상 없음"}</p>
                  <small>{REASON_LABEL[item.reason] ?? item.reason}, 신고자 {item.reporterNickname}, {formatDateTime(item.createdAt)}{item.resolvedAt ? `, 처리 ${formatDateTime(item.resolvedAt)}` : ""}</small>
                  {item.detail && <small style={{ display: "block" }}>신고 내용: {item.detail}</small>}
                  {item.targetContent && <details><summary className="admin-meta">본문 보기</summary><small style={{ display: "block", whiteSpace: "pre-wrap" }}>{item.targetContent}</small></details>}
                </span>
                <span className="admin-actions">
                  {item.targetState === "NORMAL" && <button className="admin-button" type="button" disabled={busy} onClick={() => void apply(item.reportId, "TEMPORARY_HOLD")}>임시조치</button>}
                  {item.targetState === "HELD" && <button className="admin-button" type="button" disabled={busy} onClick={() => void apply(item.reportId, "RESTORE")}>복구</button>}
                  {item.targetState !== "DELETED" && <button className="admin-button danger" type="button" disabled={busy} onClick={() => { if (window.confirm("삭제할까요? 되돌릴 수 없습니다.")) void apply(item.reportId, "DELETE"); }}>삭제</button>}
                  {item.status === "PENDING" && item.targetState !== "HELD" && <button className="admin-button" type="button" disabled={busy} onClick={() => void apply(item.reportId, "DISMISS")}>기각</button>}
                </span>
              </div>
            );
          })}
        </div>
        {data && data.totalPages > 1 && (
          <div className="admin-actions">
            <button className="admin-button" type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
            <span className="admin-meta">{page + 1} / {data.totalPages}</span>
            <button className="admin-button" type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>다음</button>
          </div>
        )}
      </section>
    </>
  );
}
