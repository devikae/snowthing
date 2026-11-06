"use client";

import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav, type ActiveNav } from "../components/SiteChrome";
import { PostImageIcon } from "../components/PostImageIcon";
import { PostListAuthor } from "../components/PostListAuthor";
import { API_ENDPOINTS } from "../lib/api";

interface PostItem {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  writerProfileImageUrl?: string | null;
  writerPublicId?: string | null;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  status: string;
  isDeleted: boolean;
  createdAt: string;
  answered?: boolean | null;
}

interface BoardConfig {
  name: string;
  navKey: ActiveNav;
}

const categories = [
  { code: "", name: "전체글" },
  { code: "NOTICE", name: "공지사항" },
  { code: "FREE", name: "자유게시판" },
  { code: "ANONYMOUS", name: "익명게시판" },
  { code: "QNA", name: "질문게시판" },
  { code: "FOOD", name: "리조트 맛집" },
  { code: "CREW", name: "동호회 모집" },
  { code: "SEASON_ROOM", name: "시즌방 모집" },
];

const boardConfigs: Record<string, BoardConfig> = {
  "": { name: "전체 게시판", navKey: "posts" },
  NOTICE: { name: "공지사항", navKey: "notice" },
  FREE: { name: "자유게시판", navKey: "free" },
  ANONYMOUS: { name: "익명게시판", navKey: "anonymous" },
  QNA: { name: "질문게시판", navKey: "gear" },
  FOOD: { name: "리조트 맛집", navKey: "food" },
  CREW: { name: "동호회 모집", navKey: "crew" },
  SEASON_ROOM: { name: "시즌방 모집", navKey: "seasonRoom" },
  INQUIRY: { name: "1:1 문의", navKey: "inquiry" },
};

