"use client";

import React, { useCallback, useEffect, useState } from "react";
import Image from "next/image";
import { API_ENDPOINTS, AdminMemberItem } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";

export default function AdminMembersPage() {
  const [members, setMembers] = useState<AdminMemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Nickname Edit Modal State
  const [selectedMember, setSelectedMember] = useState<AdminMemberItem | null>(null);
  const [newNickname, setNewNickname] = useState("");
  const [actionLoading, setActionLoading] = useState(false);

  const fetchMembers = useCallback((pageIndex = 0, signal?: AbortSignal) => {
    return fetch(
        API_ENDPOINTS.admin.members(
          appliedQuery,
          statusFilter || undefined,
          pageIndex,
          20
        ),
        { credentials: "include" }
      )
      .then((res) => {
        if (!res.ok) throw new Error("회원 목록을 불러오지 못했습니다.");
        return res.json();
      })
      .then((data) => {
        if (signal?.aborted) return;
      setMembers(data.content || []);
      setTotalPages(data.totalPages || 1);
      setTotalElements(data.totalElements || 0);
      setPage(data.number || 0);
      })
      .catch((err: unknown) => {
        if (signal?.aborted) return;
      alert((err instanceof Error ? err.message : "오류가 발생했습니다."));
      })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, [appliedQuery, statusFilter]);

  useEffect(() => {
    const controller = new AbortController();
    void fetchMembers(0, controller.signal);
    return () => controller.abort();
  }, [fetchMembers]);

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    if (query === appliedQuery) void fetchMembers(0);
    else setAppliedQuery(query);
  }

  async function handleToggleStatus(member: AdminMemberItem) {
    const nextStatus = member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    const promptMsg =
      nextStatus === "SUSPENDED"
        ? `[${member.nickname}] 회원을 활동 정지(SUSPENDED) 처리하시겠습니까?`
        : `[${member.nickname}] 회원의 활동 정지를 해제(ACTIVE)하시겠습니까?`;

    if (!window.confirm(promptMsg)) return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(
        API_ENDPOINTS.admin.updateMemberStatus(member.publicId),
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ status: nextStatus }),
        }
      );
      if (!res.ok) throw new Error("회원 상태 변경에 실패했습니다.");
      setMembers((prev) =>
        prev.map((m) =>
          m.publicId === member.publicId ? { ...m, status: nextStatus } : m
        )
      );
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "상태 변경 실패"));
    } finally {
      setActionLoading(false);
    }
  }

  async function handleDeleteProfileImage(member: AdminMemberItem) {
    if (
      !window.confirm(
        `[${member.nickname}] 회원의 프로필 사진을 강제 영구 삭제하시겠습니까?\nS3 스토리지에서 즉시 삭제되며 기본 이미지로 초기화됩니다.`
      )
    )
      return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(
        API_ENDPOINTS.admin.deleteMemberProfileImage(member.publicId),
        {
          method: "DELETE",
          credentials: "include",
        }
      );
      if (!res.ok) throw new Error("프로필 사진 삭제에 실패했습니다.");
      setMembers((prev) =>
        prev.map((m) =>
          m.publicId === member.publicId ? { ...m, profileImageUrl: null } : m
        )
      );
      alert("프로필 사진이 영구 삭제 및 기본 프사로 초기화되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "삭제 실패"));
    } finally {
      setActionLoading(false);
    }
  }

  async function handleUpdateNickname(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedMember || !newNickname.trim()) return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(
        API_ENDPOINTS.admin.updateMemberNickname(selectedMember.publicId),
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ nickname: newNickname.trim() }),
        }
      );
      if (!res.ok) {
        const errJson = await res.json().catch(() => null);
        throw new Error(errJson?.message || "닉네임 변경에 실패했습니다.");
      }
      setMembers((prev) =>
        prev.map((m) =>
          m.publicId === selectedMember.publicId
            ? { ...m, nickname: newNickname.trim() }
            : m
        )
      );
      setSelectedMember(null);
      setNewNickname("");
      alert("닉네임이 성공적으로 변경되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "닉네임 변경 실패"));
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Title & Stats */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-slate-900 tracking-tight">
            회원 통합 관리
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            총 {totalElements.toLocaleString()}명의 가입 회원 조회, 상태 제어 및 불량 프로필 관리
          </p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col sm:flex-row gap-3 items-center justify-between">
        <form onSubmit={handleSearchSubmit} className="flex-1 w-full sm:max-w-md flex gap-2">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="이메일 또는 닉네임 검색..."
            className="flex-1 px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-sky-500/20 focus:border-sky-500"
          />
          <button
            type="submit"
            className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-sm font-semibold transition"
          >
            검색
          </button>
        </form>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-xs text-slate-500 font-medium">상태 필터:</span>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 rounded-xl border border-slate-200 text-xs font-semibold focus:outline-none focus:border-sky-500 bg-white"
          >
            <option value="">전체 상태</option>
            <option value="ACTIVE">정상 (ACTIVE)</option>
            <option value="SUSPENDED">활동 정지 (SUSPENDED)</option>
            <option value="WITHDRAWN">탈퇴 (WITHDRAWN)</option>
          </select>
        </div>
      </div>

      {/* Member Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-xs uppercase font-semibold text-slate-500">
              <tr>
                <th className="py-3.5 px-4">회원 정보</th>
                <th className="py-3.5 px-4">권한</th>
                <th className="py-3.5 px-4">계정 상태</th>
                <th className="py-3.5 px-4">가입일</th>
                <th className="py-3.5 px-4 text-right">관리 작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-slate-400">
                    회원 목록을 불러오는 중입니다...
                  </td>
                </tr>
              ) : members.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-12 text-center text-slate-400">
                    검색 조건에 일치하는 회원이 없습니다.
                  </td>
                </tr>
              ) : (
                members.map((m) => (
                  <tr key={m.publicId} className="hover:bg-slate-50/70 transition">
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-slate-200 overflow-hidden relative flex-shrink-0">
                          {m.profileImageUrl ? (
                            <Image
                              src={m.profileImageUrl}
                              alt={m.nickname}
                              fill
                              className="object-cover"
                            />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center font-bold text-slate-500 text-xs">
                              {m.nickname[0]}
                            </div>
                          )}
                        </div>
                        <div>
                          <p className="font-bold text-slate-900">{m.nickname}</p>
                          <p className="text-xs text-slate-400 font-mono">{m.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-[11px] font-bold ${
                          m.role === "ROLE_ADMIN"
                            ? "bg-purple-100 text-purple-700"
                            : "bg-slate-100 text-slate-600"
                        }`}
                      >
                        {m.role === "ROLE_ADMIN" ? "관리자" : "일반 회원"}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${
                          m.status === "ACTIVE"
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : m.status === "SUSPENDED"
                            ? "bg-rose-50 text-rose-700 border border-rose-200"
                            : "bg-slate-100 text-slate-500"
                        }`}
                      >
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            m.status === "ACTIVE"
                              ? "bg-emerald-500"
                              : m.status === "SUSPENDED"
                              ? "bg-rose-500"
                              : "bg-slate-400"
                          }`}
                        />
                        {m.status === "ACTIVE"
                          ? "정상"
                          : m.status === "SUSPENDED"
                          ? "활동 정지"
                          : "탈퇴"}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400 font-mono">
                      {m.createdAt ? m.createdAt.slice(0, 10) : "-"}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => {
                            setSelectedMember(m);
                            setNewNickname(m.nickname);
                          }}
                          className="px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
                        >
                          닉네임 변경
                        </button>
                        {m.profileImageUrl && (
                          <button
                            onClick={() => handleDeleteProfileImage(m)}
                            className="px-2.5 py-1 rounded-lg border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-medium transition"
                          >
                            프사 삭제
                          </button>
                        )}
                        <button
                          onClick={() => handleToggleStatus(m)}
                          className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                            m.status === "ACTIVE"
                              ? "bg-rose-500 hover:bg-rose-600 text-white"
                              : "bg-emerald-600 hover:bg-emerald-700 text-white"
                          }`}
                        >
                          {m.status === "ACTIVE" ? "정지" : "해제"}
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
                onClick={() => { setLoading(true); void fetchMembers(page - 1); }}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50 transition"
              >
                이전
              </button>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => { setLoading(true); void fetchMembers(page + 1); }}
                className="px-3 py-1.5 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50 transition"
              >
                다음
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Nickname Change Modal */}
      {selectedMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white max-w-sm w-full rounded-2xl shadow-2xl border border-slate-200 p-6">
            <h3 className="text-base font-bold text-slate-900 mb-1">
              회원 닉네임 강제 변경
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              불쾌감을 주는 닉네임을 규정에 부합하는 이름으로 강제 수정합니다.
            </p>
            <form onSubmit={handleUpdateNickname} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  새 닉네임 입력
                </label>
                <input
                  type="text"
                  required
                  value={newNickname}
                  onChange={(e) => setNewNickname(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-sky-500"
                />
              </div>
              <div className="flex gap-2 justify-end pt-2">
                <button
                  type="button"
                  onClick={() => setSelectedMember(null)}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-semibold transition"
                >
                  {actionLoading ? "저장 중..." : "변경 적용"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
