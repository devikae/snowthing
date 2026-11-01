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
  loading: () => <div className="p-12 text-center text-sm font-mono text-[var(--snow-muted)]">에디터를 불러오는 중입니다...</div>,
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
  { value: "NOTICE", label: "공지사항" },
  { value: "FREE", label: "자유게시판" },
  { value: "ANONYMOUS", label: "익명게시판" },
  { value: "QNA", label: "질문게시판" },
  { value: "FOOD", label: "리조트 맛집" },
  { value: "CREW", label: "동호회 모집" },
  { value: "SEASON_ROOM", label: "시즌방 모집" },
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
  const [writerNickname, setWriterNickname] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
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

          const res = await fetch(API_ENDPOINTS.posts.detail(publicId), {
            credentials: "include",
          });
          if (res.ok) {
            const data: PostDetail = await res.json();

            const isWriter = Boolean(currentUserPublicId && data.writer?.publicId === currentUserPublicId);
            setIsAuthor(isWriter);

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
            setWriterNickname(data.writer?.nickname || "익명의 사용자");
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

  const writerInitial = (writerNickname || "익").trim().charAt(0) || "익";

  return (
    <main className="compose-page community-container">
      <header className="compose-top-bar">
        <div className="compose-head-left">
          <small className="section-eyebrow">EDIT POST · EDITORIAL COMPOSER</small>
          <h1>게시글 수정</h1>
        </div>

        <div className="compose-meta-Cluster">
          <div className="compose-author-pill">
            <span className="m-avatar" aria-hidden="true">
              {writerInitial}
            </span>
            <span>작성자</span>
            <strong>{writerNickname || "익명의 사용자"}</strong>
          </div>

          {isAnonymous && !isAuthor && (
            <label className="compose-anon-pw-pill">
              <span className="material-symbols-outlined">lock</span>
              <input
                type="password"
                placeholder="작성 당시 익명 비밀번호"
                value={anonymousPassword}
                onChange={(event) => setAnonymousPassword(event.target.value)}
              />
            </label>
          )}
        </div>
      </header>

      <nav className="compose-category-bar" aria-label="게시판 변경">
        <div className="filter-pills">
          {categoryOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              className={categoryCode === option.value ? "active" : ""}
              onClick={() => setCategoryCode(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </nav>

      <form onSubmit={handleSubmit} className="compose-editorial-form">
        {errorMsg && (
          <div className="compose-error" role="alert">
            <span className="material-symbols-outlined">error</span>
            {errorMsg}
          </div>
        )}

        <div className="compose-title-bar">
          <label htmlFor="post-edit-title" className="compose-title-tag">
            제목
          </label>
          <input
            id="post-edit-title"
            type="text"
            maxLength={200}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="제목을 입력하세요"
            aria-label="글 제목"
          />
          <span className="compose-title-count" aria-hidden="true">
            {title.length}/200
          </span>
        </div>

        <div className="compose-sheet">
          <div className="compose-sheet-editor">
            <ToastEditor ref={editorRef} initialValue={content} height="480px" />
          </div>
        </div>

        <footer className="compose-footer-bar">
          <Link href={`/posts/${publicId}`} className="compose-cancel-pill">
            <span className="material-symbols-outlined">arrow_back</span> 취소
          </Link>
          <button type="submit" disabled={submitting} className="compose-submit-pill">
            <span className="material-symbols-outlined">check</span>
            {submitting ? "수정 중..." : "수정 완료"}
          </button>
        </footer>
      </form>
    </main>
  );
}

export default function PostEditPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);

  return (
    <div className="community-page">
      <TopNav active="posts" />
      <Suspense fallback={<div className="p-12 text-center text-sm text-[var(--snow-muted)]">로딩 중입니다.</div>}>
        <PostEditForm publicId={publicId} />
      </Suspense>
      <Footer />
    </div>
  );
}
