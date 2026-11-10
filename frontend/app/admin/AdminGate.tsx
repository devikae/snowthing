"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

type GateState = "checking" | "admin" | "forbidden" | "anonymous";

const ADMIN_TABS: { href: string; label: string }[] = [
  { href: "/admin", label: "대시보드" },
  { href: "/admin/reports", label: "신고 처리" },
  { href: "/admin/inquiries", label: "1:1 문의" },
  { href: "/admin/members", label: "회원" },
  { href: "/admin/posts", label: "게시글" },
  { href: "/admin/comments", label: "댓글" },
  { href: "/admin/market", label: "중고장터" },
  { href: "/admin/carpool", label: "카풀" },
  { href: "/admin/resort-reports", label: "설질 제보" },
  { href: "/admin/answer-drafts", label: "답변 초안" },
  { href: "/admin/api-tokens", label: "API 토큰" },
  { href: "/admin/search", label: "검색 인덱스" },
  { href: "/admin/chat", label: "라이브톡 차단" },
  { href: "/admin/banners", label: "배너" },
  { href: "/admin/action-logs", label: "조치 기록" },
];

/**
 * 관리자 화면 진입 확인. 화면을 숨기는 것은 편의일 뿐이고 실제 권한은 서버가 `/api/v1/admin/**`에서 ROLE_ADMIN으로 막는다.
 */
export function AdminGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<GateState>("checking");
  const pathname = usePathname();

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.members.me, { credentials: "include" })
      .then(async (response) => {
        if (cancelled) return;
        if (response.status === 401) {
          setState("anonymous");
          return;
        }
        if (!response.ok) {
          setState("forbidden");
          return;
        }
        const member = (await response.json()) as { role?: string };
        if (!cancelled) setState(member.role === "ROLE_ADMIN" ? "admin" : "forbidden");
      })
      .catch(() => {
        if (!cancelled) setState("forbidden");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="community-page">
      <TopNav active="home" />
      <main className="community-container admin-page">
        {state === "admin" ? (
          <>
            <nav className="admin-tabs" aria-label="관리자 메뉴">
              {ADMIN_TABS.map((tab) => {
                const current = tab.href === "/admin" ? pathname === "/admin" : pathname?.startsWith(tab.href);
                return (
                  <Link key={tab.href} href={tab.href} aria-current={current ? "page" : undefined}>
                    {tab.label}
                  </Link>
                );
              })}
            </nav>
            {children}
          </>
        ) : (
          <section className="panel admin-gate" aria-live="polite">
            {state === "checking" && <p>권한을 확인하는 중입니다...</p>}
            {state === "anonymous" && (
              <p>
                관리자 로그인이 필요합니다. <Link href={`/login?returnUrl=${encodeURIComponent(pathname ?? "/admin/answer-drafts")}`}>로그인</Link>
              </p>
            )}
            {state === "forbidden" && <p>관리자만 볼 수 있는 화면입니다.</p>}
          </section>
        )}
      </main>
      <Footer />
    </div>
  );
}
