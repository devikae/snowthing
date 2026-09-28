"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

type ViewMode = "list" | "album";

interface MarketCategory {
  code: string;
  name: string;
  sortOrder: number;
}

interface MarketItem {
  publicId: string;
  title: string;
  category: { code: string; name: string };
  productCondition: string;
  transactionMethod: string;
  tradeStatus: string;
  price: number;
  negotiable: boolean;
  free: boolean;
  thumbnailImageUrl: string | null;
  seller: { publicId: string; nickname: string; profileImageUrl: string | null };
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

const conditionLabels: Record<string, string> = {
  NEW: "새 상품",
  LIKE_NEW: "거의 새 상품",
  GOOD: "사용감 적음",
  USED: "사용감 있음",
  DAMAGED: "수리·하자 있음",
};

const tradeLabels: Record<string, string> = {
  ON_SALE: "판매 중",
  RESERVED: "예약 중",
  SOLD: "판매 완료",
};

function MarketImage({ item }: { item: MarketItem }) {
  return item.thumbnailImageUrl ? (
    <Image src={item.thumbnailImageUrl} alt={`${item.title} 상품 사진`} width={240} height={180} unoptimized />
  ) : (
    <span className="material-symbols-outlined">inventory_2</span>
  );
}

function MarketPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const page = Math.max(1, Number(searchParams.get("page") ?? "1") || 1);
  const keyword = searchParams.get("keyword") ?? "";
  const categoryCode = searchParams.get("categoryCode") ?? "";
  const productCondition = searchParams.get("productCondition") ?? "";
  const tradeStatus = searchParams.get("tradeStatus") ?? "";
  const [categories, setCategories] = useState<MarketCategory[]>([]);
  const [items, setItems] = useState<MarketItem[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState(keyword);
  const [viewMode, setViewMode] = useState<ViewMode>(() =>
    typeof window !== "undefined" && localStorage.getItem("snowthing:market:view") === "album"
      ? "album"
      : "list",
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const redirectToLogin = useCallback(() => {
    const returnUrl = `/market${searchParams.size ? `?${searchParams.toString()}` : ""}`;
    router.replace(`/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }, [router, searchParams]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const listParams = new URLSearchParams({ page: String(page), size: "20" });
      if (keyword.trim()) listParams.set("keyword", keyword.trim());
      if (categoryCode) listParams.set("categoryCode", categoryCode);
      if (productCondition) listParams.set("productCondition", productCondition);
      if (tradeStatus) listParams.set("tradeStatus", tradeStatus);
      const [categoryResponse, listResponse] = await Promise.all([
        fetch(API_ENDPOINTS.market.categories, { credentials: "include" }),
        fetch(`${API_ENDPOINTS.market.list}?${listParams}`, { credentials: "include" }),
      ]);
      if (categoryResponse.status === 401 || listResponse.status === 401) {
        redirectToLogin();
        return;
      }
      if (!categoryResponse.ok || !listResponse.ok) throw new Error("중고장터를 불러오지 못했습니다.");
      const categoryData: { categories?: MarketCategory[] } = await categoryResponse.json();
      const listData = await listResponse.json();
      setCategories(Array.isArray(categoryData.categories) ? categoryData.categories : []);
      setItems(Array.isArray(listData.content) ? listData.content : []);
      setTotalPages(Math.max(1, listData.pageInfo?.totalPages ?? 1));
    } catch {
      setError(true);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [categoryCode, keyword, page, productCondition, redirectToLogin, tradeStatus]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const move = (changes: Record<string, string | number | undefined>) => {
    const params = new URLSearchParams(searchParams.toString());
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === "" || (key === "page" && value === 1)) params.delete(key);
      else params.set(key, String(value));
    });
    router.push(`/market${params.size ? `?${params}` : ""}`);
  };

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    move({ keyword: searchKeyword.trim(), page: 1 });
  };

  const changeView = (next: ViewMode) => {
    setViewMode(next);
    localStorage.setItem("snowthing:market:view", next);
  };

  const visiblePages = useMemo(() => {
    const start = Math.max(1, Math.min(page - 2, totalPages - 4));
    return Array.from({ length: Math.min(5, totalPages) }, (_, index) => start + index);
  }, [page, totalPages]);

  return (
    <div className="community-page">
      <TopNav active="market" />
      <main className="community-container market-page">
        <header className="market-page-heading">
          <div><span className="material-symbols-outlined">sell</span><div><h1>중고장터</h1><p>회원끼리 장비를 등록하고 댓글과 연락처로 거래합니다.</p></div></div>
          <Link href="/market/new" className="snow-btn-primary"><span className="material-symbols-outlined">add</span>판매글 등록</Link>
        </header>

        <section className="market-filter-card">
          <form onSubmit={handleSearch} className="market-search-row">
            <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder="상품 제목 검색" aria-label="상품 제목 검색" />
            <button type="submit">검색</button>
          </form>
          <div className="market-filter-row">
            <select value={categoryCode} onChange={(event) => move({ categoryCode: event.target.value, page: 1 })} aria-label="상품 분류"><option value="">상품 분류 전체</option>{categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}</select>
            <select value={productCondition} onChange={(event) => move({ productCondition: event.target.value, page: 1 })} aria-label="상품 상태"><option value="">상품 상태 전체</option>{Object.entries(conditionLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select>
            <select value={tradeStatus} onChange={(event) => move({ tradeStatus: event.target.value, page: 1 })} aria-label="거래 상태"><option value="">거래 상태 전체</option>{Object.entries(tradeLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select>
            <button type="button" className="market-filter-reset" onClick={() => { setSearchKeyword(""); router.push("/market"); }}>초기화</button>
            <div className="market-view-toggle" aria-label="보기 방식"><button type="button" className={viewMode === "list" ? "active" : ""} onClick={() => changeView("list")} aria-pressed={viewMode === "list"}><span className="material-symbols-outlined">view_list</span></button><button type="button" className={viewMode === "album" ? "active" : ""} onClick={() => changeView("album")} aria-pressed={viewMode === "album"}><span className="material-symbols-outlined">grid_view</span></button></div>
          </div>
        </section>

        <section className={`market-results ${viewMode}`} aria-live="polite">
          {loading ? <div className="market-state"><span className="material-symbols-outlined spin">progress_activity</span><p>판매글을 불러오는 중입니다.</p></div>
            : error ? <div className="market-state"><span className="material-symbols-outlined">cloud_off</span><p>중고장터를 불러오지 못했습니다.</p><button onClick={() => void load()}>다시 시도</button></div>
            : items.length === 0 ? <div className="market-state"><span className="material-symbols-outlined">inventory_2</span><p>조건에 맞는 판매글이 없습니다.</p></div>
            : items.map((item) => <Link href={`/market/${item.publicId}`} key={item.publicId} className="market-result-card">
              <div className="market-result-image"><MarketImage item={item} /><em data-status={item.tradeStatus}>{tradeLabels[item.tradeStatus] ?? item.tradeStatus}</em></div>
              <div className="market-result-copy"><div><span>{item.category.name}</span><span>{conditionLabels[item.productCondition]}</span></div><strong>{item.title}</strong><p>{item.seller.nickname}<span>·</span>{new Date(item.createdAt).toLocaleDateString("ko-KR")}<span>·</span>댓글 {item.commentCount}</p></div>
              <b>{item.free ? "무료 나눔" : `${item.price.toLocaleString()}원`}</b>
            </Link>)}
        </section>

        {!loading && !error && totalPages > 1 && <nav className="board-pagination market-pagination" aria-label="페이지 이동"><button disabled={page === 1} onClick={() => move({ page: page - 1 })}><span className="material-symbols-outlined">chevron_left</span></button>{visiblePages.map((number) => <button key={number} className={number === page ? "current" : ""} onClick={() => move({ page: number })}>{number}</button>)}<button disabled={page === totalPages} onClick={() => move({ page: page + 1 })}><span className="material-symbols-outlined">chevron_right</span></button></nav>}
      </main>
      <Footer />
    </div>
  );
}

export default function MarketPage() {
  return <Suspense fallback={<div className="market-state">중고장터를 준비하는 중입니다.</div>}><MarketPageContent /></Suspense>;
}
