"use client";

import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { useSearchParams } from "next/navigation";
import { type FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";
import { carpoolCostLabel, formatCarpoolDateTime } from "./format";

interface CarpoolItem {
  publicId: string;
  title: string;
  writerName: string;
  departureRegion: string;
  destinationResortName: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  departureAt: string;
  passengerCapacity: number;
  costMode: "AUTO" | "MANUAL";
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
  const keyword = (searchParams.get("keyword") ?? "").trim();
  const [searchKeyword, setSearchKeyword] = useState(keyword);
  const keywordQuery = keyword ? `&keyword=${encodeURIComponent(keyword)}` : "";
  const [data, setData] = useState<CarpoolPageResponse>({ content: [], totalElements: 0, totalPages: 0 });
  // 페이지마다 결과를 따로 기록해, 한 번 실패해도 다른 페이지로 옮기면 오류 화면이 남지 않게 한다.
  const [loadedPage, setLoadedPage] = useState<number | null>(null);
  const [failedPage, setFailedPage] = useState<number | null>(null);
  const error = failedPage === page;
  const loading = loadedPage !== page && !error;
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
    let stale = false;
    void fetch(`${API_ENDPOINTS.carpool.list}?page=${page - 1}&size=${PAGE_SIZE}${keywordQuery}`, { credentials: "include" })
      .then((response) => {
        if (!response.ok) throw new Error();
        return response.json();
      })
      .then((response: CarpoolPageResponse) => {
        if (stale) return;
        setData({
          content: Array.isArray(response.content) ? response.content : [],
          totalElements: response.totalElements || 0,
          totalPages: response.totalPages || 0,
        });
        setLoadedPage(page);
        setFailedPage(null);
      })
      .catch(() => {
        if (!stale) setFailedPage(page);
      });
    return () => {
      stale = true;
    };
  }, [keywordQuery, page]);

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    const next = searchKeyword.trim();
    window.location.assign(next ? `/carpool?keyword=${encodeURIComponent(next)}` : "/carpool");
  };

  return <>
    <form className="carpool-search" onSubmit={handleSearch} role="search">
      <input
        aria-label="카풀 검색"
        placeholder="제목, 출발지, 집결지, 리조트 검색"
        value={searchKeyword}
        maxLength={100}
        onChange={(event) => setSearchKeyword(event.target.value)}
      />
      <button type="submit">검색</button>
    </form>
    <section className="carpool-list">
      <div className="carpool-list-toolbar"><strong>카풀 모집글</strong>{!loading && !error && <span>총 {data.totalElements.toLocaleString("ko-KR")}건</span>}</div>
      {loading ? <div className="carpool-empty">카풀 모집글을 불러오는 중입니다.</div>
        : error ? <div className="carpool-empty"><p>카풀 모집글을 불러오지 못했습니다.</p><button type="button" onClick={() => window.location.reload()}>다시 시도</button></div>
          : data.content.length === 0 ? keyword ? <div className="carpool-empty"><p>검색 결과가 없습니다.</p></div> : <div className="carpool-empty"><span className="material-symbols-outlined" aria-hidden="true">directions_car</span><p>등록된 카풀 모집글이 없습니다.</p><Link href="/carpool/new">첫 모집글 등록하기</Link></div>
            : data.content.map((item) => <Link className="carpool-card" href={`/carpool/${item.publicId}`} key={item.publicId}>
              <div className="carpool-card-main">
                <div className="carpool-card-tags"><em>{item.tripType === "ROUND_TRIP" ? "왕복" : "편도"}</em><em>{item.equipmentLoadAvailable ? "장비 적재 가능" : "장비 적재 불가"}</em></div>
                <h2>{item.title}</h2>
                <p className="carpool-route"><strong>{item.departureRegion}</strong><span className="material-symbols-outlined" aria-hidden="true">east</span><strong>{item.destinationResortName}</strong></p>
                <p className="carpool-meta"><span className="material-symbols-outlined" aria-hidden="true">schedule</span>{formatCarpoolDateTime(item.departureAt)}<span className="material-symbols-outlined" aria-hidden="true">person</span>모집 {item.passengerCapacity}명</p>
              </div>
              <div className="carpool-card-cost"><small>{carpoolCostLabel(item.costMode)}</small><b>{item.estimatedCostPerPerson.toLocaleString("ko-KR")}원</b><span>작성자 {item.writerName}</span></div>
            </Link>)}
    </section>
    {cappedTotalPages > 1 && <nav className="carpool-pagination" aria-label="카풀 목록 페이지">
      <Link aria-disabled={page <= 1} className={page <= 1 ? "disabled" : ""} href={`/carpool?page=${Math.max(1, page - 1)}${keywordQuery}`}>이전</Link>
      {visiblePages.map((number) => <Link className={number === page ? "active" : ""} href={`/carpool?page=${number}${keywordQuery}`} key={number}>{number}</Link>)}
      <Link aria-disabled={page >= cappedTotalPages} className={page >= cappedTotalPages ? "disabled" : ""} href={`/carpool?page=${Math.min(cappedTotalPages, page + 1)}${keywordQuery}`}>다음</Link>
    </nav>}
  </>;
}

export default function CarpoolPage() {
  return (
    <div className="community-page">
      <TopNav active="carpool" />
      <main className="community-container carpool-page">
        <PageHeading title="카풀과 동행" description="출발지와 리조트가 맞는 동행을 찾아보세요."
          actions={<Link href="/carpool/new">모집글 등록</Link>} />
        <Suspense fallback={<div className="carpool-empty">카풀 모집글을 불러오는 중입니다.</div>}>
          <CarpoolList />
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}
