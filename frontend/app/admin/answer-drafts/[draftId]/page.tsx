"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { PageHeading } from "../../../components/PageHeading";
import { API_ENDPOINTS } from "../../../lib/api";
import { csrfFetch } from "../../../lib/csrfFetch";
import {
  ANSWER_DRAFT_STATUS_LABEL,
  type AnswerDraftDetail,
  formatDateTime,
  readErrorMessage,
} from "../../adminTypes";

const EVIDENCE_STATE_LABEL = {
  MATCH: "저장 시점과 같음",
  CHANGED: "원문이 바뀜",
  UNAVAILABLE: "볼 수 없음",
} as const;

export default function AnswerDraftDetailPage() {
  const params = useParams<{ draftId: string }>();
  const draftId = params.draftId;
  const [draft, setDraft] = useState<AnswerDraftDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const fetchDetail = useCallback(async () => {
    const response = await fetch(API_ENDPOINTS.admin.answerDraft(draftId), { credentials: "include" });
    if (!response.ok) throw new Error(await readErrorMessage(response, "초안을 불러오지 못했습니다."));
    return (await response.json()) as AnswerDraftDetail;
  }, [draftId]);

  useEffect(() => {
    let cancelled = false;
    fetchDetail()
      .then((body) => {
        if (!cancelled) setDraft(body);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [fetchDetail]);

  const runAction = async (action: "approve" | "reject" | "publish", successMessage: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.answerDraftAction(draftId, action), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: action === "reject" ? JSON.stringify({ reason: rejectReason }) : undefined,
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "요청을 처리하지 못했습니다."));
      setNotice(successMessage);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      try {
        setDraft(await fetchDetail());
      } catch {
        // 상세를 다시 못 읽어도 직전 화면은 유지한다.
      }
      setBusy(false);
    }
  };

  if (!draft) {
    return (
      <section className="panel admin-section">
        {error ? <p className="admin-error" role="alert">{error}</p> : <p className="admin-meta">불러오는 중...</p>}
      </section>
    );
  }

  const canApprove = draft.status === "REVIEW_REQUIRED";
  const canReject = draft.status === "REVIEW_REQUIRED" || draft.status === "APPROVED";
  const canPublish = draft.status === "APPROVED" && draft.publishEnabled;

  return (
    <>
      <PageHeading
        title="답변 초안 상세"
        description={`초안 ${draft.draftId}`}
        meta={
          <span className="admin-status" data-status={draft.status}>
            {ANSWER_DRAFT_STATUS_LABEL[draft.status]}
          </span>
        }
        actions={<Link href="/admin/answer-drafts">목록으로</Link>}
      />
      {error && <p className="admin-error" role="alert">{error}</p>}
      {notice && <p className="admin-notice" role="status">{notice}</p>}

      <section className="panel admin-section" aria-label="대상 게시글">
        <h2>대상 게시글</h2>
        {draft.target.visible && draft.target.url ? (
          <p>
            <Link className="admin-link" href={draft.target.url} target="_blank">
              {draft.target.title}
            </Link>{" "}
            <span className="admin-meta">현재 내용 버전 {draft.target.contentVersion}</span>
          </p>
        ) : (
          <p className="admin-error">대상 게시글이 삭제되었거나 볼 수 없습니다.</p>
        )}
        {draft.approvedPostContentVersion != null && (
          <p className="admin-meta">
            승인 시점 내용 버전 {draft.approvedPostContentVersion}
            {draft.target.contentVersion != null && draft.target.contentVersion !== draft.approvedPostContentVersion
              ? ". 승인 이후 게시글이 바뀌어 게시가 거절됩니다."
              : ""}
          </p>
        )}
      </section>

      <section className="panel admin-section" aria-label="초안 본문">
        <h2>본문</h2>
        <p className="admin-body">{draft.body}</p>
        <p className="admin-meta">
          작성 {draft.createdByNickname}
          {draft.createdByToken ? " (API 토큰)" : ""}, {formatDateTime(draft.createdAt)}
        </p>
      </section>

      <section className="panel admin-section" aria-label="근거">
        <h2>근거 {draft.evidence.length}개</h2>
        {draft.evidence.map((item) => (
          <article className="admin-evidence" key={item.evidenceId}>
            <header>
              <code>{item.evidenceId}</code>
              <span className="admin-evidence-state" data-state={item.currentStatus}>
                {EVIDENCE_STATE_LABEL[item.currentStatus]}
              </span>
              {item.url && (
                <Link href={item.url} target="_blank">
                  {item.title ?? "원문 보기"}
                </Link>
              )}
            </header>
            {item.text && <p>{item.text}</p>}
          </article>
        ))}
      </section>

      <section className="panel admin-section" aria-label="검토">
        <h2>검토</h2>
        {!draft.publishEnabled && (
          <p className="admin-notice">게시 기능이 꺼져 있어 게시 버튼을 쓸 수 없습니다. 승인과 거절은 할 수 있습니다.</p>
        )}
        <div className="admin-actions">
          <button
            className="admin-button primary"
            type="button"
            disabled={!canApprove || busy}
            onClick={() => void runAction("approve", "승인했습니다.")}
          >
            승인
          </button>
          <button
            className="admin-button"
            type="button"
            disabled={!canPublish || busy}
            title={draft.publishEnabled ? undefined : "게시 기능이 꺼져 있습니다"}
            onClick={() => {
              if (window.confirm("승인자 이름으로 댓글을 게시합니다. 계속할까요?")) {
                void runAction("publish", "댓글로 게시했습니다.");
              }
            }}
          >
            게시
          </button>
        </div>
        <div className="admin-actions">
          <input
            aria-label="거절 사유"
            placeholder="거절 사유 (선택, 255자 이하)"
            maxLength={255}
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            disabled={!canReject || busy}
          />
          <button
            className="admin-button danger"
            type="button"
            disabled={!canReject || busy}
            onClick={() => void runAction("reject", "거절했습니다.")}
          >
            거절
          </button>
        </div>
        <dl className="admin-meta">
          {draft.approvedByNickname && <dd>승인 {draft.approvedByNickname}, {formatDateTime(draft.approvedAt)}</dd>}
          {draft.rejectReason && <dd>거절 사유: {draft.rejectReason}</dd>}
          {draft.publishedCommentId && (
            <dd>
              게시 {draft.publishedByNickname}, {formatDateTime(draft.publishedAt)}, 댓글 {draft.publishedCommentId}
            </dd>
          )}
        </dl>
      </section>

      <section className="panel admin-section" aria-label="감사 기록">
        <h2>기록</h2>
        <ul className="admin-audit">
          {draft.audits.map((audit, index) => (
            <li key={`${audit.action}-${index}`}>
              {formatDateTime(audit.createdAt)} {audit.action}
              {audit.actorNickname ? `, ${audit.actorNickname}` : ""}
              {audit.viaToken ? " (API 토큰)" : ""}
              {audit.reasonCode ? `, ${audit.reasonCode}` : ""}
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
