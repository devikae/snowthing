"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

type GateState = "checking" | "admin" | "forbidden" | "anonymous";

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
              <Link href="/admin/reports" aria-current={pathname?.startsWith("/admin/reports") ? "page" : undefined}>
                신고 처리
              </Link>
              <Link href="/admin/answer-drafts" aria-current={pathname?.startsWith("/admin/answer-drafts") ? "page" : undefined}>
                답변 초안
              </Link>
              <Link href="/admin/api-tokens" aria-current={pathname?.startsWith("/admin/api-tokens") ? "page" : undefined}>
                API 토큰
              </Link>
              <Link href="/admin/search" aria-current={pathname?.startsWith("/admin/search") ? "page" : undefined}>
                검색 인덱스
              </Link>
              <Link href="/admin/members" aria-current={pathname?.startsWith("/admin/members") ? "page" : undefined}>
                회원
              </Link>
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
