"use client";

import React, { useEffect, useState } from "react";
import { API_ENDPOINTS, AdminPostItem } from "../../lib/api";

export default function AdminPostsPage() {
  const [posts, setPosts] = useState<AdminPostItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Content Preview Modal
  const [viewingPost, setViewingPost] = useState<AdminPostItem | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  async function fetchDeletedPosts(pageIndex = 0) {
    try {
      setLoading(true);
      // onlyDeleted = true (소프트 삭제된 글 전용 조회)
      const res = await fetch(
        API_ENDPOINTS.admin.posts(true, pageIndex, 20),
        { credentials: "include" }
      );
      if (!res.ok) throw new Error("삭제된 게시글 목록을 불러오지 못했습니다.");
      const data = await res.json();
      setPosts(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
      setPage(data.number || 0);
    } catch (err: any) {
      alert(err.message || "오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchDeletedPosts(0);
  }, []);

  async function handleRestore(post: AdminPostItem) {
    if (!window.confirm(`'${post.title}' 게시글을 정상 상태로 복구하시겠습니까?\n복구 시 메인 피드와 커뮤니티 목록에 다시 노출됩니다.`)) {
      return;
    }
    try {
      setActionLoading(true);
      const res = await fetch(API_ENDPOINTS.admin.restorePost(post.publicId), {
        method: "PATCH",
        credentials: "include",
      });
      if (!res.ok) throw new Error("게시글 복구에 실패했습니다.");
      alert("게시글이 성공적으로 복구되었습니다.");
      // 목록 갱신
      fetchDeletedPosts(page);
    } catch (err: any) {
      alert(err.message || "복구 실패");
    } finally {
      setActionLoading(false);
    }
  }

  const formatDateTime = (dateStr: string | null | undefined) => {
    if (!dateStr) return "-";
    return dateStr.slice(0, 19).replace("T", " ");
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto font-sans">
      {/* Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xl">🗑️</span>
            <h2 className="text-xl font-black text-[#0f2942] tracking-tight">
              소프트 삭제 게시글 감사 관리
            </h2>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            소프트 삭제(블라인드/가리기)된 글 <strong className="text-[#0284c7] font-bold">{totalElements.toLocaleString()}건</strong>의 삭제 주체 및 이력 추적
          </p>
        </div>

        <button
          onClick={() => fetchDeletedPosts(page)}
          className="self-start sm:self-auto px-3.5 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold shadow-xs transition flex items-center gap-1.5"
        >
          <span>🔄</span>
          <span>새로고침</span>
        </button>
      </div>

      {/* Posts Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-[#f8fafc] border-b border-slate-200 text-[11px] font-bold text-slate-500">
              <tr>
                <th className="py-3 px-4">카테고리</th>
                <th className="py-3 px-4">제목</th>
                <th className="py-3 px-4">작성자</th>
                <th className="py-3 px-4">삭제 구분 및 감사자</th>
                <th className="py-3 px-4">삭제 일시</th>
                <th className="py-3 px-4 text-right">관리 작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 font-medium">
                    삭제된 게시글 목록을 불러오는 중입니다...
                  </td>
                </tr>
              ) : posts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 font-medium">
                    소프트 삭제된 게시글이 없습니다.
                  </td>
                </tr>
              ) : (
                posts.map((p) => {
                  const isAuthorDelete = p.isDeletedByAuthor;
                  const hasDeleter = Boolean(p.deletedByMemberId);

                  return (
                    <tr key={p.publicId} className="hover:bg-slate-50/80 transition">
                      <td className="py-3 px-4">
                        <span className="font-bold text-[#0f2942]">
                          {p.categoryName || p.category || "일반"}
                        </span>
                      </td>
                      <td className="py-3 px-4 max-w-xs truncate font-semibold text-slate-900">
                        {p.title}
                      </td>
                      <td className="py-3 px-4">
                        <p className="font-bold text-slate-800">
                          {p.writerNickname || p.authorNickname || (p.isAnonymous ? "익명 작성자" : "알 수 없음")}
                        </p>
                        {p.writerEmail && (
                          <p className="text-[10px] text-slate-400 font-mono">{p.writerEmail}</p>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        {isAuthorDelete ? (
                          <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
                            <span>👤</span>
                            <span>작성자 본인 자진 삭제</span>
                          </div>
                        ) : hasDeleter ? (
                          <div className="flex flex-col gap-0.5">
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-rose-50 text-rose-700 border border-rose-200 w-fit">
                              <span>🛡️</span>
                              <span>관리자 가리기/삭제</span>
                            </span>
                            <span className="text-[10px] text-slate-500 font-medium">
                              삭제자: <strong className="text-slate-800">{p.deleterNickname || "관리자"}</strong>
                              {p.deleterEmail && ` (${p.deleterEmail})`}
                            </span>
                          </div>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                            <span>🔑</span>
                            <span>익명/비회원 비밀번호 삭제</span>
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-500">
                        {formatDateTime(p.deletedAt || p.createdAt)}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            type="button"
                            onClick={() => setViewingPost(p)}
                            className="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold transition"
                          >
                            원문 보기
                          </button>
                          <button
                            type="button"
                            disabled={actionLoading}
                            onClick={() => handleRestore(p)}
                            className="px-2.5 py-1 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-white text-xs font-bold transition shadow-2xs disabled:opacity-50"
                          >
                            복구
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-100 flex items-center justify-between">
            <span className="text-xs text-slate-400">
              {page + 1} / {totalPages} 페이지 (총 {totalElements.toLocaleString()}건)
            </span>
            <div className="flex gap-1.5">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => fetchDeletedPosts(page - 1)}
                className="px-3 py-1 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                이전
              </button>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => fetchDeletedPosts(page + 1)}
                className="px-3 py-1 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40"
              >
                다음
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Content Preview Modal */}
      {viewingPost && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full max-h-[85vh] flex flex-col shadow-xl border border-slate-200 overflow-hidden">
            {/* Modal Header */}
            <div className="p-5 border-b border-slate-100 flex items-start justify-between bg-[#f8fafc]">
              <div>
                <div className="flex items-center gap-2 mb-1.5">
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-200 text-slate-700">
                    {viewingPost.categoryName || viewingPost.category || "일반"}
                  </span>
                  {viewingPost.isDeletedByAuthor ? (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                      작성자 자진 삭제
                    </span>
                  ) : viewingPost.deletedByMemberId ? (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-rose-100 text-rose-700">
                      관리자 가리기/삭제 ({viewingPost.deleterNickname || "관리자"})
                    </span>
                  ) : null}
                </div>
                <h3 className="text-base font-bold text-[#0f2942]">
                  {viewingPost.title}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setViewingPost(null)}
                className="text-slate-400 hover:text-slate-700 text-lg font-bold p-1 rounded-lg hover:bg-slate-200/50"
              >
                ✕
              </button>
            </div>

            {/* Modal Info Bar */}
            <div className="px-5 py-2.5 bg-slate-50 border-b border-slate-100 text-xs text-slate-500 flex flex-wrap gap-4">
              <div>
                작성자: <strong className="text-slate-800">{viewingPost.writerNickname || "익명"}</strong>
              </div>
              <div>
                작성일: <span className="font-mono">{formatDateTime(viewingPost.createdAt)}</span>
              </div>
              <div>
                삭제일: <span className="font-mono text-rose-600 font-bold">{formatDateTime(viewingPost.deletedAt)}</span>
              </div>
            </div>

            {/* Modal Body */}
            <div className="p-5 flex-1 overflow-y-auto">
              <div className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed bg-[#f8fafc] p-4 rounded-xl border border-slate-100">
                {viewingPost.content}
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-slate-100 bg-[#f8fafc] flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setViewingPost(null)}
                className="px-4 py-2 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-white"
              >
                닫기
              </button>
              <button
                type="button"
                disabled={actionLoading}
                onClick={async () => {
                  await handleRestore(viewingPost);
                  setViewingPost(null);
                }}
                className="px-4 py-2 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-xs font-bold text-white shadow-xs"
              >
                이 게시글 복구하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
