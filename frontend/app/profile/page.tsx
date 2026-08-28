"use client";

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

  const handleNextPhoto = () => {
    setPhotoIndex((current) => (current + 1) % demoPhotos.length);
  };

  const handlePrevPhoto = () => {
    setPhotoIndex((current) => (current - 1 + demoPhotos.length) % demoPhotos.length);
  };

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="profile" />
      <main className="snow-container px-5 py-8 lg:px-8 lg:py-10">
        <section className="mx-auto max-w-5xl">
          <div className="mb-8 border-b-2 border-black pb-5">
            <span className="snow-label">Rider Identity</span>
            <h1 className="mt-2 text-4xl font-extrabold italic text-black">RIDER CARD & GALLERY</h1>
            <p className="mt-3 text-[var(--snow-muted)]">라이더 명함과 5장 갤러리 프로필 예시입니다.</p>
          </div>

          <div className="snow-card grid gap-8 bg-white p-6 md:grid-cols-[minmax(0,1.05fr)_minmax(320px,0.95fr)] md:p-8">
            <div>
              <div className="relative aspect-[4/3] overflow-hidden rounded border border-[var(--snow-border)] bg-black">
                <img src={demoPhotos[photoIndex]} alt={`라이더 갤러리 ${photoIndex + 1}`} className="h-full w-full object-cover grayscale" />
                <div className="absolute left-3 top-3 bg-black px-3 py-1 font-mono text-[11px] font-bold uppercase tracking-[0.08em] text-white">
                  {photoIndex + 1} / {demoPhotos.length}
                </div>
                <button onClick={handlePrevPhoto} className="absolute left-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded bg-white text-black">
                  <span className="material-symbols-outlined text-[18px]">chevron_left</span>
                </button>
                <button onClick={handleNextPhoto} className="absolute right-3 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded bg-white text-black">
                  <span className="material-symbols-outlined text-[18px]">chevron_right</span>
                </button>
              </div>
              <div className="mt-3 grid grid-cols-5 gap-2">
                {demoPhotos.map((photo, index) => (
                  <button key={photo} onClick={() => setPhotoIndex(index)} className={`aspect-square overflow-hidden rounded border-2 ${photoIndex === index ? "border-black" : "border-transparent opacity-60"}`}>
                    <img src={photo} alt="" className="h-full w-full object-cover grayscale" />
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-col justify-between gap-8">
              <div>
                <div className="mb-5 flex items-center justify-between gap-3">
                  <span className="snow-chip snow-chip-green">Snowboarder Card</span>
                  <span className="snow-label">Sample Profile</span>
                </div>
                <h2 className="text-4xl font-extrabold text-black">카빙하는 보더</h2>
                <p className="mt-4 text-lg leading-8 text-[var(--snow-ink-soft)]">
                  휘닉스파크와 용평을 주로 다니며 주말마다 카빙 연습을 하는 라이더입니다.
                </p>

                <div className="mt-8 grid grid-cols-2 gap-4 border-t border-[var(--snow-border)] pt-6">
                  <Spec label="Base" value="휘닉스파크, 용평" />
                  <Spec label="Region" value="서울 송파구" />
                  <Spec label="Style" value="카빙, 트릭" />
                  <Spec label="Level" value="중급" />
                </div>
              </div>

              <div className="flex flex-col gap-3 sm:flex-row">
                <button className="snow-btn-primary flex-1">
                  <span className="material-symbols-outlined text-[17px]">mail</span>
                  쪽지 보내기
                </button>
                <Link href="/" className="snow-btn-secondary">
                  프로필 수정
                </Link>
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
    <div>
      <span className="snow-label block">{label}</span>
      <span className="mt-1 block font-bold text-black">{value}</span>
    </div>
  );
}
