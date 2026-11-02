"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { API_ENDPOINTS } from "../lib/api";

interface AdminUser {
  email: string;
  nickname: string;
  role: string;
}

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [unauthorized, setUnauthorized] = useState(false);

  useEffect(() => {
    async function checkAdminAuth() {
      try {
        const res = await fetch(API_ENDPOINTS.members.me, {
          credentials: "include",
        });
        if (!res.ok) {
          setUnauthorized(true);
          return;
        }
        const data = await res.json();
        if (data.role !== "ROLE_ADMIN") {
          setUnauthorized(true);
          return;
        }
        setUser(data);
      } catch {
        setUnauthorized(true);
      } finally {
        setLoading(false);
      }
    }
    checkAdminAuth();
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#f4f6f9]">
        <div className="text-center">
          <div className="w-10 h-10 border-4 border-[#0284c7] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-sm text-slate-500 font-medium">관리자 권한 확인 중...</p>
        </div>
      </div>
    );
  }

  if (unauthorized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#f4f6f9] p-4 font-sans">
        <div className="max-w-md w-full bg-white rounded-2xl shadow-sm border border-slate-200 p-8 text-center">
          <div className="w-14 h-14 bg-rose-50 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-4 text-2xl font-bold border border-rose-100">
            ⚠️
          </div>
          <h2 className="text-lg font-bold text-[#0f2942] mb-2">관리자 권한 필요</h2>
          <p className="text-sm text-slate-600 mb-6 leading-relaxed">
            이 페이지는 시스템 관리자 전용 구역입니다.<br />
            관리자 계정으로 로그인한 후 다시 접근해 주시기 바랍니다.
          </p>
          <div className="flex gap-3 justify-center">
            <Link
              href="/"
              className="px-4 py-2.5 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-50 font-medium text-sm transition"
            >
              메인으로 이동
            </Link>
            <Link
              href="/login"
              className="px-4 py-2.5 rounded-lg bg-[#0284c7] hover:bg-[#0369a1] text-white font-medium text-sm transition shadow-sm"
            >
              로그인하기
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const navItems = [
    { label: "대시보드 & 지표", href: "/admin", icon: "📊" },
    { label: "회원 관리", href: "/admin/members", icon: "👥" },
    { label: "삭제글 / 블라인드", href: "/admin/posts", icon: "🗑️" },
    { label: "채팅 / IP 차단", href: "/admin/chat", icon: "💬" },
    { label: "배너 관리", href: "/admin/banners", icon: "🖼️" },
  ];

  return (
    <div className="min-h-screen bg-[#f4f6f9] flex flex-col md:flex-row font-sans text-slate-800">
      {/* Sidebar - Snowthing Style Clean White Panel */}
      <aside className="w-full md:w-64 bg-white border-r border-slate-200 flex-shrink-0 flex flex-col">
        {/* Brand */}
        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-2xl">❄️</span>
              <span className="font-black tracking-tight text-[#0f2942] text-lg">Snowthing</span>
              <span className="text-[10px] bg-[#0284c7] text-white px-1.5 py-0.5 rounded font-bold uppercase tracking-wider">
                Admin
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1 font-medium">통합 운영 관리 콘솔</p>
          </div>
        </div>

        {/* User Card */}
        <div className="p-3.5 bg-[#f8fafc] mx-4 my-4 rounded-xl border border-slate-200/80 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-[#0f2942] text-white font-bold flex items-center justify-center text-sm shadow-sm">
            {user?.nickname?.[0] || "A"}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-[#0f2942] truncate">{user?.nickname}</p>
            <p className="text-[11px] text-slate-500 font-mono truncate">{user?.email}</p>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 space-y-1">
          {navItems.map((item) => {
            const isActive =
              item.href === "/admin"
                ? pathname === "/admin"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-xs font-bold transition ${
                  isActive
                    ? "bg-[#e0f2fe] text-[#0284c7] shadow-xs"
                    : "text-slate-600 hover:text-[#0f2942] hover:bg-slate-50"
                }`}
              >
                <span className="text-base">{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Footer Navigation */}
        <div className="p-4 border-t border-slate-100">
          <Link
            href="/"
            className="flex items-center gap-2 text-xs font-semibold text-slate-500 hover:text-[#0284c7] transition py-1"
          >
            <span>←</span>
            <span>스노우띵 서비스 홈으로</span>
          </Link>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 min-w-0 flex flex-col">
        {/* Top Header */}
        <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between sticky top-0 z-10">
          <div className="flex items-center gap-2">
            <h1 className="text-base font-extrabold text-[#0f2942]">
              {navItems.find((n) =>
                n.href === "/admin"
                  ? pathname === "/admin"
                  : pathname.startsWith(n.href)
              )?.label || "관리 콘솔"}
            </h1>
          </div>
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1.5 text-xs text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-full font-semibold border border-emerald-200">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              서버 정상 가동
            </span>
            <Link
              href="/"
              className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-50 transition"
            >
              사용자 화면
            </Link>
          </div>
        </header>

        {/* Body Content */}
        <div className="flex-1 p-6 md:p-8 overflow-y-auto">
          {children}
        </div>
      </main>
    </div>
  );
}
