"use client";

import { useState } from "react";
import { Footer, TopNav } from "../components/SiteChrome";

interface ResortStatusItem {
  id: number;
  name: string;
  region: string;
  weather: string;
  temp: string;
  slopeStatus: string;
  liftStatus: string;
  updatedAt: string;
  crowded: boolean;
}

const resortsData: ResortStatusItem[] = [
  { id: 1, name: "휘닉스파크", region: "강원 평창", weather: "맑음", temp: "-4°C", slopeStatus: "12 / 14 슬로프 운영", liftStatus: "보통", updatedAt: "10분 전", crowded: false },
  { id: 2, name: "용평 리조트", region: "강원 평창", weather: "구름 조금", temp: "-6°C", slopeStatus: "20 / 28 슬로프 운영", liftStatus: "혼잡", updatedAt: "5분 전", crowded: true },
  { id: 3, name: "하이원 리조트", region: "강원 정선", weather: "눈", temp: "-8°C", slopeStatus: "15 / 18 슬로프 운영", liftStatus: "원활", updatedAt: "15분 전", crowded: false },
  { id: 4, name: "비발디파크", region: "강원 홍천", weather: "맑음", temp: "-2°C", slopeStatus: "10 / 12 슬로프 운영", liftStatus: "혼잡", updatedAt: "3분 전", crowded: true },
  { id: 5, name: "웰리힐리파크", region: "강원 횡성", weather: "흐림", temp: "-5°C", slopeStatus: "14 / 16 슬로프 운영", liftStatus: "보통", updatedAt: "8분 전", crowded: false },
];

export default function ResortStatusPage() {
  const [resorts] = useState<ResortStatusItem[]>(resortsData);

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="resort" />
      <main className="snow-container px-5 py-8 lg:px-8 lg:py-10">
        <section className="mb-8 border-b-2 border-black pb-5">
          <span className="snow-label">Field Monitor</span>
          <h1 className="mt-2 text-4xl font-extrabold italic text-black">RESORT LIVE STATUS</h1>
          <p className="mt-3 max-w-3xl text-[var(--snow-muted)]">주요 리조트의 날씨, 슬로프, 리프트 혼잡도를 한눈에 확인합니다.</p>
        </section>

        <section className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {resorts.map((item) => (
            <article key={item.id} className="snow-card snow-card-hover bg-white p-6">
              <div className="mb-5 flex items-start justify-between gap-4">
                <div>
                  <span className="snow-chip">{item.region}</span>
                  <h2 className="mt-3 text-2xl font-extrabold text-black">{item.name}</h2>
                </div>
                <span className="font-mono text-xs text-[var(--snow-muted)]">{item.updatedAt}</span>
              </div>

              <div className="grid grid-cols-2 gap-4 border-y border-[var(--snow-border)] py-5">
                <Info label="Weather" value={`${item.weather} / ${item.temp}`} />
                <Info label="Slope" value={item.slopeStatus} />
              </div>

              <div className="mt-5 flex items-center justify-between">
                <span className="snow-label">Lift Queue</span>
                <span className={`snow-chip ${item.crowded ? "bg-[#fef2f2] text-[#dc2626]" : "snow-chip-green"}`}>{item.liftStatus}</span>
              </div>
            </article>
          ))}
        </section>
      </main>
      <Footer />
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="snow-label block">{label}</span>
      <p className="mt-1 text-sm font-bold leading-6 text-black">{value}</p>
    </div>
  );
}
