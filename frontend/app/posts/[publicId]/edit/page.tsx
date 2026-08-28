"use client";

import { Suspense, use, useEffect, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import { ToastEditorHandle } from "../../../components/ToastEditor";
import { Footer, TopNav } from "../../../components/SiteChrome";
import { csrfFetch } from "../../../lib/csrfFetch";
import { API_ENDPOINTS } from "../../../lib/api";

const ToastEditor = dynamic(() => import("../../../components/ToastEditor"), {
  ssr: false,
  loading: () => <div className="p-12 text-center text-sm font-mono text-gray-400">📝 스마트 에디터를 불러오는 중입니다...</div>,
});

interface PostDetail {
  publicId: string;
  categoryCode: string;
  title: string;
  content: string;
  isAnonymous: boolean;
  writer?: {
    publicId: string;
    nickname: string;
  };
}

const categoryOptions = [
  { value: "FREE", label: "자유 게시판" },
  { value: "ANONYMOUS", label: "익명 게시판" },
  { value: "QNA", label: "장비 Q&A" },
  { value: "FOOD", label: "리조트 맛집" },
];

function PostEditForm({ publicId }: { publicId: string }) {
  const router = useRouter();
  const editorRef = useRef<ToastEditorHandle>(null);
  const [categoryCode, setCategoryCode] = useState("FREE");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [anonymousPassword, setAnonymousPassword] = useState("");
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [isAuthor, setIsAuthor] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          // 1. 현재 로그인한 유저 정보 확인
          let currentUserPublicId: string | null = null;
          try {
            const meRes = await fetch(API_ENDPOINTS.members.me, { credentials: "include" });
            if (meRes.ok) {
              const meData = await meRes.json();
              currentUserPublicId = meData.publicId || null;
            }
          } catch {
            currentUserPublicId = null;
          }

          // 2. 게시글 상세 정보 확인
          const res = await fetch(API_ENDPOINTS.posts.detail(publicId), {
            credentials: "include",
          });
          if (res.ok) {
            const data: PostDetail = await res.json();

            const isWriter = Boolean(currentUserPublicId && data.writer?.publicId === currentUserPublicId);
            setIsAuthor(isWriter);

            // 작성자 권한 검증: 익명글이 아닌 경우 본인만 접근 가능
            if (!data.isAnonymous) {
              if (!isWriter) {
                alert("본인이 작성한 글만 수정할 수 있습니다.");
                router.replace(`/posts/${publicId}`);
                return;
              }
            }

            setCategoryCode(data.categoryCode || "FREE");
            setTitle(data.title || "");
            setContent(data.content || "");
            setIsAnonymous(Boolean(data.isAnonymous));
          } else {
            setErrorMsg("수정할 게시글 정보를 불러오지 못했습니다.");
          }
        } catch {
          setErrorMsg("서버 통신 중 오류가 발생했습니다.");
        } finally {
          setLoading(false);
        }
      })();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [publicId, router]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMsg("");
    const editorContent = editorRef.current?.getInstance().getMarkdown() || content;

    if (!title.trim()) {
      setErrorMsg("제목을 입력해주세요.");
      return;
    }

    if (!editorContent.trim()) {
      setErrorMsg("본문 내용을 입력해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      const res = await csrfFetch(API_ENDPOINTS.posts.update(publicId), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryCode,
          title: title.trim(),
          content: editorContent.trim(),
          anonymousPassword: isAnonymous && !isAuthor ? anonymousPassword : null,
        }),
      });

      if (res.ok) {
        router.push(`/posts/${publicId}`);
        return;
      }

      const errorData = await res.json();
      setErrorMsg(errorData.message || "게시글 수정에 실패했습니다.");
    } catch {
      setErrorMsg("서버 통신 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="p-12 text-center text-sm text-[var(--snow-muted)]">게시글 정보를 불러오는 중입니다.</div>;
  }

  return (
    <main className="snow-container px-5 py-8 lg:px-8 lg:py-10">
      <div className="mx-auto max-w-4xl">
        <div className="mb-7 border-b-2 border-black pb-5">
          <span className="snow-label">Community Editor</span>
          <h1 className="mt-2 text-4xl font-extrabold italic text-black">EDIT POST</h1>
          <p className="mt-3 text-[var(--snow-muted)]">작성한 게시글의 제목과 본문을 수정합니다.</p>
        </div>

        <form onSubmit={handleSubmit} className="snow-card bg-white p-6 md:p-8">
          {errorMsg && (
            <div className="mb-6 rounded border border-[#fecaca] bg-[#fef2f2] p-4 text-sm font-semibold text-[#dc2626]">
              {errorMsg}
            </div>
          )}

          <div className="grid gap-6">
            <label className="grid gap-2">
              <span className="snow-label">Category</span>
              <select value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)} className="snow-select">
                {categoryOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="grid gap-2">
              <span className="snow-label">Title</span>
              <input value={title} onChange={(event) => setTitle(event.target.value)} className="snow-input" />
            </label>

            <div className="grid gap-2">
              <span className="snow-label">Content</span>
              <div className="rounded border border-[#e5e7eb] bg-white p-1">
                <ToastEditor ref={editorRef} initialValue={content} height="500px" />
              </div>
            </div>

            {isAnonymous && !isAuthor && (
              <label className="grid gap-2">
                <span className="snow-label">Anonymous Password</span>
                <input
                  type="password"
                  placeholder="작성 당시 등록한 익명 비밀번호"
                  value={anonymousPassword}
                  onChange={(event) => setAnonymousPassword(event.target.value)}
                  className="snow-input"
                />
              </label>
            )}
          </div>

          <div className="mt-8 flex justify-end gap-3 border-t border-[var(--snow-border)] pt-5">
            <Link href={`/posts/${publicId}`} className="snow-btn-secondary">
              취소
            </Link>
            <button type="submit" disabled={submitting} className="snow-btn-primary">
              {submitting ? "수정 중" : "수정 완료"}
            </button>
          </div>
        </form>
      </div>
    </main>
  );
}

export default function PostEditPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="posts" />
      <Suspense fallback={<div className="p-12 text-center text-sm text-[var(--snow-muted)]">로딩 중입니다.</div>}>
        <PostEditForm publicId={publicId} />
      </Suspense>
      <Footer />
    </div>
  );
}
