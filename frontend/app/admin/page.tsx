"use client";

import React, { useCallback, useEffect, useState } from "react";
import { csrfFetch } from "../lib/csrfFetch";
import {
  API_ENDPOINTS,
  AdminDashboardStats,
} from "../lib/api";

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatingChat, setUpdatingChat] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = useCallback((signal?: AbortSignal) => {
    return csrfFetch(API_ENDPOINTS.admin.stats(), {
        credentials: "include",
        signal,
      })
      .then((res) => {
        if (!res.ok) throw new Error("통계 데이터를 불러오지 못했습니다.");
        return res.json();
      })
      .then((data) => {
        if (signal?.aborted) return;
      setError(null);
      setStats(data);
      })
      .catch((err: unknown) => {
        if (signal?.aborted) return;
      setError((err instanceof Error ? err.message : "오류가 발생했습니다."));
      })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void fetchStats(controller.signal);
    return () => controller.abort();
  }, [fetchStats]);

  async function handleToggleAnonymousChat() {
    if (!stats || updatingChat) return;
    const nextVal = !stats.anonymousChatEnabled;
    const confirmMsg = nextVal
      ? "비로그인 유저도 라이브톡에 메시지를 전송할 수 있도록 허용하시겠습니까?"
      : "비로그인 유저의 라이브톡 전송을 차단하고 기존대로 로그인 회원 전용으로 변경하시겠습니까?";

    if (!window.confirm(confirmMsg)) return;

    try {
      setUpdatingChat(true);
      const res = await csrfFetch(
        API_ENDPOINTS.admin.updateConfig("CHAT_ANONYMOUS_ENABLED"),
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ value: String(nextVal) }),
        }
      );
      if (!res.ok) throw new Error("설정 변경에 실패했습니다.");
      setStats({ ...stats, anonymousChatEnabled: nextVal });
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "설정 변경 중 오류가 발생했습니다."));
    } finally {
      setUpdatingChat(false);
    }
  }

  if (loading && !stats) {
    return (
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="w-8 h-8 border-4 border-sky-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error || !stats) {
    return (
      <div className="p-6 bg-rose-50 border border-rose-200 rounded-2xl text-rose-700">
        <p className="font-semibold mb-2">데이터 로드 실패</p>
        <p className="text-sm">{error}</p>
        <button
          onClick={() => { setLoading(true); void fetchStats(); }}
          className="mt-4 px-4 py-2 bg-rose-600 text-white rounded-xl text-sm font-medium hover:bg-rose-700 transition"
        >
          다시 시도
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Page Title */}
      <div>
        <h2 className="text-2xl font-black text-slate-900 tracking-tight">
          시스템 현황
        </h2>
        <p className="text-sm text-slate-500 mt-1">
          회원 현황과 실시간 핵심 시스템 제어
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        {/* Members */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-xs font-medium">
            <span>전체 회원수</span>
            <span className="text-base">🏂</span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">
              {stats.totalMembers.toLocaleString()}
            </span>
            <span className="text-xs text-slate-400 font-normal">명</span>
          </div>
          <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <span>오늘 신규 가입</span>
            <span className="font-semibold text-sky-600">
              +{stats.todayNewMembers}명
            </span>
          </div>
        </div>

        {/* Anonymous Chat Switch */}
        <div
          className={`p-5 rounded-2xl border shadow-sm transition ${
            stats.anonymousChatEnabled
              ? "bg-sky-50/60 border-sky-200"
              : "bg-white border-slate-200/80"
          }`}
        >
          <div className="flex items-center justify-between text-slate-500 text-xs font-medium">
            <span className="font-semibold text-slate-700">비로그인 라이브톡</span>
            <span className="text-base">💬</span>
          </div>
          <div className="mt-3 flex items-center justify-between">
            <div>
              <span
                className={`text-xl font-black tracking-tight ${
                  stats.anonymousChatEnabled ? "text-sky-600" : "text-slate-500"
                }`}
              >
                {stats.anonymousChatEnabled ? "허용 (ON)" : "차단 (OFF)"}
              </span>
              <p className="text-[11px] text-slate-400 mt-0.5">
                {stats.anonymousChatEnabled
                  ? "비회원 랜덤 닉네임 발신 가능"
                  : "로그인 회원만 채팅 가능"}
              </p>
            </div>
            {/* Toggle Switch */}
            <button
              onClick={handleToggleAnonymousChat}
              disabled={updatingChat}
              className={`relative inline-flex h-7 w-13 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                stats.anonymousChatEnabled ? "bg-sky-600" : "bg-slate-300"
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-6 w-6 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
                  stats.anonymousChatEnabled ? "translate-x-6" : "translate-x-0"
                }`}
              />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
