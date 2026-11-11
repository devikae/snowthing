"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { type AdminCommentItem, type AdminPage, formatDateTime, postHref, readErrorMessage } from "../adminTypes";
import { AdminPager as Pager } from "../AdminPager";
import { useAdminFetch } from "../useAdminFetch";

const FILTERS = [
  { value: "ALL", label: "전체" },
  { value: "VISIBLE", label: "보이는 댓글" },
  { value: "HELD", label: "가린 댓글" },
  { value: "DELETED", label: "삭제됨" },
];

function statusOf(comment: AdminCommentItem) {
  if (comment.isHeld) return { label: "가림", badge: "REJECTED" };
  if (comment.isDeleted) return { label: "삭제됨", badge: "DRAFT" };
  return { label: "정상", badge: "APPROVED" };
}

/** 관리자 댓글 관리 (IKA-78). 블라인드는 신고 임시조치와 같은 가림 상태를 쓰고, 신고로 가린 댓글은 신고 처리 화면에서만 해제된다. */
export default function AdminCommentsPage() {
  const [filter, setFilter] = useState("ALL");
  const [queryInput, setQueryInput] = useState("");
  const [authorInput, setAuthorInput] = useState("");
  const [search, setSearch] = useState({ query: "", author: "" });
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const list = useAdminFetch<AdminPage<AdminCommentItem>>(
    API_ENDPOINTS.admin.comments({ filter, query: search.query, author: search.author, page }),
    "댓글 목록을 불러오지 못했습니다.",
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const next = { query: queryInput.trim(), author: authorInput.trim() };
    if (next.query === search.query && next.author === search.author && page === 0) return;
    list.setLoading(true);
    setPage(0);
    setSearch(next);
  };

  const run = async (comment: AdminCommentItem, message: string, request: () => Promise<Response>, failure: string) => {
    if (!window.confirm(message)) return;
    setBusyId(comment.commentId);
    list.setError(null);
    try {
      const response = await request();
      if (!response.ok) throw new Error(await readErrorMessage(response, failure));
      list.reload();
    } catch (cause) {
      list.setError((cause as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const toggleBlind = (comment: AdminCommentItem) => {
    const blinded = !comment.isHeld;
    void run(
      comment,
      blinded
        ? "이 댓글을 블라인드할까요? 글 화면과 검색에서 보이지 않게 됩니다."
        : "이 댓글의 블라인드를 해제할까요? 신고로 가린 댓글이면 신고 처리 화면에서 해제해야 합니다.",
      () =>
        csrfFetch(API_ENDPOINTS.admin.blindComment(comment.commentId), {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ blinded }),
        }),
      "블라인드 상태를 바꾸지 못했습니다.",
    );
  };

  const remove = (comment: AdminCommentItem) =>
    void run(
      comment,
      "이 댓글을 삭제할까요? 되돌릴 수 없습니다.",
      () => csrfFetch(API_ENDPOINTS.admin.deleteComment(comment.commentId), { method: "DELETE" }),
      "댓글을 삭제하지 못했습니다.",
    );

  const data = list.data;

  return (
    <>
      <PageHeading title="댓글 관리" description="내용이나 작성자 닉네임으로 댓글을 찾아 블라인드하거나 삭제합니다." />
      <section className="panel admin-section" aria-label="댓글 목록">
        <form className="admin-actions" role="search" onSubmit={submit}>
          <input
            type="search"
            value={queryInput}
            maxLength={100}
            aria-label="댓글 내용 검색"
            placeholder="댓글 내용"
            onChange={(event) => setQueryInput(event.target.value)}
          />
          <input
            type="search"
            value={authorInput}
            maxLength={100}
            aria-label="작성자 닉네임 검색"
            placeholder="작성자 닉네임"
            onChange={(event) => setAuthorInput(event.target.value)}
          />
          <button className="admin-button" type="submit">
            검색
          </button>
        </form>
        <div className="admin-filter" role="group" aria-label="댓글 필터">
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
          {!list.loading && data?.items.length === 0 && <p className="admin-meta">찾는 댓글이 없습니다.</p>}
          {data?.items.map((comment) => {
            const status = statusOf(comment);
            const busy = busyId === comment.commentId;
            return (
              <div className="admin-row" key={comment.commentId}>
                <span className="admin-status" data-status={status.badge}>
                  {status.label}
                </span>
                <span style={{ minWidth: 0 }}>
                  <p style={{ whiteSpace: "normal", overflowWrap: "anywhere" }}>{comment.contentPreview}</p>
                  <small style={{ overflowWrap: "anywhere" }}>
                    {comment.isAnonymous ? "익명" : comment.writerPublicId ? (
                      <Link href={`/admin/members/${comment.writerPublicId}`}>{comment.writerNickname}</Link>
                    ) : (
                      "비회원"
                    )}
                    , <Link href={postHref(comment.postCategoryCode, comment.postPublicId)}>{comment.postTitle}</Link>, 작성{" "}
                    {formatDateTime(comment.createdAt)}
                  </small>
                </span>
                {(!comment.isDeleted || comment.isHeld) && (
                  <span className="admin-actions" style={{ justifyContent: "flex-end" }}>
                    <button className="admin-button" type="button" disabled={busy} onClick={() => toggleBlind(comment)}>
                      {comment.isHeld ? "블라인드 해제" : "블라인드"}
                    </button>
                    <button className="admin-button danger" type="button" disabled={busy} onClick={() => remove(comment)}>
                      삭제
                    </button>
                  </span>
                )}
              </div>
            );
          })}
        </div>
        <Pager
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
