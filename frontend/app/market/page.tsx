"use client";
import { PageHeading } from "../components/PageHeading";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";
import { readErrorMessage } from "../lib/http";
import { formatKoreanDate } from "../lib/koreanDate";
import { PRODUCT_CONDITION_LABELS } from "./marketOptions";
import { readLocal, writeLocal } from "../lib/safeStorage";

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
  seller: { publicId: string | null; nickname: string; profileImageUrl: string | null };
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

const tradeLabels: Record<string, string> = {
  ON_SALE: "판매 중",
  RESERVED: "예약 중",
  SOLD: "판매 완료",
};

function MarketImage({ item }: { item: MarketItem }) {
  return item.thumbnailImageUrl ? (
    <Image src={item.thumbnailImageUrl} alt={`${item.title} 상품 사진`} width={240} height={180} unoptimized />
  ) : (
    <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">inventory_2</span>
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
    typeof window !== "undefined" && readLocal("snowthing:market:view") === "album"
      ? "album"
      : "list",
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  // 검색어 길이, 검색 횟수 제한처럼 사용자가 고칠 수 있는 실패는 서버 문구를 목록 자리에 보여 준다.
  const [rejectedMessage, setRejectedMessage] = useState<string | null>(null);

  const redirectToLogin = useCallback(() => {
    const returnUrl = `/market${searchParams.size ? `?${searchParams.toString()}` : ""}`;
    router.replace(`/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }, [router, searchParams]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    setRejectedMessage(null);
    try {
      const listParams = new URLSearchParams({ page: String(page), size: "20" });
      if (keyword.trim()) listParams.set("keyword", keyword.trim());
      if (categoryCode) listParams.set("categoryCode", categoryCode);
      if (productCondition) listParams.set("productCondition", productCondition);
      if (tradeStatus === "ALL") listParams.set("includeSold", "true");
      else if (tradeStatus) listParams.set("tradeStatus", tradeStatus);
      const [categoryResponse, listResponse] = await Promise.all([
        fetch(API_ENDPOINTS.market.categories, { credentials: "include" }),
        fetch(`${API_ENDPOINTS.market.list}?${listParams}`, { credentials: "include" }),
      ]);
      if (categoryResponse.status === 401 || listResponse.status === 401) {
        redirectToLogin();
        return;
      }
      if (categoryResponse.ok && (listResponse.status === 400 || listResponse.status === 429)) {
        const rejected = await readErrorMessage(listResponse, "중고장터를 불러오지 못했습니다.");
        const categoryData: { categories?: MarketCategory[] } = await categoryResponse.json();
        setCategories(Array.isArray(categoryData.categories) ? categoryData.categories : []);
        setItems([]);
        setRejectedMessage(rejected);
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

  const keywordTooShort = Array.from(searchKeyword.trim()).length === 1;

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    // 서버도 한 글자 검색을 거절한다. 입력칸 아래 안내가 이미 떠 있으므로 요청을 보내지 않는다.
    if (keywordTooShort) return;
    const params = new URLSearchParams(searchParams.toString());
    params.delete("page");
    if (searchKeyword.trim()) params.set("keyword", searchKeyword.trim());
    else params.delete("keyword");
    window.location.assign(`/market${params.size ? `?${params}` : ""}`);
  };

  const changeView = (next: ViewMode) => {
    setViewMode(next);
    writeLocal("snowthing:market:view", next);
  };

  const visiblePages = useMemo(() => {
    const start = Math.max(1, Math.min(page - 2, totalPages - 4));
    return Array.from({ length: Math.min(5, totalPages) }, (_, index) => start + index);
  }, [page, totalPages]);

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container market-page">
        <PageHeading title="중고장터" description="라이더끼리 장비를 사고팔아요."
          actions={<Link href="/market/new">판매글 등록</Link>} />

        <section className="market-filter-card">
          <form onSubmit={handleSearch} className="market-search-row">
            <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder="상품 제목 검색" aria-label="상품 제목 검색" aria-describedby={keywordTooShort ? "market-search-hint" : undefined} maxLength={50} />
            <button type="submit" disabled={keywordTooShort}>검색</button>
            {keywordTooShort && <p id="market-search-hint" className="search-length-hint" role="status">한 글자로는 검색할 수 없어요.</p>}
          </form>
          <div className="market-filter-row">
            <select value={categoryCode} onChange={(event) => move({ categoryCode: event.target.value, page: 1 })} aria-label="상품 분류"><option value="">상품 분류 전체</option>{categories.map((category) => <option key={category.code} value={category.code}>{category.name}</option>)}</select>
            <select value={productCondition} onChange={(event) => move({ productCondition: event.target.value, page: 1 })} aria-label="상품 상태"><option value="">상품 상태 전체</option>{Object.entries(PRODUCT_CONDITION_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select>
            <select value={tradeStatus} onChange={(event) => move({ tradeStatus: event.target.value, page: 1 })} aria-label="거래 상태"><option value="">거래 가능(판매 중, 예약 중)</option><option value="ALL">전체(판매 완료 포함)</option>{Object.entries(tradeLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select>
            <button type="button" className="market-filter-reset" onClick={() => { setSearchKeyword(""); router.push("/market"); }}>초기화</button>
            <div className="market-view-toggle" aria-label="보기 방식"><button type="button" className={viewMode === "list" ? "active" : ""} onClick={() => changeView("list")} aria-pressed={viewMode === "list"} aria-label="목록으로 보기"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">view_list</span></button><button type="button" className={viewMode === "album" ? "active" : ""} onClick={() => changeView("album")} aria-pressed={viewMode === "album"} aria-label="앨범으로 보기"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">grid_view</span></button></div>
          </div>
        </section>

        <section className={`market-results ${viewMode}`} aria-live="polite">
          {loading ? <div className="market-state"><span data-nosnippet className="material-symbols-outlined spin" aria-hidden="true">progress_activity</span><p>판매글을 불러오는 중입니다.</p></div>
            : rejectedMessage ? <div className="market-state"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">search_off</span><p>{rejectedMessage}</p></div>
            : error ? <div className="market-state"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">cloud_off</span><p>중고장터를 불러오지 못했습니다.</p><button onClick={() => void load()}>다시 시도</button></div>
            : items.length === 0 ? <div className="market-state"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">inventory_2</span><p>조건에 맞는 판매글이 없습니다.</p></div>
            : items.map((item) => <Link href={`/market/${item.publicId}`} key={item.publicId} className="market-result-card">
              <div className="market-result-image"><MarketImage item={item} /><em data-status={item.tradeStatus}>{tradeLabels[item.tradeStatus] ?? item.tradeStatus}</em></div>
              <div className="market-result-copy"><div><span>{item.category.name}</span><span>{PRODUCT_CONDITION_LABELS[item.productCondition]}</span></div><strong>{item.title}</strong><p><span>{item.seller.nickname}</span><span>{formatKoreanDate(item.createdAt)}</span><span>댓글 {item.commentCount}</span></p></div>
              <b>{item.free ? "무료 나눔" : `${item.price.toLocaleString()}원`}</b>
            </Link>)}
        </section>

        {!loading && !error && totalPages > 1 && <nav className="board-pagination market-pagination" aria-label="페이지 이동"><button disabled={page === 1} onClick={() => move({ page: page - 1 })} aria-label="이전 페이지"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">chevron_left</span></button>{visiblePages.map((number) => <button key={number} className={number === page ? "current" : ""} aria-current={number === page ? "page" : undefined} onClick={() => move({ page: number })}>{number}</button>)}<button disabled={page === totalPages} onClick={() => move({ page: page + 1 })} aria-label="다음 페이지"><span data-nosnippet className="material-symbols-outlined" aria-hidden="true">chevron_right</span></button></nav>}
      </main>
      <Footer />
    </div>
  );
}

export default function MarketPage() {
  return <Suspense fallback={<div className="market-state">중고장터를 준비하는 중입니다.</div>}><MarketPageContent /></Suspense>;
}
