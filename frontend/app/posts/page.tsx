"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

interface PostItem {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  thumbnailImageUrl: string | null;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  status: string;
  createdAt: string;
}

const CATEGORIES = [
  { code: "", name: "전체게시판" },
  { code: "FREE", name: "자유게시판" },
  { code: "ANONYMOUS", name: "익명게시판" },
  { code: "QNA", name: "질문게시판" },
  { code: "FOOD", name: "맛집게시판" },
];

export default function PostListPage() {
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [selectedCategory, setSelectedCategory] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);

  const fetchPosts = async () => {
    setLoading(true);
    try {
      let url = `http://localhost:8080/api/posts?page=${page}&size=10`;
      if (selectedCategory) {
        url += `&categoryCode=${selectedCategory}`;
      }
      const res = await fetch(url, { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setPosts(data.content || []);
        setTotalPages(data.totalPages || 1);
      }
    } catch (err) {
      console.error("게시글 목록 로드 실패:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPosts();
  }, [selectedCategory, page]);

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "var(--bg-dark)", color: "var(--text-main)" }}>
      {/* Header Navigation */}
      <header style={{ borderBottom: "1px solid var(--border-dark)", background: "var(--bg-dark-soft)", padding: "1rem 2rem" }}>
        <div className="container" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Link href="/" style={{ fontSize: "1.5rem", fontWeight: "bold", color: "var(--primary)", textDecoration: "none" }}>
            🏂 Snowthing Board
          </Link>
          <div style={{ display: "flex", gap: "1rem" }}>
            <Link href="/" style={{ color: "var(--text-sub)", textDecoration: "none" }}>마이페이지</Link>
            <Link href="/posts" style={{ color: "var(--primary)", fontWeight: "bold", textDecoration: "none" }}>게시판</Link>
            <Link href="/login" style={{ color: "var(--text-sub)", textDecoration: "none" }}>로그인</Link>
          </div>
        </div>
      </header>

      <main className="container" style={{ paddingTop: "2rem", paddingBottom: "4rem" }}>
        {/* Top Header & Write Button */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
          <h1 style={{ fontSize: "1.8rem", fontWeight: "bold" }}>🏂 스노우보드 커뮤니티</h1>
          <Link
            href={`/posts/create${selectedCategory ? `?category=${selectedCategory}` : ""}`}
            className="btn-primary-green"
            style={{ textDecoration: "none", display: "inline-flex", alignItems: "center", gap: "0.5rem", padding: "0.65rem 1.25rem", borderRadius: "8px", fontWeight: 600, boxShadow: "0 4px 12px rgba(62, 207, 142, 0.2)" }}
          >
            ✏️ 글쓰기
          </Link>
        </div>

        {/* Category Tabs */}
        <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1.5rem", flexWrap: "wrap" }}>
          {CATEGORIES.map((cat) => (
            <button
              key={cat.code}
              onClick={() => {
                setSelectedCategory(cat.code);
                setPage(0);
              }}
              style={{
                padding: "0.5rem 1rem",
                borderRadius: "20px",
                border: "1px solid",
                borderColor: selectedCategory === cat.code ? "var(--primary)" : "var(--border-dark)",
                backgroundColor: selectedCategory === cat.code ? "var(--primary-deep)" : "var(--bg-dark-card)",
                color: selectedCategory === cat.code ? "#ffffff" : "var(--text-sub)",
                cursor: "pointer",
                fontWeight: 500,
                fontSize: "0.9rem",
              }}
            >
              {cat.name}
            </button>
          ))}
        </div>

        {/* Post Table/List */}
        <div className="card-supabase" style={{ padding: 0, overflow: "hidden" }}>
          {loading ? (
            <div style={{ padding: "3rem", textAlign: "center", color: "var(--text-muted)" }}>게시글 로딩 중...</div>
          ) : posts.length === 0 ? (
            <div style={{ padding: "3rem", textAlign: "center", color: "var(--text-muted)" }}>등록된 게시글이 없습니다.</div>
          ) : (
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left" }}>
              <thead>
                <tr style={{ background: "var(--bg-dark-card)", borderBottom: "1px solid var(--border-dark)", color: "var(--text-sub)", fontSize: "0.85rem" }}>
                  <th style={{ padding: "1rem" }}>카테고리</th>
                  <th style={{ padding: "1rem" }}>제목</th>
                  <th style={{ padding: "1rem" }}>작성자</th>
                  <th style={{ padding: "1rem", textAlign: "center" }}>조회 / 반응</th>
                  <th style={{ padding: "1rem", textAlign: "right" }}>작성일</th>
                </tr>
              </thead>
              <tbody>
                {posts.map((post) => (
                  <tr key={post.publicId} style={{ borderBottom: "1px solid var(--border-dark)" }}>
                    <td style={{ padding: "1rem", fontSize: "0.85rem", color: "var(--primary)" }}>
                      [{post.categoryName}]
                    </td>
                    <td style={{ padding: "1rem" }}>
                      <Link href={`/posts/${post.publicId}`} style={{ color: "var(--text-main)", textDecoration: "none", fontWeight: 600 }}>
                        {post.title}
                        {post.commentCount > 0 && (
                          <span style={{ color: "var(--primary)", marginLeft: "0.5rem", fontSize: "0.85rem" }}>
                            [{post.commentCount}]
                          </span>
                        )}
                      </Link>
                    </td>
                    <td style={{ padding: "1rem", fontSize: "0.9rem", color: "var(--text-sub)" }}>
                      {post.writerNickname}
                    </td>
                    <td style={{ padding: "1rem", textAlign: "center", fontSize: "0.85rem", color: "var(--text-muted)" }}>
                      👁️ {post.viewCount} | 👍 {post.likeCount} | 👎 {post.dislikeCount}
                    </td>
                    <td style={{ padding: "1rem", textAlign: "right", fontSize: "0.85rem", color: "var(--text-muted)" }}>
                      {new Date(post.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination Controls */}
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "1rem", marginTop: "2rem" }}>
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            style={{
              padding: "0.5rem 1rem",
              borderRadius: "6px",
              border: "1px solid var(--border-dark)",
              background: "var(--bg-dark-soft)",
              color: "var(--text-main)",
              cursor: page === 0 ? "not-allowed" : "pointer",
              opacity: page === 0 ? 0.5 : 1,
            }}
          >
            ◀ 이전
          </button>
          <span style={{ color: "var(--text-sub)", fontSize: "0.9rem" }}>
            {page + 1} / {totalPages} 페이지
          </span>
          <button
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            style={{
              padding: "0.5rem 1rem",
              borderRadius: "6px",
              border: "1px solid var(--border-dark)",
              background: "var(--bg-dark-soft)",
              color: "var(--text-main)",
              cursor: page + 1 >= totalPages ? "not-allowed" : "pointer",
              opacity: page + 1 >= totalPages ? 0.5 : 1,
            }}
          >
            다음 ▶
          </button>
        </div>
      </main>
    </div>
  );
}