function formatCompactTime(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const diffSec = Math.floor((now.getTime() - date.getTime()) / 1000);
  if (diffSec < 60) return "방금";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay === 1) return "어제";
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${month}.${day}`;
}

function formatShortCategory(categoryCode: string, categoryName: string): string {
  switch (categoryCode) {
    case "NOTICE":
      return "공지사항";
    case "FREE":
      return "자유";
    case "ANONYMOUS":
      return "익명";
    case "QNA":
      return "질문";
    case "FOOD":
      return "맛집";
    case "CREW":
      return "동호회";
    case "SEASON_ROOM":
      return "시즌방";
    default:
      return categoryName.replace(/게시판|\s+/g, "");
  }
}

function PostListContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const categoryCode = searchParams.get("category")?.toUpperCase() ?? "";
  const currentCategory = boardConfigs[categoryCode] ? categoryCode : "";
  const currentView = searchParams.get("view")?.toLowerCase() === "best" ? "best" : "";
  const page = Math.max(0, Number(searchParams.get("page") ?? "1") - 1 || 0);
  const keywordFromUrl = searchParams.get("keyword") ?? "";
  const config = boardConfigs[currentCategory];
  const isInquiry = currentCategory === "INQUIRY";

  const [posts, setPosts] = useState<PostItem[]>([]);
  const [notices, setNotices] = useState<PostItem[]>([]);
  const [isAdmin, setIsAdmin] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState(keywordFromUrl);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void fetch(`${API_ENDPOINTS.posts.list}?categoryCode=NOTICE&page=1&size=3`, { signal: controller.signal })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data) => setNotices(Array.isArray(data.content) ? data.content : []))
      .catch(() => {
        if (!controller.signal.aborted) setNotices([]);
      });
    void fetch(API_ENDPOINTS.members.me, { credentials: "include", signal: controller.signal })
      .then((response) => (response.ok ? response.json() : null))
      .then((member) => setIsAdmin(member?.role === "ROLE_ADMIN"))
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const fetchPosts = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError(false);
      try {
        const params = new URLSearchParams({ page: String(page + 1), size: "20" });
        if (currentCategory) params.set("categoryCode", currentCategory);
        if (currentView === "best") params.set("viewType", "BEST");
        if (keywordFromUrl.trim()) params.set("keyword", keywordFromUrl.trim());
        const response = await fetch(`${API_ENDPOINTS.posts.list}?${params}`, {
          credentials: "include",
          signal,
        });
        if (response.status === 401 && currentCategory === "INQUIRY") {
          router.replace(`/login?returnUrl=${encodeURIComponent("/posts?category=INQUIRY")}`);
          return;
        }
        if (!response.ok) throw new Error(`게시글 목록 응답 오류: ${response.status}`);
        const data = await response.json();
        if (signal?.aborted) return;
        setPosts(Array.isArray(data.content) ? data.content : []);
        setTotalPages(Math.max(1, data.pageInfo?.totalPages || 1));
      } catch (fetchError) {
        if (fetchError instanceof DOMException && fetchError.name === "AbortError") return;
        console.error("게시글 목록 로드 실패:", fetchError);
        setPosts([]);
        setError(true);
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [currentCategory, currentView, keywordFromUrl, page, router],
  );

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => void fetchPosts(controller.signal), 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
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
            {isInquiry ? (
              <section className="board-filter-panel board-inquiry-guide" aria-label="1:1 문의 안내">
                <strong>{isAdmin ? "전체 문의" : "1:1 문의"}</strong>
                <p>{isAdmin ? "회원이 남긴 모든 문의입니다. 댓글로 답변해 주세요." : "내가 남긴 문의만 보입니다. 답변은 운영자가 댓글로 남깁니다."}</p>
              </section>
            ) : (
            <nav className="board-filter-panel" aria-label="게시판 선택">
              <div className="board-category-tabs filter-pills">
                {categories.map((category) => (
                  <button
                    key={category.code}
                    type="button"
                    className={currentCategory === category.code && !currentView ? "active" : ""}
                    onClick={() => moveTo({ category: category.code, page: 0, keyword: "", view: "" })}
                  >
                    {category.name}
                  </button>
                ))}
              </div>
            </nav>
            )}

            {currentCategory !== "NOTICE" && !isInquiry && notices.length > 0 && (
              <section className="board-notices" aria-label="게시판 공지">
                {notices.map((notice) => (
                  <article key={notice.publicId} className="m-row">
                    <div className="m-row-left">
                      <span className="m-cat notice">공지사항</span>
                      <Link href={`/posts/${notice.publicId}`} className="m-title-link">
                        <strong className="m-title">{notice.title}</strong>
                      </Link>
                    </div>
                    <div className="m-row-right">
                      <span className="m-author">
                        <b>{notice.writerNickname}</b>
                      </span>
                      <span className="m-stats">
                        <span className="m-stat">
                          <span className="material-symbols-outlined" aria-hidden="true">
                            visibility
                          </span>
                          {notice.viewCount.toLocaleString()}
                        </span>
                      </span>
                      <time className="m-time">{formatCompactTime(notice.createdAt)}</time>
                    </div>
                  </article>
                ))}
              </section>
            )}

            <section className="board-post-list" aria-live="polite">
              {loading ? (
                <div className="board-state">
                  <span className="material-symbols-outlined spin">progress_activity</span>
                  <strong>게시글을 불러오는 중입니다.</strong>
                </div>
              ) : error ? (
                <div className="board-state error">
                  <span className="material-symbols-outlined">cloud_off</span>
                  <strong>게시글을 불러오지 못했습니다.</strong>
                  <button type="button" onClick={() => void fetchPosts()}>
                    다시 시도
                  </button>
                </div>
              ) : posts.length === 0 ? (
                <div className="board-state">
                  <span className="material-symbols-outlined">edit_note</span>
                  <strong>
                    {isInquiry
                      ? isAdmin ? "들어온 문의가 없습니다." : "남긴 문의가 없습니다."
                      : "등록된 게시글이 없습니다."}
                  </strong>
                  {(currentCategory !== "NOTICE" || isAdmin) && (
                    <Link href={`/posts/create${currentCategory ? `?category=${currentCategory}` : ""}`}>
                      {isInquiry ? "문의하기" : "첫 글 작성하기"}
                    </Link>
                  )}
                </div>
              ) : (
                posts.map((post) => {
                  const unavailable = post.isDeleted || post.status === "DELETED" || post.status === "BLOCKED";
                  const title =
                    post.status === "BLOCKED"
                      ? "[차단된 게시글입니다]"
                      : post.isDeleted || post.status === "DELETED"
                        ? "[삭제된 게시글입니다]"
                        : post.title;
                  return (
                    <Link
                      key={post.publicId}
                      href={post.categoryCode === "CARPOOL" ? `/carpool/${post.publicId}` : `/posts/${post.publicId}`}
                      onClick={(event) => handlePostClick(event, post)}
                      className={`board-post-row m-row ${unavailable ? "unavailable" : ""}`}
                    >
                      <div className="m-row-left">
                        {!currentCategory && (
                          <span className={`m-cat ${post.categoryCode === "NOTICE" ? "notice" : post.categoryCode === "QNA" ? "accent" : ""}`}>
                            {formatShortCategory(post.categoryCode, post.categoryName)}
                          </span>
                        )}
                        {post.answered != null && (
                          <span className={`m-cat ${post.answered ? "accent" : ""}`}>
                            {post.answered ? "답변 완료" : "답변 대기"}
                          </span>
                        )}
                        {post.likeCount >= 50 && <em className="board-hot-badge">HOT</em>}
                        <strong className="m-title">{title}</strong>
                        {post.hasImage && <PostImageIcon />}
                      </div>
                      <div className="m-row-right">
                        <PostListAuthor nickname={post.writerNickname} profileImageUrl={post.writerProfileImageUrl} publicId={post.writerPublicId} />
                        <span className="m-stats">
                          {post.commentCount > 0 && (
                            <span className="m-stat hot" title={`댓글 ${post.commentCount}`}>
                              <span className="material-symbols-outlined" aria-hidden="true">
                                chat_bubble
                              </span>
                              {post.commentCount}
                            </span>
                          )}
                          <span className="m-stat" title={`조회 ${post.viewCount.toLocaleString()}`}>
                            <span className="material-symbols-outlined" aria-hidden="true">
                              visibility
                            </span>
                            {post.viewCount.toLocaleString()}
                          </span>
                        </span>
                        <time className="m-time">{formatCompactTime(post.createdAt)}</time>
                      </div>
                    </Link>
                  );
                })
              )}
            </section>

            <div className="board-navigation">
              <nav className="board-pagination" aria-label="페이지 이동">
                <button disabled={page === 0} onClick={() => moveTo({ page: page - 1 })}>
                  <span className="material-symbols-outlined">chevron_left</span>
                </button>
                {visiblePages.map((pageNumber) => (
                  <button
                    key={pageNumber}
                    className={page === pageNumber ? "current" : ""}
                    onClick={() => moveTo({ page: pageNumber })}
                  >
                    {pageNumber + 1}
                  </button>
                ))}
                {totalPages > 6 && (
                  <>
                    <span>…</span>
                    <button onClick={() => moveTo({ page: totalPages - 1 })}>{totalPages}</button>
                  </>
                )}
                <button disabled={page + 1 >= totalPages} onClick={() => moveTo({ page: page + 1 })}>
                  <span className="material-symbols-outlined">chevron_right</span>
                </button>
              </nav>
              <div className="board-navigation-actions">
                <form className="board-search" onSubmit={handleSearch}>
                  <select aria-label="검색 범위">
                    <option>제목+내용</option>
                  </select>
                  <input
                    value={searchKeyword}
                    onChange={(event) => setSearchKeyword(event.target.value)}
                    placeholder={`${config.name} 내 검색`}
                  />
                  <button>검색</button>
                </form>
                {(currentCategory !== "NOTICE" || isAdmin) && (
                  <Link
                    href={`/posts/create${currentCategory ? `?category=${currentCategory}` : ""}`}
                    className="board-write-button"
                  >
                    <span className="material-symbols-outlined">edit</span>
                    {isInquiry ? "문의하기" : "글쓰기"}
                  </Link>
                )}
              </div>
            </div>
          </section>

        </div>
      </main>
      <Footer />
    </div>
  );
}

export default function PostListPage() {
  return (
    <Suspense
      fallback={
        <div className="board-state">
          <strong>게시판을 준비하는 중입니다.</strong>
        </div>
      }
    >
      <PostListContent />
    </Suspense>
  );
}
