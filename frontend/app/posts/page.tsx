"use client";

import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav, type ActiveNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";

interface PostItem {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  status: string;
  isDeleted: boolean;
  createdAt: string;
}

interface BoardConfig {
  name: string;
  navKey: ActiveNav;
  notices: { label: string; title: string; date: string; views: string }[];
  topics: string[];
}

const categories = [
  { code: "", name: "전체글" },
  { code: "FREE", name: "자유게시판" },
  { code: "ANONYMOUS", name: "익명게시판" },
  { code: "QNA", name: "장비 후기" },
  { code: "FOOD", name: "리조트 맛집" },
];

const defaultNotices = [
  { label: "공지", title: "스노우띵 커뮤니티 이용 수칙 및 게시물 운영 정책 안내", date: "24.12.15", views: "1.2만" },
  { label: "필독", title: "안전한 라이딩을 위한 슬로프 에티켓과 제보 작성 가이드", date: "24.12.20", views: "8,490" },
];

const boardConfigs: Record<string, BoardConfig> = {
  "": { name: "전체 게시판", navKey: "posts", notices: defaultNotices, topics: ["휘팍 챔피언 설질", "야간 고글 추천", "시즌권 양도", "초보 데크 선택", "강원권 교통"] },
  FREE: { name: "자유게시판", navKey: "free", notices: defaultNotices, topics: ["첫 보딩 후기", "시즌방 생활", "주말 원정", "라이딩 영상", "보드복 추천"] },
  ANONYMOUS: { name: "익명게시판", navKey: "anonymous", notices: [
    { label: "공지", title: "익명게시판 내 특정인 저격, 허위 사실 유포 및 연락처 공유 시 제재 안내", date: "24.12.15", views: "1.2만" },
    { label: "필독", title: "익명성은 타인을 공격할 권리가 아닙니다. 익명게시판 이용 가이드", date: "24.12.20", views: "8,490" },
  ], topics: ["셔틀버스 매너", "휘팍 실시간 파우더", "시즌방 비용 정산", "데크 구매 고민", "복귀길 정체"] },
  QNA: { name: "장비 후기", navKey: "gear", notices: defaultNotices, topics: ["부츠 열성형", "바인딩 각도", "엣지 튜닝", "입문 데크", "카빙 자세"] },
  FOOD: { name: "리조트 맛집", navKey: "food", notices: defaultNotices, topics: ["용평 아침식사", "휘팍 국밥", "하이원 야식", "비발디 카페", "웰리힐리 맛집"] },
};

const carpools = [
  { from: "서울 사당", to: "하이원", date: "12/28(토) 05:00 출발", seat: "2석 남음", price: "기름/톨비 N빵" },
  { from: "경기 분당(서현)", to: "휘닉스파크", date: "12/28(토) 18:00 야간", seat: "1석 남음", price: "편도 1.5만원" },
];

function PostListContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const categoryCode = searchParams.get("category")?.toUpperCase() ?? "";
  const currentCategory = boardConfigs[categoryCode] ? categoryCode : "";
  const currentView = searchParams.get("view")?.toLowerCase() === "best" ? "best" : "";
  const page = Math.max(0, Number(searchParams.get("page") ?? "1") - 1 || 0);
  const keywordFromUrl = searchParams.get("keyword") ?? "";
  const config = boardConfigs[currentCategory];

  const [posts, setPosts] = useState<PostItem[]>([]);
  const [searchKeyword, setSearchKeyword] = useState(keywordFromUrl);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const params = new URLSearchParams({ page: String(page + 1), size: "10" });
      if (currentCategory) params.set("categoryCode", currentCategory);
      if (currentView === "best") params.set("viewType", "BEST");
      if (keywordFromUrl.trim()) params.set("keyword", keywordFromUrl.trim());
      const response = await fetch(`${API_ENDPOINTS.posts.list}?${params}`, { credentials: "include" });
      if (!response.ok) throw new Error(`게시글 목록 응답 오류: ${response.status}`);
      const data = await response.json();
      setPosts(Array.isArray(data.content) ? data.content : []);
      setTotalPages(Math.max(1, data.pageInfo?.totalPages || 1));
    } catch (fetchError) {
      console.error("게시글 목록 로드 실패:", fetchError);
      setPosts([]);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [currentCategory, currentView, keywordFromUrl, page]);

  useEffect(() => {
    const timer = window.setTimeout(() => void fetchPosts(), 0);
    return () => window.clearTimeout(timer);
  }, [fetchPosts]);

  const visiblePages = useMemo(() => {
    const start = Math.max(0, Math.min(page - 2, totalPages - 5));
    return Array.from({ length: Math.min(5, totalPages) }, (_, index) => start + index);
  }, [page, totalPages]);

  const moveTo = (next: { category?: string; page?: number; keyword?: string; view?: string }) => {
    const params = new URLSearchParams();
    const category = next.category ?? currentCategory;
    const nextPage = next.page ?? page;
    const keyword = next.keyword ?? keywordFromUrl;
    const view = next.view ?? currentView;
    if (category) params.set("category", category);
    if (view) params.set("view", view);
    if (nextPage > 0) params.set("page", String(nextPage + 1));
    if (keyword.trim()) params.set("keyword", keyword.trim());
    router.push(`/posts${params.size ? `?${params}` : ""}`);
  };

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    moveTo({ page: 0, keyword: searchKeyword });
  };

  const handlePostClick = (event: React.MouseEvent, post: PostItem) => {
    if (post.isDeleted || post.status === "DELETED") {
      event.preventDefault();
      alert("삭제된 게시글입니다.");
    } else if (post.status === "BLOCKED") {
      event.preventDefault();
      alert("관리자에 의해 차단된 게시글입니다.");
    }
  };

  return (
    <div className="community-page">
      <TopNav active={currentView === "best" ? "best" : config.navKey} />
      <main className="community-container board-page">
        <div className="board-layout">
          <section className="board-primary">
            <nav className="board-filter-panel" aria-label="게시판 선택">
              <div className="board-category-tabs">{categories.map((category) => <button key={category.code} type="button" className={currentCategory === category.code && !currentView ? "active" : ""} onClick={() => moveTo({ category: category.code, page: 0, keyword: "", view: "" })}>{category.name}</button>)}</div>
            </nav>

            <section className="board-notices" aria-label="게시판 공지">
              {config.notices.map((notice) => <article key={notice.title}><div><span>{notice.label}</span><strong>{notice.title}</strong></div><p><b>관리자</b><time>{notice.date}</time><span>조회 {notice.views}</span></p></article>)}
            </section>

            <section className="board-post-list" aria-live="polite">
              {loading ? <div className="board-state"><span className="material-symbols-outlined spin">progress_activity</span><strong>게시글을 불러오는 중입니다.</strong></div>
                : error ? <div className="board-state error"><span className="material-symbols-outlined">cloud_off</span><strong>게시글을 불러오지 못했습니다.</strong><button type="button" onClick={() => void fetchPosts()}>다시 시도</button></div>
                : posts.length === 0 ? <div className="board-state"><span className="material-symbols-outlined">edit_note</span><strong>등록된 게시글이 없습니다.</strong><Link href={`/posts/create${currentCategory ? `?category=${currentCategory}` : ""}`}>첫 글 작성하기</Link></div>
                : posts.map((post) => {
                  const unavailable = post.isDeleted || post.status === "DELETED" || post.status === "BLOCKED";
                  const title = post.status === "BLOCKED" ? "[차단된 게시글입니다]" : post.isDeleted || post.status === "DELETED" ? "[삭제된 게시글입니다]" : post.title;
                  return <Link key={post.publicId} href={`/posts/${post.publicId}`} onClick={(event) => handlePostClick(event, post)} className={`board-post-row ${unavailable ? "unavailable" : ""}`}>
                    <div className="board-post-copy"><div className="board-post-title">{!currentCategory && <span>{post.categoryCode === "QNA" ? "장비 후기" : post.categoryName}</span>}{post.likeCount >= 50 && <em>HOT</em>}<strong>{title}</strong>{post.commentCount > 0 && <b>[{post.commentCount}]</b>}{post.hasImage && <span className="material-symbols-outlined image-mark">image</span>}</div><p><b>{post.writerNickname}</b><span>•</span><time>{new Date(post.createdAt).toLocaleDateString("ko-KR")}</time><span>•</span><span>조회 {post.viewCount.toLocaleString()}</span></p></div>
                    {post.thumbnailImageUrl && <Image src={post.thumbnailImageUrl} alt="" width={58} height={52} unoptimized />}
                  </Link>;
                })}
            </section>

            <div className="board-navigation">
              <nav className="board-pagination" aria-label="페이지 이동"><button disabled={page === 0} onClick={() => moveTo({ page: page - 1 })}><span className="material-symbols-outlined">chevron_left</span></button>{visiblePages.map((pageNumber) => <button key={pageNumber} className={page === pageNumber ? "current" : ""} onClick={() => moveTo({ page: pageNumber })}>{pageNumber + 1}</button>)}{totalPages > 6 && <><span>…</span><button onClick={() => moveTo({ page: totalPages - 1 })}>{totalPages}</button></>}<button disabled={page + 1 >= totalPages} onClick={() => moveTo({ page: page + 1 })}><span className="material-symbols-outlined">chevron_right</span></button></nav>
              <div className="board-navigation-actions">
                <form className="board-search" onSubmit={handleSearch}><select aria-label="검색 범위"><option>제목+내용</option></select><input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} placeholder={`${config.name} 내 검색`} /><button>검색</button></form>
                <Link href={`/posts/create${currentCategory ? `?category=${currentCategory}` : ""}`} className="board-write-button"><span className="material-symbols-outlined">edit</span>글쓰기</Link>
              </div>
            </div>
          </section>

          <aside className="board-sidebar">
            <section className="board-widget topic-widget"><header><h2><span className="material-symbols-outlined">trending_up</span>{config.name} 실시간 핫 토픽</h2><small>10분 주기 갱신</small></header><ol>{config.topics.map((topic, index) => <li key={topic}><b>{index + 1}</b><Link href={`/posts${currentCategory ? `?category=${currentCategory}` : ""}`}>{topic}</Link><span>{index === 3 ? "NEW" : index < 2 ? `▲ ${42 - index * 24}` : "-"}</span></li>)}</ol></section>
            <section className="board-widget board-carpool" id="carpool"><header><h2><em>실시간</em> 급구! 카풀 &amp; 동행</h2><Link href="/#carpool">+ 등록</Link></header><div>{carpools.map((item) => <article key={item.from}><div><strong>{item.from} <span>→</span> <em>{item.to}</em></strong><b>{item.seat}</b></div><p><span>{item.date}</span><strong>{item.price}</strong></p></article>)}</div></section>
          </aside>
        </div>
      </main>
      <Footer />
    </div>
  );
}

export default function PostListPage() {
  return <Suspense fallback={<div className="board-state"><strong>게시판을 준비하는 중입니다.</strong></div>}><PostListContent /></Suspense>;
}
