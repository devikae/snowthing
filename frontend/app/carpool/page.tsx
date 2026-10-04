"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

interface CarpoolItem {
  publicId: string;
  title: string;
  writerName: string;
  departureRegion: string;
  destinationResortName: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  departureAt: string;
  passengerCapacity: number;
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
}

interface CarpoolPageResponse {
  content: CarpoolItem[];
  totalElements: number;
  totalPages: number;
}

const PAGE_SIZE = 20;
const MAX_PAGE = 100;

function CarpoolList() {
  const searchParams = useSearchParams();
  const requestedPage = Number(searchParams.get("page") || "1");
  const page = Number.isFinite(requestedPage) && requestedPage > 0
    ? Math.min(Math.floor(requestedPage), MAX_PAGE)
    : 1;
  const [data, setData] = useState<CarpoolPageResponse>({ content: [], totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const cappedTotalPages = Math.min(data.totalPages, MAX_PAGE);
  const visiblePages = useMemo(() => {
    const pageIndex = page - 1;
    const start = Math.max(0, Math.min(pageIndex - 2, cappedTotalPages - 5));
    return Array.from(
      { length: Math.min(5, cappedTotalPages) },
      (_, index) => start + index + 1,
    );
  }, [cappedTotalPages, page]);

  useEffect(() => {
    void fetch(`${API_ENDPOINTS.carpool.list}?page=${page - 1}&size=${PAGE_SIZE}`, { credentials: "include" })
      .then((response) => {
        if (!response.ok) throw new Error();
        return response.json();
      })
      .then((response: CarpoolPageResponse) => setData({
        content: Array.isArray(response.content) ? response.content : [],
        totalElements: response.totalElements || 0,
        totalPages: response.totalPages || 0,
      }))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [page]);

  return <>
    <section className="carpool-list">
      <div className="carpool-list-toolbar"><strong>카풀 모집글</strong><span>총 {data.totalElements.toLocaleString("ko-KR")}건</span></div>
      {loading ? <div className="carpool-empty">카풀 모집글을 불러오는 중입니다.</div>
        : error ? <div className="carpool-empty"><p>카풀 모집글을 불러오지 못했습니다.</p><button type="button" onClick={() => window.location.reload()}>다시 시도</button></div>
          : data.content.length === 0 ? <div className="carpool-empty"><span className="material-symbols-outlined">directions_car</span><p>등록된 카풀 모집글이 없습니다.</p><Link href="/carpool/new">첫 모집글 등록하기</Link></div>
            : data.content.map((item) => <Link className="carpool-card" href={`/carpool/${item.publicId}`} key={item.publicId}>
              <div className="carpool-card-main">
                <div className="carpool-card-tags"><em>{item.tripType === "ROUND_TRIP" ? "왕복" : "편도"}</em><em>{item.equipmentLoadAvailable ? "장비 적재 가능" : "장비 적재 불가"}</em></div>
                <h2>{item.title}</h2>
                <p className="carpool-route"><strong>{item.departureRegion}</strong><span className="material-symbols-outlined">east</span><strong>{item.destinationResortName}</strong></p>
                <p className="carpool-meta"><span className="material-symbols-outlined">schedule</span>{new Date(item.departureAt).toLocaleString("ko-KR")}<span className="material-symbols-outlined">person</span>모집 {item.passengerCapacity}명</p>
              </div>
              <div className="carpool-card-cost"><small>예상 1인 비용</small><b>{item.estimatedCostPerPerson.toLocaleString("ko-KR")}원</b><span>작성자 {item.writerName}</span></div>
            </Link>)}
    </section>
    {cappedTotalPages > 1 && <nav className="carpool-pagination" aria-label="카풀 목록 페이지">
      <Link aria-disabled={page <= 1} className={page <= 1 ? "disabled" : ""} href={`/carpool?page=${Math.max(1, page - 1)}`}>이전</Link>
      {visiblePages.map((number) => <Link className={number === page ? "active" : ""} href={`/carpool?page=${number}`} key={number}>{number}</Link>)}
      <Link aria-disabled={page >= cappedTotalPages} className={page >= cappedTotalPages ? "disabled" : ""} href={`/carpool?page=${Math.min(cappedTotalPages, page + 1)}`}>다음</Link>
    </nav>}
  </>;
}

export default function CarpoolPage() {
  return <>
    <TopNav active="carpool" />
    <main className="community-container carpool-page">
      <header className="carpool-heading">
        <div><span className="material-symbols-outlined">directions_car</span><div><p className="carpool-eyebrow">함께 가면 더 가벼운 이동</p><h1>카풀·동행 모집</h1><p>출발지와 리조트, 예상 비용을 확인하고 이동 계획을 세워보세요.</p></div></div>
        <Link className="carpool-primary-button" href="/carpool/new"><span className="material-symbols-outlined">add</span>모집글 등록</Link>
      </header>
      <Suspense fallback={<div className="carpool-empty">카풀 모집글을 불러오는 중입니다.</div>}><CarpoolList /></Suspense>
    </main>
    <Footer />
  </>;
}
