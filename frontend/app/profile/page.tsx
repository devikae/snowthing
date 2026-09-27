"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { Footer, TopNav } from "../components/SiteChrome";

const demoPhotos = [
  "https://images.unsplash.com/photo-1551698618-1dfe5d97d256?w=900&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1565992441121-4367c2967103?w=900&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1546707012-0c9f63eb0775?w=900&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1482867996988-29ec3a0f1acd?w=900&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1518602164578-cd0074062767?w=900&auto=format&fit=crop&q=80",
];

export default function ProfilePage() {
  const [photoIndex, setPhotoIndex] = useState(0);

  const handleNextPhoto = () => setPhotoIndex((current) => (current + 1) % demoPhotos.length);
  const handlePrevPhoto = () => setPhotoIndex((current) => (current - 1 + demoPhotos.length) % demoPhotos.length);

  return (
    <div className="community-page">
      <TopNav active="profile" />
      <main className="community-container py-4 pb-12">
        <section className="mx-auto max-w-6xl">
          <header className="mb-4">
            <h1 className="m-0 text-xl font-extrabold tracking-[-0.03em] text-slate-900">내 프로필</h1>
            <p className="mt-1.5 text-sm text-slate-500">라이딩 정보와 사진을 한곳에서 관리할 수 있습니다.</p>
          </header>

          <div className="grid gap-6 rounded-xl border border-slate-200 bg-white p-5 shadow-[0_4px_16px_rgba(15,41,66,0.055)] md:grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)] md:p-6">
            <div>
              <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-slate-100">
                <Image src={demoPhotos[photoIndex]} alt={`라이더 갤러리 ${photoIndex + 1}`} fill sizes="(max-width: 768px) 100vw, 55vw" className="object-cover" unoptimized />
                <span className="absolute left-3 top-3 rounded-md bg-slate-950/80 px-2.5 py-1 text-xs font-bold text-white">{photoIndex + 1} / {demoPhotos.length}</span>
                <button type="button" onClick={handlePrevPhoto} aria-label="이전 사진" className="absolute left-3 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg bg-white/95 text-slate-700 shadow-sm hover:text-sky-700">
                  <span className="material-symbols-outlined text-[19px]">chevron_left</span>
                </button>
                <button type="button" onClick={handleNextPhoto} aria-label="다음 사진" className="absolute right-3 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center rounded-lg bg-white/95 text-slate-700 shadow-sm hover:text-sky-700">
                  <span className="material-symbols-outlined text-[19px]">chevron_right</span>
                </button>
              </div>

              <div className="mt-3 grid grid-cols-5 gap-2">
                {demoPhotos.map((photo, index) => (
                  <button key={photo} type="button" onClick={() => setPhotoIndex(index)} aria-label={`${index + 1}번 사진 보기`} className={`relative aspect-square overflow-hidden rounded-lg border-2 ${photoIndex === index ? "border-sky-600" : "border-transparent opacity-65 hover:opacity-100"}`}>
                    <Image src={photo} alt="" fill sizes="120px" className="object-cover" unoptimized />
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-col justify-between gap-8 py-1">
              <div>
                <h2 className="text-2xl font-extrabold tracking-[-0.03em] text-slate-900">카빙하는 보더</h2>
                <p className="mt-3 text-sm leading-7 text-slate-600">휘팍과 용평을 주로 다니며 주말마다 카빙 연습을 하는 라이더입니다.</p>

                <dl className="mt-6 grid grid-cols-2 gap-3">
                  <Spec label="베이스 리조트" value="휘팍, 용평" />
                  <Spec label="활동 지역" value="서울 송파구" />
                  <Spec label="라이딩 스타일" value="카빙, 트릭" />
                  <Spec label="레벨" value="중급" />
                </dl>
              </div>

              <div className="flex flex-col gap-2 sm:flex-row">
                <button type="button" className="inline-flex h-11 flex-1 items-center justify-center gap-2 rounded-lg bg-[#0f2942] px-4 text-sm font-extrabold text-white hover:bg-sky-700">
                  <span className="material-symbols-outlined text-[18px]">mail</span>
                  쪽지 보내기
                </button>
                <Link href="/profile" className="inline-flex h-11 items-center justify-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-bold text-slate-700 hover:border-sky-300 hover:bg-sky-50">프로필 수정</Link>
              </div>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}

function Spec({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 p-3">
      <dt className="text-xs font-bold text-slate-400">{label}</dt>
      <dd className="mt-1.5 text-sm font-extrabold text-slate-700">{value}</dd>
    </div>
  );
}
