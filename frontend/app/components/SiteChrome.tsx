"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS } from "../lib/api";

export type ActiveNav = "home" | "posts" | "resort" | "profile" | "login" | "signup";

interface MemberUser {
  publicId: string;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
}

const navItems: { href: string; label: string; key: ActiveNav }[] = [
  { href: "/resort", label: "Resorts", key: "resort" },
  { href: "/posts", label: "Community", key: "posts" },
  { href: "/profile", label: "Rider Card", key: "profile" },
];

const categories = [
  { href: "/posts", label: "전체 게시판", icon: "forum", key: "all" },
  { href: "/posts?category=FREE", label: "자유 게시판", icon: "terrain", key: "free" },
  { href: "/posts?category=ANONYMOUS", label: "익명 게시판", icon: "visibility_off", key: "anonymous" },
  { href: "/posts?category=QNA", label: "장비 Q&A", icon: "help", key: "qna" },
  { href: "/posts?category=FOOD", label: "리조트 맛집", icon: "restaurant", key: "food" },
];

export function TopNav({ active = "home" }: { active?: ActiveNav }) {
  const [user, setUser] = useState<MemberUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(API_ENDPOINTS.members.me, {
          credentials: "include",
        });
        if (res.ok) {
          const data = await res.json();
          setUser(data);
        } else {
          setUser(null);
        }
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleLogout = async () => {
    try {
      await csrfFetch(API_ENDPOINTS.auth.logout, {
        method: "POST",
      });
    } catch {
      // ignore
    } finally {
      setUser(null);
      window.location.href = "/";
    }
  };

  return (
    <header className="sticky top-0 z-50 border-b border-[var(--snow-border)] bg-white">
      <div className="snow-container flex h-16 items-center justify-between px-5 lg:px-8">
        <div className="flex items-center gap-10">
          <Link href="/" className="snow-brand">
            SnowThing
          </Link>
          <nav className="hidden items-center gap-8 md:flex">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`font-mono text-xs uppercase tracking-[0.22em] transition ${
                  active === item.key
                    ? "border-b-2 border-black pb-1 text-black"
                    : "text-[var(--snow-ink-soft)] hover:text-black"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-4">
          {!loading && (
            <>
              {user ? (
                <div className="flex items-center gap-3">
                  <Link
                    href="/profile"
                    className="font-mono text-xs font-bold text-black hover:underline"
                  >
                    {user.nickname}님
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="font-mono text-xs uppercase tracking-[0.14em] text-[var(--snow-ink-soft)] hover:text-[#dc2626]"
                  >
                    Sign Out
                  </button>
                </div>
              ) : (
                <Link
                  href="/login"
                  className="font-mono text-xs uppercase tracking-[0.14em] text-[var(--snow-ink-soft)] hover:text-black sm:inline"
                >
                  Sign In
                </Link>
              )}
            </>
          )}
          <Link href="/posts/create" className="snow-btn-primary min-h-9 px-4">
            <span className="material-symbols-outlined text-[16px]">edit</span>
            Write
          </Link>
        </div>
      </div>
    </header>
  );
}

export function SideCategories({ active = "all" }: { active?: string }) {
  return (
    <aside className="hidden border-r-2 border-black bg-white px-6 py-8 lg:block">
      <div className="mb-8">
        <h2 className="text-2xl font-extrabold italic text-black">Categories</h2>
        <p className="mt-1 text-sm text-[var(--snow-muted)]">Find your ride</p>
      </div>
      <nav className="flex flex-col gap-2">
        {categories.map((category) => (
          <Link
            key={category.href}
            href={category.href}
            className={`flex items-center gap-3 rounded px-4 py-3 text-sm font-semibold transition ${
              active === category.key
                ? "bg-[var(--snow-surface-low)] text-black"
                : "text-[var(--snow-ink-soft)] hover:bg-[var(--snow-background)] hover:text-black"
            }`}
          >
            <span className="material-symbols-outlined text-[19px]">{category.icon}</span>
            {category.label}
          </Link>
        ))}
      </nav>
    </aside>
  );
}

export function Footer() {
  return (
    <footer className="border-t border-[var(--snow-border)] bg-white">
      <div className="snow-container flex flex-col gap-4 px-5 py-6 text-xs text-[var(--snow-muted)] md:flex-row md:items-center md:justify-between lg:px-8">
        <Link href="/" className="snow-brand text-xl">
          SnowThing
        </Link>
        <div className="flex flex-wrap gap-6 font-mono">
          <span>© 2026 Snowthing</span>
          <Link href="/">Privacy Policy</Link>
          <Link href="/">Terms of Service</Link>
          <Link href="/">Contact Support</Link>
        </div>
      </div>
    </footer>
  );
}
