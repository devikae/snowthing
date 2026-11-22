"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

type GateState = "checking" | "admin" | "forbidden" | "anonymous";

const ADMIN_MENU: { title: string; items: { href: string; label: string }[] }[] = [
  {
    title: "운영",
    items: [
      { href: "/admin", label: "대시보드" },
      { href: "/admin/reports", label: "신고 처리" },
      { href: "/admin/inquiries", label: "1:1 문의" },
      { href: "/admin/action-logs", label: "조치 기록" },
    ],
  },
  {
    title: "콘텐츠",
    items: [
      { href: "/admin/posts", label: "게시글" },
      { href: "/admin/comments", label: "댓글" },
      { href: "/admin/market", label: "중고장터" },
      { href: "/admin/carpool", label: "카풀" },
      { href: "/admin/resort-reports", label: "설질 제보" },
      { href: "/admin/chat", label: "라이브톡 차단" },
    ],
  },
  { title: "회원", items: [{ href: "/admin/members", label: "회원" }] },
  {
    title: "사이트",
    items: [
      { href: "/admin/banners", label: "배너" },
      { href: "/admin/season-openings", label: "개장일" },
    ],
  },
  {
    title: "AI, 시스템",
    items: [
      { href: "/admin/ai-members", label: "AI 회원" },
      { href: "/admin/answer-drafts", label: "답변 초안" },
      { href: "/admin/api-tokens", label: "API 토큰" },
      { href: "/admin/search", label: "검색 인덱스" },
    ],
  },
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
          <div className="admin-shell">
            <nav className="admin-nav" aria-label="관리자 메뉴">
              {ADMIN_MENU.map((group) => (
                <div key={group.title} className="admin-nav-group">
                  <p className="admin-nav-title">{group.title}</p>
                  {group.items.map((item) => {
                    const current = item.href === "/admin" ? pathname === "/admin" : pathname?.startsWith(item.href);
                    return (
                      <Link key={item.href} href={item.href} aria-current={current ? "page" : undefined}>
                        {item.label}
                      </Link>
                    );
                  })}
                </div>
              ))}
            </nav>
            <div className="admin-content">{children}</div>
          </div>
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
