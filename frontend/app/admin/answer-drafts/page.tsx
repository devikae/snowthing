"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import {
  ANSWER_DRAFT_STATUS_LABEL,
  type AnswerDraftListResponse,
  type AnswerDraftStatus,
  formatDateTime,
  readErrorMessage,
} from "../adminTypes";

const FILTERS: Array<{ value: "" | AnswerDraftStatus; label: string }> = [
  { value: "REVIEW_REQUIRED", label: "검토 대기" },
  { value: "APPROVED", label: "승인됨" },
  { value: "PUBLISHED", label: "게시됨" },
  { value: "REJECTED", label: "거절됨" },
  { value: "", label: "전체" },
];

export default function AnswerDraftListPage() {
  const [status, setStatus] = useState<"" | AnswerDraftStatus>("REVIEW_REQUIRED");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AnswerDraftListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.admin.answerDrafts(status, page), { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await readErrorMessage(response, "초안 목록을 불러오지 못했습니다."));
        return (await response.json()) as AnswerDraftListResponse;
      })
      .then((body) => {
        if (cancelled) return;
        setData(body);
        setError(null);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [status, page]);

  return (
    <>
      <PageHeading title="답변 초안 검토" description="AI가 저장한 답변 초안을 사람이 확인하고 승인, 거절, 게시합니다." />
      {data && !data.publishEnabled && (
        <p className="admin-notice">게시 기능이 꺼져 있습니다. 승인까지는 할 수 있지만 댓글로 게시되지 않습니다.</p>
      )}
      <section className="panel admin-section" aria-label="답변 초안 목록">
        <div className="admin-toolbar">
          <div className="admin-filter" role="group" aria-label="상태 필터">
            {FILTERS.map((filter) => (
              <button
                key={filter.label}
                type="button"
                aria-pressed={status === filter.value}
                onClick={() => {
                  setLoading(true);
                  setPage(0);
                  setStatus(filter.value);
                }}
              >
                {filter.label}
              </button>
            ))}
          </div>
          <span className="admin-meta">{loading ? "불러오는 중..." : `${data?.items.length ?? 0}건 표시`}</span>
        </div>
        {error && <p className="admin-error" role="alert">{error}</p>}
        <div className="admin-list">
          {!loading && data?.items.length === 0 && <p className="admin-meta">해당 상태의 초안이 없습니다.</p>}
          {data?.items.map((item) => (
            <Link key={item.draftId} className="admin-row" href={`/admin/answer-drafts/${item.draftId}`}>
              <span className="admin-status" data-status={item.status}>
                {ANSWER_DRAFT_STATUS_LABEL[item.status]}
              </span>
              <span style={{ minWidth: 0 }}>
                <p>{item.bodyPreview}</p>
                <small>
                  근거 {item.evidenceCount}개, {item.createdByNickname}
                  {item.createdByToken ? " (API 토큰)" : ""}
                </small>
              </span>
              <small>{formatDateTime(item.createdAt)}</small>
            </Link>
          ))}
        </div>
        <div className="admin-actions" style={{ justifyContent: "center" }}>
          <button
            className="admin-button"
            type="button"
            disabled={page === 0 || loading}
            onClick={() => {
              setLoading(true);
              setPage((current) => current - 1);
            }}
          >
            이전
          </button>
          <span className="admin-meta">{page + 1}쪽</span>
          <button
            className="admin-button"
            type="button"
            disabled={!data?.hasNext || loading}
            onClick={() => {
              setLoading(true);
              setPage((current) => current + 1);
            }}
          >
            다음
          </button>
        </div>
      </section>
    </>
  );
}
