"use client";

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

const resorts: ResortStatusItem[] = [
  { id: 1, name: "휘닉스파크", region: "강원 평창", weather: "맑음", temp: "-4°C", slopeStatus: "12 / 14 슬로프 운영", liftStatus: "보통", updatedAt: "10분 전", crowded: false },
  { id: 2, name: "용평 리조트", region: "강원 평창", weather: "구름 조금", temp: "-6°C", slopeStatus: "20 / 28 슬로프 운영", liftStatus: "혼잡", updatedAt: "5분 전", crowded: true },
  { id: 3, name: "하이원 리조트", region: "강원 정선", weather: "눈", temp: "-8°C", slopeStatus: "15 / 18 슬로프 운영", liftStatus: "원활", updatedAt: "15분 전", crowded: false },
  { id: 4, name: "비발디파크", region: "강원 홍천", weather: "맑음", temp: "-2°C", slopeStatus: "10 / 12 슬로프 운영", liftStatus: "혼잡", updatedAt: "3분 전", crowded: true },
  { id: 5, name: "웰리힐리파크", region: "강원 횡성", weather: "흐림", temp: "-5°C", slopeStatus: "14 / 16 슬로프 운영", liftStatus: "보통", updatedAt: "8분 전", crowded: false },
];

export default function ResortStatusPage() {
  return (
    <div className="community-page">
      <TopNav active="resort" />
      <main className="community-container py-4 pb-12">
        <header className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="m-0 text-xl font-extrabold tracking-[-0.03em] text-slate-900">리조트 현황</h1>
            <p className="mt-1.5 text-sm text-slate-500">주요 리조트의 날씨와 슬로프 운영, 리프트 대기 상태를 확인할 수 있습니다.</p>
          </div>
          <span className="text-xs font-semibold text-slate-400">최근 제보 기준</span>
        </header>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {resorts.map((item) => (
            <article key={item.id} className="rounded-xl border border-slate-200 bg-white p-5 shadow-[0_4px_16px_rgba(15,41,66,0.055)] transition hover:border-sky-200">
              <header className="flex items-start justify-between gap-4 border-b border-slate-100 pb-4">
                <div>
                  <span className="inline-flex rounded-md bg-sky-50 px-2 py-1 text-xs font-bold text-sky-700">{item.region}</span>
                  <h2 className="mt-2.5 text-lg font-extrabold tracking-[-0.025em] text-slate-900">{item.name}</h2>
                </div>
                <time className="pt-1 text-xs text-slate-400">{item.updatedAt}</time>
              </header>

              <dl className="grid grid-cols-2 gap-4 border-b border-slate-100 py-4">
                <Info label="날씨" value={`${item.weather} · ${item.temp}`} />
                <Info label="슬로프 운영" value={item.slopeStatus} />
              </dl>

              <div className="flex items-center justify-between pt-4">
                <span className="text-xs font-bold text-slate-500">리프트 대기</span>
                <span className={`rounded-md px-2 py-1 text-xs font-extrabold ${item.crowded ? "bg-amber-50 text-amber-700" : "bg-emerald-50 text-emerald-700"}`}>{item.liftStatus}</span>
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
      <dt className="text-xs font-bold text-slate-400">{label}</dt>
      <dd className="mt-1.5 text-sm font-bold leading-6 text-slate-700">{value}</dd>
    </div>
  );
}
