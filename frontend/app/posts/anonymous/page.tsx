"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Footer, SideCategories, TopNav } from "../../components/SiteChrome";
import { API_ENDPOINTS } from "../../lib/api";

interface PostItem {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  hasImage: boolean;
  commentCount: number;
  likeCount: number;
  isDeleted: boolean;
  status: string;
  createdAt: string;
}

export default function AnonymousBoardPage() {
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const res = await fetch(`${API_ENDPOINTS.posts.list}?categoryCode=ANONYMOUS&page=1&size=15`, {
            credentials: "include",
          });
          if (res.ok) {
            const data = await res.json();
            setPosts(Array.isArray(data.content) ? data.content : []);
          }
        } catch (error) {
          console.error("익명 게시글 로드 실패:", error);
        } finally {
          setLoading(false);
        }
      })();
    }, 0);

    return () => window.clearTimeout(timer);
  }, []);

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

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="posts" />
      <div className="snow-container snow-grid-shell">
        <SideCategories active="anonymous" />
        <main className="px-5 py-8 lg:px-8 lg:py-10">
          <section className="mb-10 border-b-2 border-black pb-7">
            <h1 className="snow-heading-lg">Speak Freely.</h1>
            <p className="mt-4 max-w-3xl text-lg leading-8 text-[var(--snow-ink-soft)]">
              익명 게시판입니다. 닉네임과 프로필 없이 솔직한 질문과 의견을 남길 수 있습니다.
            </p>
          </section>

          <div className="mb-6 flex items-center justify-between gap-4 border-b border-[var(--snow-border)] pb-4">
            <div className="flex gap-5 font-mono text-xs font-bold uppercase tracking-[0.08em]">
              <button className="border-b-2 border-black pb-1 text-black">Newest</button>
              <button className="text-[var(--snow-muted)]">Top</button>
              <button className="text-[var(--snow-muted)]">Controversial</button>
            </div>
            <Link href="/posts/create?category=ANONYMOUS" className="snow-btn-primary">
              <span className="material-symbols-outlined text-[17px]">edit</span>
              Write Post
            </Link>
          </div>

          <section className="snow-card bg-white">
            <div className="divide-y divide-[var(--snow-border)]">
              {loading ? (
                <div className="p-12 text-center text-sm text-[var(--snow-muted)]">익명 게시글을 불러오는 중입니다.</div>
              ) : posts.length === 0 ? (
                <div className="p-12 text-center text-sm text-[var(--snow-muted)]">작성된 익명 게시글이 없습니다.</div>
              ) : (
                posts.map((post) => (
                  <Link
                    key={post.publicId}
                    href={`/posts/${post.publicId}`}
                    onClick={(event) => handlePostClick(event, post)}
                    className="block p-6 transition hover:bg-[var(--snow-surface-low)]"
                  >
                    <div className="mb-3 flex flex-wrap items-center gap-2 font-mono text-xs text-[var(--snow-muted)]">
                      <span className="snow-chip">익명 보더</span>
                      <span>{new Date(post.createdAt).toLocaleDateString()}</span>
                      {post.hasImage && <span className="snow-chip snow-chip-green">이미지</span>}
                    </div>
                    <h2 className={`text-2xl font-extrabold text-black ${post.isDeleted ? "text-[var(--snow-faint)] line-through" : ""}`}>
                      {post.isDeleted || post.status === "DELETED" ? "[삭제된 게시글입니다]" : post.title}
                    </h2>
                    <div className="mt-4 flex gap-5 font-mono text-xs text-[var(--snow-muted)]">
                      <span>댓글 {post.commentCount}</span>
                      <span>추천 {post.likeCount}</span>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </section>
        </main>
      </div>
      <Footer />
    </div>
  );
}
