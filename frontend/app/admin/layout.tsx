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

function AdminThemeToggle() {
  const [theme, setTheme] = useState<"light" | "dark">("light");

  useEffect(() => {
    const current = (document.documentElement.dataset.theme as "light" | "dark") || "light";
    setTheme(current);
  }, []);

  const toggleTheme = () => {
    const next = theme === "light" ? "dark" : "light";
    setTheme(next);
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem("snowthing-theme", next);
    } catch {
      // ignore
    }
  };

  return (
    <button
      type="button"
      onClick={toggleTheme}
      title={`테마 전환 (현재: ${theme === "light" ? "라이트" : "다크"} 모드)`}
      aria-label="테마 전환"
      className="w-8 h-8 rounded-lg border border-[var(--snow-border)] bg-[var(--snow-canvas)] text-[var(--snow-ink)] flex items-center justify-center hover:bg-[var(--snow-surface-low)] transition shadow-2xs cursor-pointer"
    >
      <span className="material-symbols-outlined text-base">
        {theme === "light" ? "dark_mode" : "light_mode"}
      </span>
    </button>
  );
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
      <div className="min-h-screen flex items-center justify-center bg-[var(--snow-background)] text-[var(--snow-ink)] font-sans">
        <div className="text-center">
          <div className="w-10 h-10 border-4 border-[#0284c7] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-xs font-semibold text-[var(--snow-ink-soft)]">관리자 권한 확인 중...</p>
        </div>
      </div>
    );
  }

  if (unauthorized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--snow-background)] p-4 font-sans">
        <div className="max-w-md w-full bg-[var(--snow-canvas)] rounded-2xl shadow-xl border border-[var(--snow-border)] p-8 text-center">
          <div className="w-14 h-14 bg-rose-50 dark:bg-rose-950/40 text-rose-600 rounded-full flex items-center justify-center mx-auto mb-4 text-2xl font-bold border border-rose-200 dark:border-rose-900/50">
            ⚠️
          </div>
          <h2 className="text-lg font-bold text-[var(--snow-ink)] mb-2">관리자 권한 필요</h2>
          <p className="text-xs text-[var(--snow-ink-soft)] mb-6 leading-relaxed">
            이 페이지는 시스템 관리자 전용 구역입니다.<br />
            관리자 계정으로 로그인한 후 다시 접근해 주시기 바랍니다.
          </p>
          <div className="flex gap-3 justify-center">
            <Link
              href="/"
              className="px-4 py-2 rounded-xl border border-[var(--snow-border)] text-[var(--snow-ink-soft)] hover:bg-[var(--snow-surface-low)] hover:text-[var(--snow-ink)] font-semibold text-xs transition"
            >
              메인으로 이동
            </Link>
            <Link
              href="/login"
              className="px-4 py-2 rounded-xl bg-[#0284c7] hover:bg-[#0369a1] text-white font-bold text-xs transition shadow-xs"
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
    <div className="min-h-screen bg-[var(--snow-background)] flex flex-col md:flex-row font-sans text-[var(--snow-ink)] transition-colors duration-150">
      {/* Sidebar - Theme Tokenized Panel */}
      <aside className="w-full md:w-64 bg-[var(--snow-canvas)] border-r border-[var(--snow-border)] flex-shrink-0 flex flex-col">
        {/* Brand */}
        <div className="p-5 border-b border-[var(--snow-line-soft)] flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-2xl">❄️</span>
              <span className="font-black tracking-tight text-[var(--snow-ink)] text-lg">Snowthing</span>
              <span className="text-[10px] bg-[#0284c7] text-white px-1.5 py-0.5 rounded font-bold uppercase tracking-wider">
                Admin
              </span>
            </div>
            <p className="text-[11px] text-[var(--snow-muted)] mt-1 font-medium">통합 운영 관리 콘솔</p>
          </div>
        </div>

        {/* User Card */}
        <div className="p-3.5 bg-[var(--snow-surface-low)] mx-4 my-4 rounded-xl border border-[var(--snow-border)] flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-[#0284c7] text-white font-bold flex items-center justify-center text-sm shadow-xs">
            {user?.nickname?.[0] || "A"}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-[var(--snow-ink)] truncate">{user?.nickname}</p>
            <p className="text-[11px] text-[var(--snow-ink-soft)] font-mono truncate">{user?.email}</p>
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
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-bold transition ${
                  isActive
                    ? "bg-[#0284c7]/15 text-[#0284c7] dark:text-[#38bdf8] dark:bg-[#0284c7]/25 shadow-2xs"
                    : "text-[var(--snow-ink-soft)] hover:text-[var(--snow-ink)] hover:bg-[var(--snow-surface-low)]"
                }`}
              >
                <span className="text-base">{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Footer Navigation */}
        <div className="p-4 border-t border-[var(--snow-line-soft)]">
          <Link
            href="/"
            className="flex items-center gap-2 text-xs font-semibold text-[var(--snow-ink-soft)] hover:text-[#0284c7] transition py-1"
          >
            <span>←</span>
            <span>스노우띵 서비스 홈으로</span>
          </Link>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 min-w-0 flex flex-col">
        {/* Top Header */}
        <header className="h-16 bg-[var(--snow-canvas)] border-b border-[var(--snow-border)] px-6 flex items-center justify-between sticky top-0 z-10 transition-colors duration-150">
          <div className="flex items-center gap-2">
            <h1 className="text-base font-extrabold text-[var(--snow-ink)]">
              {navItems.find((n) =>
                n.href === "/admin"
                  ? pathname === "/admin"
                  : pathname.startsWith(n.href)
              )?.label || "관리 콘솔"}
            </h1>
          </div>
          <div className="flex items-center gap-2.5">
            {/* Live Server Indicator */}
            <span className="hidden sm:inline-flex items-center gap-1.5 text-xs text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 px-2.5 py-1 rounded-full font-semibold border border-emerald-200 dark:border-emerald-800/50">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              서버 정상 가동
            </span>

            {/* Dark/Light Mode Theme Toggle Button */}
            <AdminThemeToggle />

            {/* Main Service Link */}
            <Link
              href="/"
              className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-[var(--snow-border)] bg-[var(--snow-canvas)] text-[var(--snow-ink)] hover:bg-[var(--snow-surface-low)] transition shadow-2xs"
            >
              사용자 화면
            </Link>
          </div>
        </header>

        {/* Body Content */}
        <div className="flex-1 p-6 md:p-8 overflow-y-auto bg-[var(--snow-background)] text-[var(--snow-ink)]">
          {children}
        </div>
      </main>
    </div>
  );
}
