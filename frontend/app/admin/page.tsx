"use client";

import React, { useEffect, useState } from "react";
import {
  API_ENDPOINTS,
  AdminDashboardStats,
  DailyVisitorStat,
} from "../lib/api";

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [days, setDays] = useState<7 | 30>(7);
  const [updatingChat, setUpdatingChat] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function fetchStats(selectedDays: number) {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch(API_ENDPOINTS.admin.stats(selectedDays), {
        credentials: "include",
      });
      if (!res.ok) throw new Error("통계 데이터를 불러오지 못했습니다.");
      const data = await res.json();
      setStats(data);
    } catch (err: any) {
      setError(err.message || "오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchStats(days);
  }, [days]);

  async function handleToggleAnonymousChat() {
    if (!stats || updatingChat) return;
    const nextVal = !stats.anonymousChatEnabled;
    const confirmMsg = nextVal
      ? "비로그인 유저도 라이브톡에 메시지를 전송할 수 있도록 허용하시겠습니까?"
      : "비로그인 유저의 라이브톡 전송을 차단하고 기존대로 로그인 회원 전용으로 변경하시겠습니까?";

    if (!window.confirm(confirmMsg)) return;

    try {
      setUpdatingChat(true);
      const res = await fetch(
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
    } catch (err: any) {
      alert(err.message || "설정 변경 중 오류가 발생했습니다.");
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
          onClick={() => fetchStats(days)}
          className="mt-4 px-4 py-2 bg-rose-600 text-white rounded-xl text-sm font-medium hover:bg-rose-700 transition"
        >
          다시 시도
        </button>
      </div>
    );
  }

  const trends = stats.trends.slice(-days);
  const maxVisitors = Math.max(...trends.map((t) => t.totalVisitors), 10);

  return (
    <div className="space-y-8 max-w-7xl mx-auto">
      {/* Page Title & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-slate-900 tracking-tight">
            방문자 통계 및 시스템 현황
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            DAU(일일 순수 방문자) 추이와 실시간 핵심 시스템 제어
          </p>
        </div>

        {/* Global Controls */}
        <div className="flex items-center gap-3">
          <div className="inline-flex bg-slate-200/80 p-1 rounded-xl">
            <button
              onClick={() => setDays(7)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                days === 7 ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              최근 7일
            </button>
            <button
              onClick={() => setDays(30)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                days === 30 ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              최근 30일
            </button>
          </div>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Card 1: Today DAU */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-500 text-xs font-medium">
            <span>오늘 순수 방문자 (DAU)</span>
            <span className="text-base">👤</span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">
              {stats.todayTotalVisitors.toLocaleString()}
            </span>
            <span className="text-xs text-slate-400 font-normal">명</span>
          </div>
          <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <span>회원 {stats.todayMemberVisitors}명</span>
            <span className="text-slate-300">•</span>
            <span>비회원 {stats.todayGuestVisitors}명</span>
          </div>
        </div>

        {/* Card 2: Today PV */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-xs font-medium">
            <span>오늘 페이지뷰 (PV)</span>
            <span className="text-base">📄</span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">
              {stats.todayPvCount.toLocaleString()}
            </span>
            <span className="text-xs text-slate-400 font-normal">회</span>
          </div>
          <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <span>방문자당 평균 조회</span>
            <span className="font-semibold text-slate-700">
              {stats.todayTotalVisitors > 0
                ? (stats.todayPvCount / stats.todayTotalVisitors).toFixed(1)
                : 0}
              회
            </span>
          </div>
        </div>

        {/* Card 3: Members */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 text-xs font-medium">
            <span>전체 회원수</span>
            <span className="text-base">🏂</span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">
              {stats.totalMemberCount.toLocaleString()}
            </span>
            <span className="text-xs text-slate-400 font-normal">명</span>
          </div>
          <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <span>오늘 신규 가입</span>
            <span className="font-semibold text-sky-600">
              +{stats.todayNewMemberCount}명
            </span>
          </div>
        </div>

        {/* Card 4: Anonymous Chat Switch */}
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

      {/* Visitors Trend Chart Card */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6">
          <div>
            <h3 className="text-base font-bold text-slate-900">
              일일 순수 방문자(DAU) 추이 ({days}일)
            </h3>
            <p className="text-xs text-slate-400 mt-0.5">
              회원/게스트 세부 구성 및 일자별 순방문 규모
            </p>
          </div>
          <div className="flex items-center gap-4 text-xs">
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-full bg-sky-500" />
              <span className="text-slate-600">순수 방문자 (DAU)</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-full bg-slate-300" />
              <span className="text-slate-600">페이지뷰 (PV)</span>
            </div>
          </div>
        </div>

        {/* Bar/Trend Visualization */}
        <div className="h-64 flex items-end gap-2 pt-6 pb-2 border-b border-slate-100">
          {trends.map((item, idx) => {
            const heightPercent = Math.min(
              100,
              Math.max(6, (item.totalVisitors / maxVisitors) * 100)
            );
            const dateLabel = item.statDate.slice(5); // MM-DD
            return (
              <div
                key={item.statDate}
                className="flex-1 flex flex-col items-center h-full justify-end group relative"
              >
                {/* Tooltip */}
                <div className="absolute -top-14 opacity-0 group-hover:opacity-100 pointer-events-none transition bg-slate-900 text-white text-[11px] py-1.5 px-2.5 rounded-lg whitespace-nowrap shadow-xl z-20">
                  <p className="font-bold">{item.statDate}</p>
                  <p className="text-slate-300">
                    DAU: {item.totalVisitors}명 (회원 {item.memberVisitors} / 비회원 {item.guestVisitors})
                  </p>
                  <p className="text-slate-400">PV: {item.pvCount}회</p>
                </div>

                {/* Number Above Bar */}
                <span className="text-[10px] text-slate-400 mb-1 group-hover:text-sky-600 font-semibold">
                  {item.totalVisitors > 0 ? item.totalVisitors : ""}
                </span>

                {/* Bar */}
                <div
                  style={{ height: `${heightPercent}%` }}
                  className="w-full max-w-[28px] bg-gradient-to-t from-sky-600 to-sky-400 rounded-t-md transition-all duration-300 group-hover:from-sky-700 group-hover:to-sky-500 shadow-sm"
                />
              </div>
            );
          })}
        </div>

        {/* Date Labels */}
        <div className="flex items-center gap-2 mt-2">
          {trends.map((item) => (
            <div
              key={item.statDate}
              className="flex-1 text-center text-[10px] text-slate-400 font-medium"
            >
              {item.statDate.slice(5)}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
