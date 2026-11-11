"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { API_ENDPOINTS, type AdminPostFilter, type AdminPostItem } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { AdminPager } from "./AdminPager";
import { formatDateTime, postHref, readErrorMessage } from "./adminTypes";
import { useAdminFetch } from "./useAdminFetch";

interface SpringPage<T> {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
}

type Board = "MARKET" | "CARPOOL";
type MarketStatus = "NORMAL" | "HIDDEN" | "BLOCKED";

const STATUS_LABEL: Record<string, string> = {
  NORMAL: "공개",
  HIDDEN: "숨김",
  BLOCKED: "차단",
  TEMPORARY_HOLD: "임시조치",
};

const FILTERS: Record<Board, { value: AdminPostFilter; label: string }[]> = {
  MARKET: [
    { value: "ALL", label: "전체" },
    { value: "HIDDEN", label: "숨김" },
    { value: "BLINDED", label: "차단" },
    { value: "HELD", label: "임시조치" },
    { value: "DELETED", label: "삭제됨" },
  ],
  CARPOOL: [
    { value: "ALL", label: "전체" },
    { value: "BLINDED", label: "블라인드" },
    { value: "HELD", label: "임시조치" },
    { value: "DELETED", label: "삭제됨" },
  ],
};

/**
 * 시장과 카풀 글 관리 (IKA-78). 목록은 관리자 게시글 API를 게시판 코드로 걸러 쓴다.
 * 시장은 기존 시장 관리자 API로 공개, 숨김, 차단을 바꾸고, 카풀은 게시글 블라인드를 쓴다. 신고 임시조치 글은 신고 처리 화면에서 다룬다.
 */
export function AdminBoardPosts({ board, title, description }: { board: Board; title: string; description: string }) {
  const [filter, setFilter] = useState<AdminPostFilter>("ALL");
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const list = useAdminFetch<SpringPage<AdminPostItem>>(
    API_ENDPOINTS.admin.posts(filter, page, 20, board),
    "글 목록을 불러오지 못했습니다.",
  );

  const run = async (post: AdminPostItem, message: string, request: () => Promise<Response>, failure: string) => {
    if (!window.confirm(message)) return;
    setBusyId(post.publicId);
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

  const changeMarketStatus = (post: AdminPostItem, status: MarketStatus) =>
    void run(
      post,
      `"${post.title}" 글을 ${STATUS_LABEL[status]} 상태로 바꿀까요?`,
      () =>
        csrfFetch(API_ENDPOINTS.admin.marketModeration(post.publicId), {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ moderationStatus: status }),
        }),
      "글 상태를 바꾸지 못했습니다.",
    );

  const toggleBlind = (post: AdminPostItem) => {
    const blinded = !post.isBlinded;
    void run(
      post,
      blinded ? `"${post.title}" 글을 블라인드할까요? 목록과 상세 화면에서 보이지 않게 됩니다.` : `"${post.title}" 글의 블라인드를 해제할까요?`,
      () =>
        csrfFetch(API_ENDPOINTS.admin.blindPost(post.publicId), {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ blinded }),
        }),
      "블라인드 상태를 바꾸지 못했습니다.",
    );
  };

  const remove = (post: AdminPostItem) =>
    void run(
      post,
      `"${post.title}" 글을 삭제할까요? 첨부 사진도 정리되어 되돌릴 수 없습니다.`,
      () => csrfFetch(API_ENDPOINTS.admin.deletePost(post.publicId), { method: "DELETE" }),
      "글을 삭제하지 못했습니다.",
    );

  const data = list.data;

  return (
    <>
      <PageHeading title={title} description={description} />
      <section className="panel admin-section" aria-label={`${title} 목록`}>
        <div className="admin-filter" role="group" aria-label="상태 필터">
          {FILTERS[board].map((item) => (
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
          {data && <span className="admin-meta">{data.totalElements.toLocaleString()}건</span>}
        </div>
        {list.error && (
          <p className="admin-error" role="alert">
            {list.error}
          </p>
        )}
        <div className="admin-list">
          {list.loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!list.loading && data?.content.length === 0 && <p className="admin-meta">해당하는 글이 없습니다.</p>}
          {data?.content.map((post) => {
            const busy = busyId === post.publicId;
            const held = post.status === "TEMPORARY_HOLD";
            const label = post.isDeleted ? "삭제됨" : (STATUS_LABEL[post.status] ?? post.status);
            const badge = post.isDeleted || post.status === "BLOCKED" ? "REJECTED" : post.status === "NORMAL" ? "APPROVED" : "DRAFT";
            return (
              <div className="admin-row" key={post.publicId}>
                <span className="admin-status" data-status={badge}>
                  {board === "CARPOOL" && post.isBlinded ? "블라인드" : label}
                </span>
                <span style={{ minWidth: 0 }}>
                  <p style={{ overflowWrap: "anywhere" }}>
                    {post.isDeleted || post.status !== "NORMAL" ? post.title : <Link href={postHref(board, post.publicId)}>{post.title}</Link>}
                  </p>
                  <small style={{ overflowWrap: "anywhere" }}>
                    {post.writerNickname ?? "비회원"}, 작성 {formatDateTime(post.createdAt)}
                    {post.isDeleted && `, 삭제 ${formatDateTime(post.deletedAt)}`}
                  </small>
                </span>
                {!post.isDeleted && (
                  <span className="admin-actions" style={{ justifyContent: "flex-end" }}>
                    {board === "MARKET" ? (
                      (["NORMAL", "HIDDEN", "BLOCKED"] as const).map((status) => (
                        <button
                          key={status}
                          className="admin-button"
                          type="button"
                          disabled={busy || held || post.status === status}
                          onClick={() => changeMarketStatus(post, status)}
                        >
                          {STATUS_LABEL[status]}
                        </button>
                      ))
                    ) : (
                      <button className="admin-button" type="button" disabled={busy || held} onClick={() => toggleBlind(post)}>
                        {post.isBlinded ? "블라인드 해제" : "블라인드"}
                      </button>
                    )}
                    <button className="admin-button danger" type="button" disabled={busy} onClick={() => remove(post)}>
                      삭제
                    </button>
                  </span>
                )}
              </div>
            );
          })}
        </div>
        <AdminPager
          page={page}
          hasNext={!!data && page + 1 < data.totalPages}
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
