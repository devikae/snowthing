"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS } from "../lib/api";

export type ActiveNav = "home" | "posts" | "free" | "anonymous" | "gear" | "resort" | "profile" | "login" | "signup";

interface MemberUser {
  publicId: string;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
}

interface NavItem {
  href: string;
  label: string;
  key: string;
}

const navItems: NavItem[] = [
  { href: "/posts", label: "전체글", key: "posts" },
  { href: "/posts?sort=popular", label: "실시간 베스트", key: "best" },
  { href: "/posts?category=FREE", label: "자유게시판", key: "free" },
  { href: "/posts?category=ANONYMOUS", label: "익명게시판", key: "anonymous" },
  { href: "/resort", label: "실시간 설질/웹캠", key: "resort" },
  { href: "/posts?category=QNA", label: "장비·테크닉", key: "gear" },
  { href: "/#carpool", label: "카풀/동행", key: "carpool" },
  { href: "/#market", label: "중고장터", key: "market" },
];

const mobileItems = [
  { href: "/", label: "홈", icon: "home", key: "home" },
  { href: "/posts?sort=popular", label: "베스트", icon: "local_fire_department", key: "best" },
  { href: "/resort", label: "실시간 설질", icon: "ac_unit", key: "resort" },
  { href: "/#carpool", label: "카풀", icon: "directions_car", key: "carpool" },
  { href: "/posts?category=ANONYMOUS", label: "익명", icon: "forum", key: "anonymous" },
] as const;

export function TopNav({ active = "home" }: { active?: ActiveNav }) {
  const router = useRouter();
  const [user, setUser] = useState<MemberUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");

  useEffect(() => {
    void (async () => {
      try {
        const response = await fetch(API_ENDPOINTS.members.me, { credentials: "include" });
        setUser(response.ok ? await response.json() : null);
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const keyword = search.trim();
    if (keyword) router.push(`/posts?keyword=${encodeURIComponent(keyword)}`);
  };

  const handleLogout = async () => {
    try {
      await csrfFetch(API_ENDPOINTS.auth.logout, { method: "POST" });
    } finally {
      setUser(null);
      window.location.href = "/";
    }
  };

  return (
    <>
      <div className="community-notice">
        <div className="community-container community-notice-inner">
          <div className="community-notice-message">
            <span className="notice-badge">공지</span>
            <span className="truncate">24/25 시즌방 인원 구인 및 리조트 실시간 슬로프 제보 게시판 이용 수칙 안내</span>
          </div>
          <div className="community-notice-links">
            <span>강원권 야간 정설 완료</span><i />
            <Link href="/resort">실시간 웹캠 센터</Link><i />
            <Link href="/login">출석체크</Link>
          </div>
        </div>
      </div>

      <header className="community-header">
        <div className="community-container community-header-inner">
          <div className="community-header-left">
            <Link href="/" aria-label="Snowthing 홈" className="alpine-logo">
              <span className="alpine-logo-mark"><i /><i /><i /></span>
              <span>SnowThing</span>
            </Link>
            <nav className="community-main-nav" aria-label="메인 메뉴">
              {navItems.map((item) => (
                <Link
                  key={item.key}
                  href={item.href}
                  className={active === item.key ? "nav-active" : ""}
                  aria-current={active === item.key ? "page" : undefined}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          </div>

          <div className="community-header-actions">
            <form className="header-search" onSubmit={handleSearch}>
              <span className="material-symbols-outlined">search</span>
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="슬로프, 장비, 카풀 검색" aria-label="통합 검색" />
            </form>
            <Link href="/posts/create" className="header-write-button">
              <span className="material-symbols-outlined">edit</span><span>글쓰기</span>
            </Link>
            <button type="button" className="header-icon-button" aria-label="알림">
              <span className="material-symbols-outlined">notifications</span><i />
            </button>
            {!loading && (user ? (
              <div className="header-profile-wrap">
                <Link href="/profile" className="header-profile" title={`${user.nickname} 프로필`}>
                  {user.profileImageUrl ? <img src={user.profileImageUrl} alt="" /> : <span>{user.nickname.slice(0, 1)}</span>}
                </Link>
                <button type="button" onClick={handleLogout} className="header-logout">로그아웃</button>
              </div>
            ) : (
              <Link href="/login" className="header-login">로그인</Link>
            ))}
          </div>
        </div>
      </header>

      <nav className="community-mobile-nav" aria-label="모바일 메뉴">
        {mobileItems.map((item) => (
          <Link
            key={item.key}
            href={item.href}
            className={active === item.key ? "active" : ""}
            aria-current={active === item.key ? "page" : undefined}
          >
            <span className="material-symbols-outlined">{item.icon}</span><span>{item.label}</span>
          </Link>
        ))}
      </nav>
    </>
  );
}

export function SideCategories({ active = "all" }: { active?: string }) {
  return <aside hidden data-active-category={active} aria-hidden="true" />;
}

export function Footer() {
  return (
    <footer className="community-footer">
      <div className="community-container community-footer-inner">
        <div className="community-footer-brand"><span className="alpine-logo-mark small"><i /><i /><i /></span><strong>SNOWTHING</strong><span>대한민국 스노보드 &amp; 스키 커뮤니티</span></div>
        <nav><Link href="/">이용약관</Link><Link href="/">개인정보처리방침</Link><Link href="/">게시판 운영원칙</Link><Link href="/">고객센터</Link></nav>
      </div>
      <div className="community-container community-copyright">Copyright © SNOWTHING Alpine Community. All rights reserved.</div>
    </footer>
  );
}
