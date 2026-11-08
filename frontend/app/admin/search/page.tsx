"use client";

import { useCallback, useEffect, useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { formatDateTime, readErrorMessage } from "../adminTypes";

interface OutboxSummary {
  pendingCount: number;
  doneCount: number;
  failedCount: number;
  oldestPendingCreatedAt: string | null;
  oldestPendingAgeSeconds: number | null;
  checkedAt: string;
}

interface ReconcileResult {
  postUpserts: number;
  postDeletes: number;
  commentUpserts: number;
  commentDeletes: number;
  enqueuedAt: string;
}

export default function SearchAdminPage() {
  const [summary, setSummary] = useState<OutboxSummary | null>(null);
  const [reconcile, setReconcile] = useState<ReconcileResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const loadSummary = useCallback(async () => {
    try {
      const response = await fetch(API_ENDPOINTS.admin.searchOutboxSummary, { credentials: "include" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "outbox 상태를 불러오지 못했습니다."));
      setSummary((await response.json()) as OutboxSummary);
    } catch (cause) {
      setError((cause as Error).message);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.admin.searchOutboxSummary, { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await readErrorMessage(response, "outbox 상태를 불러오지 못했습니다."));
        return (await response.json()) as OutboxSummary;
      })
      .then((body) => {
        if (!cancelled) setSummary(body);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const runReconcile = async () => {
    setRunning(true);
    setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.searchReconcile, { method: "POST" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "대조를 실행하지 못했습니다."));
      setReconcile((await response.json()) as ReconcileResult);
      await loadSummary();
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <>
      <PageHeading title="검색 인덱스" description="원문과 검색 색인을 대조하고 outbox 처리 상태를 확인합니다." />
      {error && <p className="admin-error" role="alert">{error}</p>}
      <section className="panel admin-section" aria-label="outbox 상태">
        <div className="admin-toolbar">
          <h2>outbox 상태</h2>
          <button className="admin-button" type="button" onClick={() => void loadSummary()}>
            새로고침
          </button>
        </div>
        {summary ? (
          <ul className="admin-audit">
            <li>대기(PENDING): {summary.pendingCount}</li>
            <li>완료(DONE): {summary.doneCount}</li>
            <li>실패(FAILED): {summary.failedCount}</li>
            <li>
              가장 오래 기다린 대기 이벤트: {formatDateTime(summary.oldestPendingCreatedAt)}
              {summary.oldestPendingAgeSeconds != null ? ` (${summary.oldestPendingAgeSeconds}초)` : ""}
            </li>
            <li>확인 시각: {formatDateTime(summary.checkedAt)}</li>
          </ul>
        ) : (
          <p className="admin-meta">불러오는 중...</p>
        )}
      </section>
      <section className="panel admin-section" aria-label="검색 인덱스 대조">
        <div className="admin-toolbar">
          <h2>대조</h2>
          <button className="admin-button primary" type="button" disabled={running} onClick={() => void runReconcile()}>
            {running ? "실행 중..." : "검색 인덱스 대조 실행"}
          </button>
        </div>
        <p className="admin-meta">어긋난 원문을 outbox에 넣습니다. 이미 대기 중인 원문은 세지 않습니다.</p>
        {reconcile && <pre className="admin-body">{JSON.stringify(reconcile, null, 2)}</pre>}
      </section>
    </>
  );
}
