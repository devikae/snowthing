"use client";

import Link from "next/link";
import Image from "next/image";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS } from "../lib/api";

type ThemeSetting = "light" | "dark";

const THEME_STORAGE_KEY = "snowthing-theme";
const themeMeta: Record<ThemeSetting, { icon: string; label: string }> = {
  light: { icon: "light_mode", label: "라이트 모드" },
  dark: { icon: "dark_mode", label: "다크 모드" },
};

function applyTheme(setting: ThemeSetting) {
  document.documentElement.dataset.theme = setting;
}

function ThemeToggle() {
  const [setting, setSetting] = useState<ThemeSetting>("light");

  useEffect(() => {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    const initialSetting: ThemeSetting = saved === "light" || saved === "dark"
      ? saved
      : (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    applyTheme(initialSetting);
    const syncButton = window.setTimeout(() => setSetting(initialSetting), 0);
    return () => window.clearTimeout(syncButton);
  }, []);

  const handleToggle = () => {
    const nextSetting: ThemeSetting = setting === "light" ? "dark" : "light";
    setSetting(nextSetting);
    applyTheme(nextSetting);
    localStorage.setItem(THEME_STORAGE_KEY, nextSetting);
  };

  const nextSetting: ThemeSetting = setting === "light" ? "dark" : "light";

  return (
    <button
      type="button"
      className="header-theme-toggle"
      onClick={handleToggle}
      aria-label={`현재 테마: ${themeMeta[setting].label}. ${themeMeta[nextSetting].label}로 변경`}
      title={`테마: ${themeMeta[setting].label}`}
    >
      <span className="material-symbols-outlined" aria-hidden="true">{themeMeta[setting].icon}</span>
    </button>
  );
}

export type ActiveNav = "home" | "posts" | "best" | "free" | "anonymous" | "gear" | "food" | "crew" | "seasonRoom" | "carpool" | "market" | "resort" | "profile" | "login" | "signup";

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

const boardItems: NavItem[] = [
  { href: "/posts", label: "전체글", key: "posts" },
  { href: "/posts?view=best", label: "실시간 베스트", key: "best" },
  { href: "/posts?category=FREE", label: "자유게시판", key: "free" },
  { href: "/posts?category=ANONYMOUS", label: "익명게시판", key: "anonymous" },
  { href: "/posts?category=QNA", label: "장비 후기", key: "gear" },
  { href: "/posts?category=FOOD", label: "리조트 맛집", key: "food" },
  { href: "/posts?category=CREW", label: "동호회 모집", key: "crew" },
  { href: "/posts?category=SEASON_ROOM", label: "시즌방 모집", key: "seasonRoom" },
];

export function TopNav({ active = "home" }: { active?: ActiveNav }) {
  const router = useRouter();
  const [user, setUser] = useState<MemberUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const boardActive = ["posts", "best", "free", "anonymous", "gear", "food", "crew", "seasonRoom"].includes(active);
  const isBoardItemActive = (key: string) => active === key;

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
      <header className="community-header">
        <div className="community-container community-header-inner">
          <div className="community-header-left">
            <Link href="/" aria-label="Snowthing 홈" className="alpine-logo">
              <span>SnowThing</span>
            </Link>
            <nav className="community-main-nav" aria-label="메인 메뉴">
              <details className="header-board-menu">
                <summary className={boardActive ? "nav-active" : ""}>
                  게시판
                  <span className="material-symbols-outlined">keyboard_arrow_down</span>
                </summary>
                <div className="header-board-dropdown">
                  {boardItems.map((item) => (
                    <Link
                      key={item.key}
                      href={item.href}
                      className={isBoardItemActive(item.key) ? "nav-active" : ""}
                      aria-current={isBoardItemActive(item.key) ? "page" : undefined}
                    >
                      {item.label}
                    </Link>
                  ))}
                </div>
              </details>
              <Link href="/resort-cam" className={active === "resort" ? "nav-active" : ""} aria-current={active === "resort" ? "page" : undefined}>
                슬로프캠
              </Link>
              <Link href="/market" className={active === "market" ? "nav-active" : ""} aria-current={active === "market" ? "page" : undefined}>
                중고장터
              </Link>
              <Link href="/carpool" className={active === "carpool" ? "nav-active" : ""} aria-current={active === "carpool" ? "page" : undefined}>
                카풀·동행
              </Link>
            </nav>
          </div>

          <div className="community-header-actions">
            <ThemeToggle />
            <form className={`header-search${searchOpen ? " open" : ""}`} onSubmit={handleSearch}>
              <button type="button" onClick={() => setSearchOpen((open) => !open)} aria-label={searchOpen ? "검색 닫기" : "검색 열기"} aria-expanded={searchOpen}>
                <span className="material-symbols-outlined">search</span>
              </button>
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="게시글 검색" aria-label="통합 검색" tabIndex={searchOpen ? 0 : -1} />
            </form>
            {!loading && (user ? (
              <div className="header-profile-wrap">
                <Link href="/profile" className="header-profile" title={`${user.nickname} 프로필`}>
                  <Image
                    src={user.profileImageUrl || "/images/default-profile-avatar.png"}
                    alt=""
                    width={30}
                    height={30}
                    unoptimized={Boolean(user.profileImageUrl)}
                    className={user.profileImageUrl ? undefined : "header-default-profile"}
                  />
                </Link>
                <button type="button" onClick={handleLogout} className="header-logout">로그아웃</button>
              </div>
            ) : (
              <Link href="/login" className="header-login" aria-label="로그인" title="로그인"><span className="material-symbols-outlined">person</span></Link>
            ))}
            <button type="button" className="header-menu-button" onClick={() => setMobileOpen((open) => !open)} aria-label={mobileOpen ? "메뉴 닫기" : "메뉴 열기"} aria-expanded={mobileOpen}>
              <span className="material-symbols-outlined">{mobileOpen ? "close" : "menu"}</span>
            </button>
          </div>
        </div>
        <div className={`community-mobile-panel${mobileOpen ? " open" : ""}`}>
          <div className="community-container">
            <form className="mobile-header-search" onSubmit={handleSearch}>
              <span className="material-symbols-outlined">search</span>
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="게시글 검색" aria-label="통합 검색" />
              <button type="submit">검색</button>
            </form>
            <nav aria-label="모바일 메뉴">
              <strong>게시판</strong>
              {boardItems.map((item) => (
                <Link key={item.key} href={item.href} className={isBoardItemActive(item.key) ? "active" : ""} onClick={() => setMobileOpen(false)}>{item.label}</Link>
              ))}
              <Link href="/resort-cam" className={active === "resort" ? "active" : ""} onClick={() => setMobileOpen(false)}>슬로프캠</Link>
              <Link href="/market" className={active === "market" ? "active" : ""} onClick={() => setMobileOpen(false)}>중고장터</Link>
              <Link href="/carpool" className={active === "carpool" ? "active" : ""} onClick={() => setMobileOpen(false)}>카풀·동행</Link>
            </nav>
          </div>
        </div>
      </header>
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
        <div className="community-footer-brand"><strong>SNOWTHING | 눈팅</strong><span>Winter Sports Community</span></div>
        <nav><Link href="/">이용약관</Link><Link href="/">개인정보처리방침</Link><Link href="/">게시판 운영원칙</Link><Link href="/">고객센터</Link></nav>
      </div>
      <div className="community-container community-copyright">Copyright © SNOWTHING Alpine Community. All rights reserved.</div>
    </footer>
  );
}
