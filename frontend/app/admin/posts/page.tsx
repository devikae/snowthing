"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS, type AdminPostFilter, type AdminPostItem } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { formatDateTime, readErrorMessage } from "../adminTypes";

interface AdminPostPage {
  content: AdminPostItem[];
  number: number;
  totalPages: number;
  totalElements: number;
}

const FILTERS: { value: AdminPostFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "BLINDED", label: "블라인드" },
  { value: "DELETED", label: "삭제됨" },
];

function statusLabel(post: AdminPostItem) {
  if (post.isDeleted) {
    if (post.isDeletedByAuthor) return "작성자 삭제";
    // 비회원 작성자의 삭제와 기록 전 삭제는 지운 회원이 없다.
    return post.deleterRole === "ROLE_ADMIN" ? "관리자 삭제" : "삭제됨";
  }
  if (post.isBlinded) return "블라인드";
  if (post.status === "TEMPORARY_HOLD") return "임시조치";
  if (post.status === "NORMAL") return "정상";
  return post.status;
}

function statusBadge(post: AdminPostItem) {
  if (post.isDeleted || post.isBlinded) return "REJECTED";
  if (post.status === "NORMAL") return "APPROVED";
  return "DRAFT";
}

/** 관리자 게시글 관리. 블라인드는 글 상태를 BLOCKED로 바꾸고, 삭제는 일반 삭제와 같은 흐름을 탄다. 임시조치 글은 신고 처리에서 다룬다. */
export default function AdminPostsPage() {
  const [filter, setFilter] = useState<AdminPostFilter>("ALL");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminPostPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.admin.posts(filter, page), { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await readErrorMessage(response, "게시글 목록을 불러오지 못했습니다."));
        return (await response.json()) as AdminPostPage;
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
  }, [filter, page, reloadKey]);

  const changeFilter = (next: AdminPostFilter) => {
    if (next === filter) return;
    setLoading(true);
    setPage(0);
    setFilter(next);
  };

  const run = async (post: AdminPostItem, request: () => Promise<Response>, fallback: string) => {
    setBusyId(post.publicId);
    setError(null);
    try {
      const response = await request();
      if (!response.ok) throw new Error(await readErrorMessage(response, fallback));
      setLoading(true);
      setReloadKey((current) => current + 1);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const toggleBlind = (post: AdminPostItem) => {
    const blinded = !post.isBlinded;
    const message = blinded
      ? `"${post.title}" 글을 블라인드할까요? 목록과 상세 화면, 검색에서 보이지 않게 됩니다.`
      : `"${post.title}" 글의 블라인드를 해제할까요?`;
    if (!window.confirm(message)) return;
    void run(
      post,
      () =>
        csrfFetch(API_ENDPOINTS.admin.blindPost(post.publicId), {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ blinded }),
        }),
      "블라인드 상태를 바꾸지 못했습니다.",
    );
  };

  const remove = (post: AdminPostItem) => {
    if (!window.confirm(`"${post.title}" 글을 삭제할까요? 첨부 사진도 정리되어 되돌릴 수 없습니다.`)) return;
    void run(
      post,
      () => csrfFetch(API_ENDPOINTS.admin.deletePost(post.publicId), { method: "DELETE" }),
      "게시글을 삭제하지 못했습니다.",
    );
  };

  return (
    <>
      <PageHeading
        title="게시글 관리"
        description="글을 블라인드하거나 삭제하고, 누가 지웠는지 확인합니다. 신고로 임시조치된 글은 신고 처리 화면에서 다룹니다."
      />
      <section className="panel admin-section" aria-label="게시글 목록">
        <div className="admin-filter" role="group" aria-label="게시글 필터">
          {FILTERS.map((item) => (
            <button key={item.value} type="button" aria-pressed={filter === item.value} onClick={() => changeFilter(item.value)}>
              {item.label}
            </button>
          ))}
          {data && <span className="admin-meta">{data.totalElements.toLocaleString()}건</span>}
        </div>
        {error && <p className="admin-error" role="alert">{error}</p>}
        <div className="admin-list">
          {loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!loading && data?.content.length === 0 && <p className="admin-meta">해당하는 게시글이 없습니다.</p>}
          {data?.content.map((post) => (
            <div className="admin-row" key={post.publicId}>
              <span className="admin-status" data-status={statusBadge(post)}>
                {statusLabel(post)}
              </span>
              <span style={{ minWidth: 0 }}>
                <p style={{ overflowWrap: "anywhere" }}>
                  {post.isDeleted ? post.title : <Link href={`/posts/${post.publicId}`}>{post.title}</Link>}
                </p>
                <small style={{ overflowWrap: "anywhere" }}>
                  {post.categoryName}, {post.isAnonymous ? "익명" : post.writerNickname ?? "비회원"}
                  {post.writerEmail ? ` (${post.writerEmail})` : ""}, 작성 {formatDateTime(post.createdAt)}
                  {post.isDeleted &&
                    `, 삭제 ${formatDateTime(post.deletedAt)}${post.deleterNickname ? ` (${post.deleterNickname}${post.deleterRole === "ROLE_ADMIN" ? ", 관리자" : ""})` : ""}`}
                </small>
              </span>
              {!post.isDeleted && (
                <span className="admin-actions">
                  <button
                    className="admin-button"
                    type="button"
                    disabled={busyId === post.publicId || post.status === "TEMPORARY_HOLD"}
                    onClick={() => toggleBlind(post)}
                  >
                    {post.isBlinded ? "블라인드 해제" : "블라인드"}
                  </button>
                  <button
                    className="admin-button danger"
                    type="button"
                    disabled={busyId === post.publicId}
                    onClick={() => remove(post)}
                  >
                    삭제
                  </button>
                </span>
              )}
            </div>
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
          <span className="admin-meta">
            {page + 1}
            {data ? ` / ${Math.max(data.totalPages, 1)}` : ""}쪽
          </span>
          <button
            className="admin-button"
            type="button"
            disabled={!data || page + 1 >= data.totalPages || loading}
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
