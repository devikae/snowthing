"use client";

import { useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { AdminPager } from "../AdminPager";
import { type AdminPage, type AdminResortReportItem, formatDateTime, readErrorMessage } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

const FILTERS = [
  { value: "ALL", label: "전체" },
  { value: "NORMAL", label: "공개" },
  { value: "HIDDEN", label: "숨김" },
  { value: "BLOCKED", label: "차단" },
  { value: "DELETED", label: "삭제됨" },
];

const STATUS_LABEL: Record<AdminResortReportItem["status"], string> = {
  NORMAL: "공개",
  HIDDEN: "숨김",
  BLOCKED: "차단",
  DELETED: "삭제됨",
};

/** 설질 제보 관리 (IKA-78). 상태 변경은 기존 설질 제보 관리자 API를 쓴다. */
export default function AdminResortReportsPage() {
  const [filter, setFilter] = useState("ALL");
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const list = useAdminFetch<AdminPage<AdminResortReportItem>>(
    API_ENDPOINTS.admin.resortReports(filter, page),
    "설질 제보 목록을 불러오지 못했습니다.",
  );

  const changeStatus = async (report: AdminResortReportItem, status: "NORMAL" | "HIDDEN" | "BLOCKED") => {
    if (!window.confirm(`${report.resortName} 제보를 ${STATUS_LABEL[status]} 상태로 바꿀까요?`)) return;
    setBusyId(report.reportId);
    list.setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.resortReportModeration(report.reportId), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ moderationStatus: status }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "제보 상태를 바꾸지 못했습니다."));
      list.reload();
    } catch (cause) {
      list.setError((cause as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const data = list.data;

  return (
    <>
      <PageHeading title="설질 제보 관리" description="리조트 설질 제보를 공개, 숨김, 차단 상태로 바꿉니다." />
      <section className="panel admin-section" aria-label="설질 제보 목록">
        <div className="admin-filter" role="group" aria-label="상태 필터">
          {FILTERS.map((item) => (
            <button
              key={item.value}
              type="button"
              aria-pressed={filter === item.value}
              onClick={() => {
                if (filter === item.value) return;
                list.setLoading(true);
                setPage(0);
                setFilter(item.value);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        {list.error && (
          <p className="admin-error" role="alert">
            {list.error}
          </p>
        )}
        <div className="admin-list">
          {list.loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!list.loading && data?.items.length === 0 && <p className="admin-meta">해당하는 제보가 없습니다.</p>}
          {data?.items.map((report) => (
            <div className="admin-row" key={report.reportId}>
              <span
                className="admin-status"
                data-status={report.status === "NORMAL" ? "APPROVED" : report.status === "BLOCKED" ? "REJECTED" : "DRAFT"}
              >
                {STATUS_LABEL[report.status]}
              </span>
              <span style={{ minWidth: 0 }}>
                <p style={{ whiteSpace: "normal", overflowWrap: "anywhere" }}>{report.content}</p>
                <small>
                  {report.resortName}, {report.writerNickname ?? "알 수 없음"}, {formatDateTime(report.createdAt)}
                </small>
              </span>
              {report.status !== "DELETED" && (
                <span className="admin-actions" style={{ justifyContent: "flex-end" }}>
                  {(["NORMAL", "HIDDEN", "BLOCKED"] as const).map((status) => (
                    <button
                      key={status}
                      className="admin-button"
                      type="button"
                      disabled={busyId === report.reportId || report.status === status}
                      onClick={() => void changeStatus(report, status)}
                    >
                      {STATUS_LABEL[status]}
                    </button>
                  ))}
                </span>
              )}
            </div>
          ))}
        </div>
        <AdminPager
          page={page}
          hasNext={!!data?.hasNext}
          loading={list.loading}
          onChange={(next) => {
            list.setLoading(true);
            setPage(next);
          }}
        />
      </section>
    </>
  );
}
