"use client";

import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { PostImageIcon } from "../components/PostImageIcon";
import { PostListAuthor } from "../components/PostListAuthor";
import { API_ENDPOINTS } from "../lib/api";
import { fetchViewer, isAdminViewer } from "../lib/viewer";
import { formatCompactTime } from "../lib/koreanDate";
import {
  DEFAULT_SEARCH_TYPE,
  NOTICE_QUERY,
  SEARCH_TYPES,
  boardConfigs,
  postListApiQuery,
  readPostListParams,
  type InitialPostList,
  type PostItem,
} from "./postListQuery";

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

function PostListContent({ initial }: { initial: InitialPostList | null }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const {
    currentCategory,
    currentView,
    page,
    keyword: keywordFromUrl,
    searchType: searchTypeFromUrl,
  } = readPostListParams((key) => searchParams.get(key));
  const listQuery = useMemo(
    () => postListApiQuery({ currentCategory, currentView, page, keyword: keywordFromUrl, searchType: searchTypeFromUrl }),
    [currentCategory, currentView, page, keywordFromUrl, searchTypeFromUrl],
  );
  // 서버가 이 주소의 목록을 이미 받아 왔으면 브라우저는 다시 부르지 않는다.
  const serverList = initial?.query === listQuery ? initial : null;
  const config = boardConfigs[currentCategory];
  const isInquiry = currentCategory === "INQUIRY";

  const [posts, setPosts] = useState<PostItem[]>(serverList?.posts ?? []);
  const [notices, setNotices] = useState<PostItem[]>(initial?.notices ?? []);
  const [isAdmin, setIsAdmin] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState(keywordFromUrl);
  const [searchType, setSearchType] = useState(searchTypeFromUrl);
  const [totalPages, setTotalPages] = useState(serverList?.totalPages ?? 1);
  const [loading, setLoading] = useState(!serverList);
  // 실패 이유. 검색어 길이, 검색 횟수 제한처럼 사용자가 고칠 수 있는 경우는 서버 문구를 그대로 보여 준다.
  const [error, setError] = useState<string | null>(null);
  // 공지는 서버 값이 있으면 그대로 쓰고, 없을 때만 브라우저가 받는다.
  const serverNotices = initial?.notices ?? null;

  useEffect(() => {
    const controller = new AbortController();
    if (!serverNotices) {
      void fetch(`${API_ENDPOINTS.posts.list}?${NOTICE_QUERY}`, { signal: controller.signal })
        .then((response) => (response.ok ? response.json() : Promise.reject()))
        .then((data) => setNotices(Array.isArray(data.content) ? data.content : []))
        .catch(() => {
          if (!controller.signal.aborted) setNotices([]);
        });
    }
    void fetchViewer().then((viewer) => {
      if (!controller.signal.aborted) setIsAdmin(isAdminViewer(viewer));
    });
    return () => controller.abort();
  }, [serverNotices]);

  const fetchPosts = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch(`${API_ENDPOINTS.posts.list}?${listQuery}`, {
          credentials: "include",
          signal,
        });
        if (response.status === 401 && currentCategory === "INQUIRY") {
          router.replace(`/login?returnUrl=${encodeURIComponent("/posts?category=INQUIRY")}`);
          return;
        }
        if (response.status === 400 || response.status === 429) {
          const body = await response.json().catch(() => null);
          if (signal?.aborted) return;
          setPosts([]);
          setError(typeof body?.message === "string" ? body.message : "게시글을 불러오지 못했습니다.");
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
        setError("게시글을 불러오지 못했습니다.");
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [currentCategory, listQuery, router],
  );

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      // 게시판을 옮기면 서버가 새 주소의 목록을 다시 받아 props로 넘긴다. 그 값이 있으면 요청 없이 바꾼다.
      if (serverList) {
        setPosts(serverList.posts);
        setTotalPages(serverList.totalPages);
        setError(null);
        setLoading(false);
        return;
      }
      void fetchPosts(controller.signal);
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [fetchPosts, serverList]);

  const visiblePages = useMemo(() => {
    const start = Math.max(0, Math.min(page - 2, totalPages - 5));
    return Array.from({ length: Math.min(5, totalPages) }, (_, index) => start + index);
  }, [page, totalPages]);
  const lastVisiblePage = visiblePages[visiblePages.length - 1] ?? 0;

  const moveTo = (next: { category?: string; page?: number; keyword?: string; searchType?: string; view?: string }) => {
    const params = new URLSearchParams();
    const category = next.category ?? currentCategory;
    const nextPage = next.page ?? page;
    const keyword = next.keyword ?? keywordFromUrl;
    const view = next.view ?? currentView;
    if (category) params.set("category", category);
    if (view) params.set("view", view);
    if (nextPage > 0) params.set("page", String(nextPage + 1));
    if (keyword.trim()) {
      params.set("keyword", keyword.trim());
      const nextType = next.searchType ?? searchTypeFromUrl;
      if (nextType !== DEFAULT_SEARCH_TYPE) params.set("searchType", nextType);
    }
    const url = `/posts${params.size ? `?${params}` : ""}`;
    // 검색어가 걸린 이동은 Next 라우터가 프리패치된 주소로 되돌려 검색어를 잃는 경우가 있어 새로 불러온다.
    if (keyword.trim() || keywordFromUrl) window.location.assign(url);
    else router.push(url);
  };

  const keywordTooShort = Array.from(searchKeyword.trim()).length === 1;

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    // 서버도 한 글자 검색을 거절한다. 입력칸 아래 안내가 이미 떠 있으므로 요청을 보내지 않는다.
    if (keywordTooShort) return;
    moveTo({ page: 0, keyword: searchKeyword, searchType });
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
        <h1 className="sr-only">{config.name}</h1>
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
                          {notice.viewCount.toLocaleString("ko-KR")}
                        </span>
                      </span>
                      <time className="m-time" suppressHydrationWarning>{formatCompactTime(notice.createdAt)}</time>
                    </div>
                  </article>
                ))}
              </section>
            )}

            <section className="board-post-list" aria-live="polite">
              {loading ? (
                <div className="board-state">
                  <span className="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
                  <strong>게시글을 불러오는 중입니다.</strong>
                </div>
              ) : error ? (
                <div className="board-state error">
                  <span className="material-symbols-outlined" aria-hidden="true">cloud_off</span>
                  <strong>{error}</strong>
                  <button type="button" onClick={() => void fetchPosts()}>
                    다시 시도
                  </button>
                </div>
              ) : posts.length === 0 ? (
                <div className="board-state">
                  <span className="material-symbols-outlined" aria-hidden="true">edit_note</span>
                  <strong>
                    {keywordFromUrl
                      ? "검색 결과가 없습니다."
                      : isInquiry
                        ? isAdmin ? "들어온 문의가 없습니다." : "남긴 문의가 없습니다."
                        : "등록된 게시글이 없습니다."}
                  </strong>
                  {!keywordFromUrl && (currentCategory !== "NOTICE" || isAdmin) && (
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
                        <PostImageIcon hasImage={post.hasImage} />
                        <strong className="m-title">{title}</strong>
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
                          <span className="m-stat" title={`조회 ${post.viewCount.toLocaleString("ko-KR")}`}>
                            <span className="material-symbols-outlined" aria-hidden="true">
                              visibility
                            </span>
                            {post.viewCount.toLocaleString("ko-KR")}
                          </span>
                        </span>
                        <time className="m-time" suppressHydrationWarning>{formatCompactTime(post.createdAt)}</time>
                      </div>
                    </Link>
                  );
                })
              )}
            </section>

            <div className="board-navigation">
              <nav className="board-pagination" aria-label="페이지 이동">
                <button disabled={page === 0} onClick={() => moveTo({ page: page - 1 })} aria-label="이전 페이지">
                  <span className="material-symbols-outlined" aria-hidden="true">chevron_left</span>
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
                {/* 마지막 페이지가 이미 번호 목록에 있으면 다시 그리지 않는다. 바로 앞까지 보이면 말줄임표는 빼고 붙인다. */}
                {lastVisiblePage < totalPages - 1 && (
                  <>
                    {lastVisiblePage < totalPages - 2 && <span>…</span>}
                    <button onClick={() => moveTo({ page: totalPages - 1 })}>{totalPages}</button>
                  </>
                )}
                <button disabled={page + 1 >= totalPages} onClick={() => moveTo({ page: page + 1 })} aria-label="다음 페이지">
                  <span className="material-symbols-outlined" aria-hidden="true">chevron_right</span>
                </button>
              </nav>
              <div className="board-navigation-actions">
                <form className="board-search" onSubmit={handleSearch}>
                  <select aria-label="검색 범위" value={searchType} onChange={(event) => setSearchType(event.target.value)}>
                    {SEARCH_TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
                  </select>
                  <input
                    value={searchKeyword}
                    onChange={(event) => setSearchKeyword(event.target.value)}
                    placeholder={`${config.name} 내 검색`}
                    aria-label={`${config.name} 내 검색`}
                    aria-describedby={keywordTooShort ? "board-search-hint" : undefined}
                    maxLength={50}
                  />
                  <button disabled={keywordTooShort}>검색</button>
                  {keywordTooShort && <p id="board-search-hint" className="board-search-hint" role="status">한 글자로는 검색할 수 없어요.</p>}
                </form>
                {(currentCategory !== "NOTICE" || isAdmin) && (
                  <Link
                    href={`/posts/create${currentCategory ? `?category=${currentCategory}` : ""}`}
                    className="board-write-button"
                  >
                    <span className="material-symbols-outlined" aria-hidden="true">edit</span>
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

export default function PostListClient({ initial }: { initial: InitialPostList | null }) {
  return (
    <Suspense
      fallback={
        <div className="board-state">
          <strong>게시판을 준비하는 중입니다.</strong>
        </div>
      }
    >
      <PostListContent initial={initial} />
    </Suspense>
  );
}
