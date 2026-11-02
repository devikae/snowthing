"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
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
  const router = useRouter();
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
      } catch (err) {
        setUnauthorized(true);
      } finally {
        setLoading(false);
      }
    }
    checkAdminAuth();
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="text-center">
          <div className="w-10 h-10 border-4 border-sky-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-sm text-slate-500 font-medium">관리자 권한 확인 중...</p>
        </div>
      </div>
    );
  }

  if (unauthorized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-100 p-4">
        <div className="max-w-md w-full bg-white rounded-2xl shadow-xl border border-slate-200 p-8 text-center">
          <div className="w-16 h-16 bg-rose-100 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-4 text-2xl font-bold">
            ⚠️
          </div>
          <h2 className="text-xl font-bold text-slate-900 mb-2">관리자 권한 필요</h2>
          <p className="text-sm text-slate-600 mb-6 leading-relaxed">
            이 페이지는 시스템 관리자 전용 구역입니다.<br />
            관리자 계정으로 로그인한 후 다시 접근해 주시기 바랍니다.
          </p>
          <div className="flex gap-3 justify-center">
            <Link
              href="/"
              className="px-4 py-2.5 rounded-xl border border-slate-300 text-slate-700 hover:bg-slate-50 font-medium text-sm transition"
            >
              메인으로 이동
            </Link>
            <Link
              href="/login"
              className="px-4 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-700 text-white font-medium text-sm transition shadow-sm"
            >
              로그인하기
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const navItems = [
    { label: "대시보드 & 통계", href: "/admin", icon: "📊" },
    { label: "회원 관리", href: "/admin/members", icon: "👥" },
    { label: "게시글 / 블라인드", href: "/admin/posts", icon: "📝" },
    { label: "채팅 / IP 차단", href: "/admin/chat", icon: "💬" },
    { label: "배너 관리", href: "/admin/banners", icon: "🖼️" },
  ];

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col md:flex-row font-sans text-slate-800">
      {/* Sidebar */}
      <aside className="w-full md:w-64 bg-slate-900 text-white flex-shrink-0 flex flex-col">
        {/* Brand */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xl">❄️</span>
              <span className="font-extrabold tracking-tight text-white text-lg">Snowthing</span>
              <span className="text-[10px] bg-sky-500 text-white px-1.5 py-0.5 rounded font-bold uppercase tracking-wider">
                Admin
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1">시스템 통합 관리 콘솔</p>
          </div>
        </div>

        {/* User Card */}
        <div className="p-4 bg-slate-800/60 mx-4 my-4 rounded-xl border border-slate-700/60 flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-sky-600 text-white font-bold flex items-center justify-center text-sm">
            {user?.nickname?.[0] || "A"}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-white truncate">{user?.nickname}</p>
            <p className="text-xs text-sky-400 font-mono truncate">{user?.email}</p>
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
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition ${
                  isActive
                    ? "bg-sky-600 text-white shadow-md shadow-sky-600/20"
                    : "text-slate-400 hover:text-white hover:bg-slate-800/80"
                }`}
              >
                <span className="text-base">{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Footer Navigation */}
        <div className="p-4 border-t border-slate-800">
          <Link
            href="/"
            className="flex items-center gap-2 text-xs text-slate-400 hover:text-white transition py-1"
          >
            <span>←</span>
            <span>스노우띵 서비스 홈으로 나가기</span>
          </Link>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 min-w-0 flex flex-col">
        {/* Top Header */}
        <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between sticky top-0 z-10 shadow-sm">
          <div className="flex items-center gap-2">
            <h1 className="text-base font-bold text-slate-800">
              {navItems.find((n) =>
                n.href === "/admin"
                  ? pathname === "/admin"
                  : pathname.startsWith(n.href)
              )?.label || "관리 콘솔"}
            </h1>
          </div>
          <div className="flex items-center gap-4">
            <span className="inline-flex items-center gap-1.5 text-xs text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full font-medium border border-emerald-200">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              서버 정상 가동 중
            </span>
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
