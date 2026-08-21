"use client";

import { useState, useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";

function PostCreateForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialCat = searchParams.get("category") || "FREE";

  const [categoryCode, setCategoryCode] = useState(initialCat);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [anonymousPassword, setAnonymousPassword] = useState("");
  const [imageUrl, setImageUrl] = useState("");

  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const catFromUrl = searchParams.get("category");
    if (catFromUrl) {
      setCategoryCode(catFromUrl);
    }
  }, [searchParams]);

  const isAnonCategory = categoryCode === "ANONYMOUS";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setErrorMsg("제목을 입력해 주세요.");
      return;
    }
    if (!content.trim()) {
      setErrorMsg("본문을 입력해 주세요.");
      return;
    }
    if (isAnonCategory && !anonymousPassword.trim()) {
      setErrorMsg("익명 게시판은 수정/삭제용 비밀번호 입력이 필수입니다.");
      return;
    }

    setSubmitting(true);
    setErrorMsg("");

    try {
      const res = await fetch("http://localhost:8080/api/posts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          categoryCode,
          title,
          content,
          isAnonymous: isAnonCategory,
          anonymousPassword: isAnonCategory ? anonymousPassword : null,
          imageUrls: imageUrl.trim() ? [imageUrl.trim()] : [],
        }),
      });

      if (res.ok) {
        const data = await res.json();
        router.push(`/posts/${data.publicId}`);
      } else {
        const err = await res.json();
        setErrorMsg(err.message || "게시글 작성 실패");
      }
    } catch (err) {
      console.error(err);
      setErrorMsg("서버 통신 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="container" style={{ paddingTop: "2rem", paddingBottom: "4rem", maxWidth: "800px" }}>
      <h1 style={{ fontSize: "1.8rem", fontWeight: "bold", marginBottom: "1.5rem" }}>✏️ 새 게시글 작성</h1>

      <form onSubmit={handleSubmit} className="card-supabase">
        {errorMsg && (
          <div style={{ padding: "0.75rem", backgroundColor: "rgba(255, 77, 79, 0.1)", border: "1px solid var(--error)", borderRadius: "6px", color: "var(--error)", marginBottom: "1.25rem", fontSize: "0.9rem" }}>
            ⚠️ {errorMsg}
          </div>
        )}

        {/* Category Dropdown */}
        <div className="form-group">
          <label className="form-label">카테고리 선택</label>
          <select
            value={categoryCode}
            onChange={(e) => setCategoryCode(e.target.value)}
            className="input-supabase"
            style={{ fontWeight: 600 }}
          >
            <option value="FREE">자유게시판</option>
            <option value="ANONYMOUS">익명게시판 (랜덤 닉네임 자동 부여)</option>
            <option value="QNA">질문게시판</option>
            <option value="FOOD">맛집게시판</option>
          </select>
        </div>

        {/* Title */}
        <div className="form-group">
          <label className="form-label">제목</label>
          <input
            type="text"
            placeholder="제목을 입력하세요 (최대 200자)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="input-supabase"
          />
        </div>

        {/* Content */}
        <div className="form-group">
          <label className="form-label">본문 내용</label>
          <textarea
            rows={10}
            placeholder="내용을 입력하세요..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="input-supabase"
            style={{ resize: "vertical", fontFamily: "inherit" }}
          />
        </div>

        {/* Image URL Optional */}
        <div className="form-group">
          <label className="form-label">첨부 이미지 URL (선택)</label>
          <input
            type="text"
            placeholder="https://example.com/image.jpg"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            className="input-supabase"
          />
        </div>

        {/* Anonymous Category Banner & Password Input */}
        {isAnonCategory && (
          <div style={{ background: "rgba(62, 207, 142, 0.08)", border: "1px solid var(--primary-deep)", borderRadius: "8px", padding: "1rem", marginBottom: "1.25rem" }}>
            <div style={{ fontSize: "0.9rem", color: "var(--primary)", fontWeight: 600, marginBottom: "0.5rem" }}>
              🕵️ 익명게시판 작성 안내
            </div>
            <p style={{ fontSize: "0.85rem", color: "var(--text-sub)", marginBottom: "0.75rem" }}>
              익명게시판에 작성 시 작성자명은 "익명 보더"로 자동 처리됩니다. 수정/삭제를 위한 비밀번호를 입력해 주세요.
            </p>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label">익명 비밀번호</label>
              <input
                type="password"
                placeholder="수정/삭제용 비밀번호 입력"
                value={anonymousPassword}
                onChange={(e) => setAnonymousPassword(e.target.value)}
                className="input-supabase"
              />
            </div>
          </div>
        )}

        {/* Buttons */}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem", marginTop: "2rem" }}>
          <Link
            href="/posts"
            style={{
              padding: "0.75rem 1.25rem",
              borderRadius: "6px",
              border: "1px solid var(--border-dark)",
              background: "transparent",
              color: "var(--text-sub)",
              textDecoration: "none",
              fontSize: "0.95rem",
            }}
          >
            취소
          </Link>
          <button type="submit" disabled={submitting} className="btn-primary-green">
            {submitting ? "작성 중..." : "게시글 등록하기"}
          </button>
        </div>
      </form>
    </main>
  );
}

export default function PostCreatePage() {
  return (
    <div style={{ minHeight: "100vh", backgroundColor: "var(--bg-dark)", color: "var(--text-main)" }}>
      <header style={{ borderBottom: "1px solid var(--border-dark)", background: "var(--bg-dark-soft)", padding: "1rem 2rem" }}>
        <div className="container" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Link href="/posts" style={{ fontSize: "1.5rem", fontWeight: "bold", color: "var(--primary)", textDecoration: "none" }}>
            🏂 Snowthing Board
          </Link>
          <Link href="/posts" style={{ color: "var(--text-sub)", textDecoration: "none" }}>
            ◀ 목록으로 돌아가기
          </Link>
        </div>
      </header>

      <Suspense fallback={<div style={{ padding: "3rem", textAlign: "center" }}>로딩 중...</div>}>
        <PostCreateForm />
      </Suspense>
    </div>
  );
}
