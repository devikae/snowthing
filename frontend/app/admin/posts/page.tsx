"use client";

import React, { useEffect, useState } from "react";
import { API_ENDPOINTS, AdminPostItem } from "../../lib/api";

export default function AdminPostsPage() {
  const [posts, setPosts] = useState<AdminPostItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterDeleted, setFilterDeleted] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Content Preview Modal
  const [viewingPost, setViewingPost] = useState<AdminPostItem | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  async function fetchPosts(pageIndex = 0) {
    try {
      setLoading(true);
      const res = await fetch(
        API_ENDPOINTS.admin.posts(filterDeleted, pageIndex, 20),
        { credentials: "include" }
      );
      if (!res.ok) throw new Error("게시글 목록을 불러오지 못했습니다.");
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
    fetchPosts(0);
  }, [filterDeleted]);

  async function handleToggleBlind(post: AdminPostItem) {
    const nextBlind = !post.isBlinded;
    const promptMsg = nextBlind
      ? `[${post.title}] 글을 블라인드(숨김) 처리하시겠습니까?\n일반 유저 목록에서 내용이 가려집니다.`
      : `[${post.title}] 글의 블라인드를 해제하시겠습니까?`;

    if (!window.confirm(promptMsg)) return;

    try {
      setActionLoading(true);
      const res = await fetch(API_ENDPOINTS.admin.blindPost(post.publicId), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ blinded: nextBlind }),
      });
      if (!res.ok) throw new Error("블라인드 설정에 실패했습니다.");
      setPosts((prev) =>
        prev.map((p) =>
          p.publicId === post.publicId ? { ...p, isBlinded: nextBlind } : p
        )
      );
    } catch (err: any) {
      alert(err.message || "처리 실패");
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-slate-900 tracking-tight">
            게시글 관리 및 블라인드
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            삭제된 글({totalElements.toLocaleString()}건) 원본 대조 및 불량 게시글 블라인드 제어
          </p>
        </div>

        {/* Tab Filters */}
        <div className="inline-flex bg-slate-200/80 p-1 rounded-xl">
          <button
            onClick={() => setFilterDeleted(false)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              !filterDeleted
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            전체 게시글
          </button>
          <button
            onClick={() => setFilterDeleted(true)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
              filterDeleted
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-600 hover:text-slate-900"
            }`}
          >
            🗑️ 사용자가 삭제한 글만
          </button>
        </div>
      </div>

      {/* Posts Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-xs uppercase font-semibold text-slate-500">
              <tr>
                <th className="py-3.5 px-4">분류 / 리조트</th>
                <th className="py-3.5 px-4">제목</th>
                <th className="py-3.5 px-4">작성자</th>
                <th className="py-3.5 px-4">상태</th>
                <th className="py-3.5 px-4">작성일시</th>
                <th className="py-3.5 px-4 text-right">관리 작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    게시글을 불러오는 중입니다...
                  </td>
                </tr>
              ) : posts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    게시글이 존재하지 않습니다.
                  </td>
                </tr>
              ) : (
                posts.map((p) => (
                  <tr key={p.publicId} className="hover:bg-slate-50/70 transition">
                    <td className="py-3.5 px-4 text-xs">
                      <span className="font-semibold text-slate-800">
                        {p.category}
                      </span>
                      {p.resortName && (
                        <span className="block text-[11px] text-sky-600 font-medium">
                          {p.resortName}
                        </span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 max-w-xs truncate font-medium text-slate-900">
                      {p.title}
                    </td>
                    <td className="py-3.5 px-4 text-xs">
                      <p className="font-semibold text-slate-800">{p.authorNickname}</p>
                      <p className="text-[11px] text-slate-400 font-mono">{p.authorEmail}</p>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex gap-1.5 flex-wrap">
                        {p.isDeleted && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-rose-100 text-rose-700">
                            삭제됨
                          </span>
                        )}
                        {p.isBlinded && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-100 text-amber-800">
                            블라인드
                          </span>
                        )}
                        {!p.isDeleted && !p.isBlinded && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-100 text-emerald-700">
                            정상 노출
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400 font-mono">
                      {p.createdAt ? p.createdAt.slice(0, 16).replace("T", " ") : "-"}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => setViewingPost(p)}
                          className="px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
                        >
                          원문 보기
                        </button>
                        <button
                          onClick={() => handleToggleBlind(p)}
                          className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                            p.isBlinded
                              ? "bg-emerald-600 hover:bg-emerald-700 text-white"
                              : "bg-amber-600 hover:bg-amber-700 text-white"
                          }`}
                        >
                          {p.isBlinded ? "블라인드 해제" : "블라인드"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="p-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <div>
              페이지 {page + 1} / {totalPages}
            </div>
            <div className="flex gap-1">
              <button
                disabled={page === 0}
                onClick={() => fetchPosts(page - 1)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50 transition"
              >
                이전
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => fetchPosts(page + 1)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50 transition"
              >
                다음
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Post Detail Modal */}
      {viewingPost && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white max-w-2xl w-full rounded-2xl shadow-2xl border border-slate-200 p-6 max-h-[85vh] flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <span className="text-xs text-sky-600 font-bold uppercase">
                  {viewingPost.category}
                </span>
                <h3 className="text-lg font-bold text-slate-900">
                  {viewingPost.title}
                </h3>
              </div>
              <button
                onClick={() => setViewingPost(null)}
                className="text-slate-400 hover:text-slate-600 text-lg font-bold"
              >
                ✕
              </button>
            </div>

            <div className="py-2 text-xs text-slate-400 flex items-center gap-3">
              <span>작성자: {viewingPost.authorNickname} ({viewingPost.authorEmail})</span>
              <span>작성일시: {viewingPost.createdAt}</span>
            </div>

            <div className="my-4 p-4 bg-slate-50 rounded-xl text-sm text-slate-800 leading-relaxed overflow-y-auto whitespace-pre-wrap flex-1 border border-slate-200/60 font-sans">
              {viewingPost.content}
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                onClick={() => setViewingPost(null)}
                className="px-4 py-2 rounded-xl bg-slate-900 text-white text-xs font-semibold hover:bg-slate-800 transition"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
