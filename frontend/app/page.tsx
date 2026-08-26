"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Footer, SideCategories, TopNav } from "./components/SiteChrome";
import { API_ENDPOINTS } from "./lib/api";

interface MemberProfile {
  publicId: string;
  email: string;
  nickname: string;
  role: string;
}

const trendingCards = [
  {
    label: "Gear Review",
    title: "24/25 시즌 바인딩 세팅 팁",
    image: "https://images.unsplash.com/photo-1551524164-687a55dd1126?w=900&auto=format&fit=crop&q=80",
  },
  {
    label: "Discussion",
    title: "라이딩 자세가 무너질 때 체크할 것들",
    image: "https://images.unsplash.com/photo-1605540436563-5bca919ae766?w=900&auto=format&fit=crop&q=80",
  },
  {
    label: "Resort",
    title: "주말 강원권 리조트 혼잡도 공유",
    image: "https://images.unsplash.com/photo-1488590528505-98d2b5aba04b?w=900&auto=format&fit=crop&q=80",
  },
  {
    label: "Question",
    title: "첫 데크를 고를 때 플렉스 기준",
    image: "https://images.unsplash.com/photo-1518602164578-cd0074062767?w=900&auto=format&fit=crop&q=80",
  },
];

const topPosts = [
  ["FREE", "웰리힐리 야간 타보신 분 있나요?", 45],
  ["GEAR", "부츠 열성형 전후 차이가 큰가요?", 32],
  ["QNA", "초보가 전향각 바로 가도 될까요?", 28],
  ["FOOD", "용평 근처 아침 식사 추천", 19],
  ["RESORT", "하이원 리프트 대기 공유", 15],
];

export default function HomePage() {
  const [profile, setProfile] = useState<MemberProfile | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const res = await fetch(API_ENDPOINTS.members.me, {
            credentials: "include",
          });
          if (res.ok) {
            const data: MemberProfile = await res.json();
            setProfile(data);
          }
        } catch {
          setProfile(null);
        }
      })();
    }, 0);

    return () => window.clearTimeout(timer);
  }, []);

  return (
    <div className="min-h-screen bg-[var(--snow-background)] text-[var(--snow-ink)]">
      <TopNav active="home" />
      <div className="border-b border-[var(--snow-border)] bg-[var(--snow-surface-mid)] py-2">
        <div className="snow-container overflow-hidden px-5 lg:px-8">
          <p className="whitespace-nowrap font-mono text-xs uppercase tracking-[0.16em] text-[var(--snow-ink-soft)]">
            Yongpyong: -5°C, fresh powder <span className="mx-5 text-[var(--snow-faint)]">|</span>
            High1: -3°C, clear <span className="mx-5 text-[var(--snow-faint)]">|</span>
            Welli Hilli: -4°C, snowing <span className="mx-5 text-[var(--snow-faint)]">|</span>
            Phoenix: -2°C, overcast
          </p>
        </div>
      </div>

      <div className="snow-container snow-grid-shell bg-[var(--snow-background)]">
        <SideCategories active="all" />
        <main className="px-5 py-8 lg:px-8 lg:py-10">
          <section className="snow-card relative min-h-[330px] overflow-hidden p-8 md:p-10">
            <img
              src="https://images.unsplash.com/photo-1482867996988-29ec3a0f1acd?w=1600&auto=format&fit=crop&q=80"
              alt="눈 덮인 산맥"
              className="absolute inset-0 h-full w-full object-cover opacity-20 grayscale"
            />
            <div className="relative z-10 flex min-h-[260px] flex-col justify-end gap-8 md:flex-row md:items-end md:justify-between">
              <div className="max-w-3xl">
                <span className="snow-chip snow-chip-dark mb-6">Community Board</span>
                <h1 className="snow-heading-xl">SHRED-TALK</h1>
                <p className="mt-5 max-w-2xl text-lg leading-8 text-[var(--snow-ink-soft)]">
                  장비 이야기, 리조트 상황, 익명 고민, 라이딩 질문을 한곳에서 나누는 스노보더 커뮤니티입니다.
                </p>
              </div>
              <Link href="/posts/create" className="snow-btn-primary shrink-0">
                <span className="material-symbols-outlined text-[17px]">add_box</span>
                Create Post
              </Link>
            </div>
          </section>

          <section className="mt-10 grid gap-8 xl:grid-cols-[minmax(0,1fr)_280px]">
            <div>
              <div className="mb-6 flex items-end justify-between border-b-2 border-black pb-3">
                <h2 className="flex items-center gap-2 text-2xl font-extrabold text-black">
                  <span className="material-symbols-outlined text-[22px] text-[var(--snow-error)]">local_fire_department</span>
                  Trending Topics
                </h2>
                <Link href="/posts" className="snow-label text-black">
                  View all
                </Link>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                {trendingCards.map((card) => (
                  <Link key={card.title} href="/posts" className="snow-card snow-card-hover group relative min-h-[210px] overflow-hidden p-5">
                    <img src={card.image} alt="" className="absolute inset-0 h-full w-full object-cover opacity-[0.22] grayscale transition group-hover:opacity-[0.32]" />
                    <div className="relative z-10 flex h-full min-h-[170px] flex-col justify-between">
                      <span className="snow-chip bg-white">{card.label}</span>
                      <h3 className="max-w-[85%] text-2xl font-extrabold leading-tight text-black">{card.title}</h3>
                    </div>
                  </Link>
                ))}
              </div>
            </div>

            <aside className="space-y-6">
              <div className="snow-card p-6">
                <h3 className="text-2xl font-extrabold italic text-black">Quick Actions</h3>
                <div className="mt-5 grid gap-3">
                  <Link href="/posts" className="snow-btn-primary w-full">
                    게시판 보기
                  </Link>
                  {profile ? (
                    <Link href="/profile" className="snow-btn-secondary w-full">
                      {profile.nickname} 프로필
                    </Link>
                  ) : (
                    <Link href="/signup" className="snow-btn-secondary w-full">
                      회원가입
                    </Link>
                  )}
                </div>
              </div>

              <div className="snow-card p-6">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="text-2xl font-extrabold text-black">Top Posts</h3>
                  <span className="snow-label">Real-time</span>
                </div>
                <div className="divide-y divide-[var(--snow-border)]">
                  {topPosts.map(([category, title, count]) => (
                    <Link key={title} href="/posts" className="grid grid-cols-[56px_minmax(0,1fr)_42px] gap-3 py-3 text-sm">
                      <span className="snow-label">{category}</span>
                      <span className="truncate font-semibold text-black">{title}</span>
                      <span className="font-mono text-xs text-[var(--snow-error)]">[{count}]</span>
                    </Link>
                  ))}
                </div>
              </div>
            </aside>
          </section>
        </main>
      </div>
      <Footer />
    </div>
  );
}
