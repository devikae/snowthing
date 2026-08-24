"use client";

import { useCallback, useEffect, useMemo, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { Footer, SideCategories, TopNav } from "../components/SiteChrome";

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

const CATEGORIES = [
  { code: "", name: "전체 게시판", key: "all" },
  { code: "FREE", name: "자유게시판", key: "free" },
  { code: "ANONYMOUS", name: "익명 게시판", key: "anonymous" },
  { code: "QNA", name: "장비 Q&A", key: "qna" },
  { code: "FOOD", name: "리조트 맛집", key: "food" },
];

const featured = [
  {
    label: "Announcement",
    title: "시즌권 공동구매와 양도 거래 주의사항",
    body: "거래 게시글은 연락처 노출을 최소화하고, 현장 확인 전 선입금을 피해주세요.",
    dark: false,
  },
  {
    label: "Event",
    title: "이번 주말 베스트 라이딩 클립 공유",
    body: "짧은 영상 링크와 촬영 리조트를 함께 남기면 메인 피드에 소개됩니다.",
    dark: true,
  },
];

function PostListContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const categoryFromUrl = searchParams.get("category") || "";

  const [posts, setPosts] = useState<PostItem[]>([]);
  const [selectedCategory, setSelectedCategory] = useState(categoryFromUrl);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);

  // URL Query Parameter ?category= 변경 감지 및 자동 동기화
  useEffect(() => {
    setSelectedCategory(categoryFromUrl);
    setPage(0);
  }, [categoryFromUrl]);

  const activeCategoryKey = useMemo(
    () => CATEGORIES.find((category) => category.code === selectedCategory)?.key ?? "all",
    [selectedCategory],
  );

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    try {
      let url = `http://localhost:8080/api/v1/posts?page=${page + 1}&size=10`;
      if (selectedCategory) {
        url += `&categoryCode=${selectedCategory}`;
      }
      if (searchKeyword.trim()) {
        url += `&keyword=${encodeURIComponent(searchKeyword.trim())}`;
      }

      const res = await fetch(url, { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setPosts(Array.isArray(data.content) ? data.content : []);
        setTotalPages(data.pageInfo?.totalPages || 1);
      }
    } catch (error) {
      console.error("게시글 목록 로드 실패:", error);
    } finally {
      setLoading(false);
    }
  }, [page, searchKeyword, selectedCategory]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchPosts();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchPosts]);

  const handlePostClick = (event: React.MouseEvent, post: PostItem) => {
    if (post.isDeleted || post.status === "DELETED") {
      event.preventDefault();
      alert("삭제된 게시글입니다.");
      return;
    }

    if (post.status === "BLOCKED") {
      event.preventDefault();
      alert("관리자에 의해 차단된 게시글입니다.");
    }
  };

  const handleSearch = () => {
    setPage(0);
    void fetchPosts();
  };

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="posts" />
      <div className="snow-container snow-grid-shell">
        <SideCategories active={activeCategoryKey} />
        <main className="px-5 py-8 lg:px-8 lg:py-10">
          <header className="mb-8 border-b-2 border-black pb-5">
            <div className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
              <div>
                <h1 className="snow-heading-lg uppercase">Free Board</h1>
                <p className="mt-3 text-lg text-[var(--snow-muted)]">자유 게시판과 익명 게시판을 한곳에서 확인합니다.</p>
              </div>
              <Link href="/posts/create" className="snow-btn-primary">
                <span className="material-symbols-outlined text-[17px]">edit</span>
                Write
              </Link>
            </div>
          </header>

          <section className="mb-10 grid gap-6 md:grid-cols-2">
            {featured.map((item) => (
              <article
                key={item.title}
                className={`snow-card min-h-[190px] p-6 ${item.dark ? "bg-black text-white" : "bg-white text-black"}`}
              >
                <div className="flex h-full flex-col justify-between gap-8">
                  <div className="flex items-start justify-between gap-4">
                    <span className={`snow-chip ${item.dark ? "bg-white text-black" : "snow-chip-dark"}`}>{item.label}</span>
                    <span className={`font-mono text-xs ${item.dark ? "text-white/70" : "text-[var(--snow-muted)]"}`}>Pinned</span>
                  </div>
                  <div>
                    <h2 className="text-2xl font-extrabold leading-tight">{item.title}</h2>
                    <p className={`mt-3 leading-7 ${item.dark ? "text-white/80" : "text-[var(--snow-ink-soft)]"}`}>{item.body}</p>
                  </div>
                </div>
              </article>
            ))}
          </section>

          <section className="snow-card bg-white">
            <div className="flex flex-col gap-4 border-b border-[var(--snow-border)] p-5 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex flex-wrap gap-4">
                {CATEGORIES.map((category) => (
                  <button
                    key={category.code}
                    onClick={() => {
                      setSelectedCategory(category.code);
                      setPage(0);
                    }}
                    className={`font-mono text-xs font-bold uppercase tracking-[0.08em] ${
                      selectedCategory === category.code
                        ? "border-b-2 border-black pb-1 text-black"
                        : "text-[var(--snow-muted)] hover:text-black"
                    }`}
                  >
                    {category.name}
                  </button>
                ))}
              </div>

              <div className="flex w-full gap-2 sm:w-auto">
                <input
                  type="text"
                  placeholder="제목 또는 본문 검색"
                  value={searchKeyword}
                  onChange={(event) => setSearchKeyword(event.target.value)}
                  onKeyDown={(event) => event.key === "Enter" && handleSearch()}
                  className="snow-input min-w-0 sm:w-64"
                />
                <button onClick={handleSearch} className="snow-btn-secondary shrink-0">
                  Search
                </button>
              </div>
            </div>

            <div className="divide-y divide-[var(--snow-border)]">
              {loading ? (
                <div className="p-12 text-center text-sm text-[var(--snow-muted)]">게시글 목록을 불러오는 중입니다.</div>
              ) : posts.length === 0 ? (
                <div className="p-12 text-center text-sm text-[var(--snow-muted)]">등록된 게시글이 없습니다. 첫 글을 작성해보세요.</div>
              ) : (
                posts.map((post) => {
                  const isUnavailable = post.isDeleted || post.status === "DELETED" || post.status === "BLOCKED";
                  const title =
                    post.status === "BLOCKED"
                      ? "[차단된 게시글입니다]"
                      : post.isDeleted || post.status === "DELETED"
                        ? "[삭제된 게시글입니다]"
                        : post.title;

                  return (
                    <Link
                      key={post.publicId}
                      href={`/posts/${post.publicId}`}
                      onClick={(event) => handlePostClick(event, post)}
                      className="grid gap-4 p-5 transition hover:bg-[var(--snow-surface-low)] md:grid-cols-[minmax(0,1fr)_130px]"
                    >
                      <div className="min-w-0">
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <span className="snow-chip">{post.categoryName}</span>
                          {post.hasImage && (
                            <span className="snow-chip snow-chip-green">
                              <span className="material-symbols-outlined text-[14px]">image</span>
                              이미지
                            </span>
                          )}
                          {post.commentCount > 0 && (
                            <span className="snow-chip">
                              <span className="material-symbols-outlined text-[14px]">chat_bubble</span>
                              {post.commentCount}
                            </span>
                          )}
                        </div>
                        <h2 className={`truncate text-xl font-extrabold text-black ${isUnavailable ? "text-[var(--snow-faint)] line-through" : ""}`}>
                          {title}
                        </h2>
                        <div className="mt-3 flex flex-wrap items-center gap-3 font-mono text-xs text-[var(--snow-muted)]">
                          <span className="font-bold text-black">{post.writerNickname}</span>
                          <span>{new Date(post.createdAt).toLocaleDateString()}</span>
                          <span>조회 {post.viewCount}</span>
                          <span>추천 {post.likeCount}</span>
                        </div>
                      </div>
                      <div className="hidden items-center justify-end md:flex">
                        {post.thumbnailImageUrl ? (
                          <img
                            src={post.thumbnailImageUrl}
                            alt=""
                            className="h-20 w-28 rounded border border-[var(--snow-border)] object-cover grayscale"
                          />
                        ) : (
                          <div className="flex h-20 w-28 items-center justify-center rounded border border-[var(--snow-border)] bg-[var(--snow-background)]">
                            <span className="material-symbols-outlined text-[24px] text-[var(--snow-faint)]">article</span>
                          </div>
                        )}
                      </div>
                    </Link>
                  );
                })
              )}
            </div>
          </section>

          <div className="mt-8 flex items-center justify-center gap-3 font-mono text-sm">
            <button
              disabled={page === 0}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              className="snow-btn-secondary min-h-10 px-3"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <span className="px-3 font-bold">
              PAGE {page + 1} / {totalPages}
            </span>
            <button
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((current) => current + 1)}
              className="snow-btn-secondary min-h-10 px-3"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>
        </main>
      </div>
      <Footer />
    </div>
  );
}

export default function PostListPage() {
  return (
    <Suspense fallback={<div className="p-12 text-center text-sm text-[var(--snow-muted)]">로딩 중입니다...</div>}>
      <PostListContent />
    </Suspense>
  );
}
