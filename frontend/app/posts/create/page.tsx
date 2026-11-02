"use client";

import { useState, useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { DECK_QUESTION_KEY, readDeckQuestionDraft } from "../../lib/toolQuestionDraft";
import dynamic from "next/dynamic";
import { Footer, TopNav } from "../../components/SiteChrome";
import { ToastEditorHandle } from "../../components/ToastEditor";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS } from "../../lib/api";

const ToastEditor = dynamic(() => import("../../components/ToastEditor"), {
  ssr: false,
  loading: () => <div className="p-12 text-center text-sm font-mono text-[var(--snow-muted)]">에디터를 불러오는 중입니다...</div>,
});

interface MemberProfile {
  publicId: string;
  nickname: string;
  role: string;
}

const COMPOSE_CATEGORIES = [
  { code: "NOTICE", label: "공지사항", adminOnly: true },
  { code: "FREE", label: "자유게시판", adminOnly: false },
  { code: "ANONYMOUS", label: "익명게시판", adminOnly: false },
  { code: "QNA", label: "질문게시판", adminOnly: false },
  { code: "FOOD", label: "리조트 맛집", adminOnly: false },
  { code: "CREW", label: "동호회 모집", adminOnly: false },
  { code: "SEASON_ROOM", label: "시즌방 모집", adminOnly: false },
] as const;

function PostCreateForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialCat = searchParams.get("category") || "FREE";

  const [categoryCode, setCategoryCode] = useState(initialCat);
  const [toolDraft] = useState(() => readDeckQuestionDraft(searchParams.get("draft") === "deck" && initialCat === "QNA"));
  const [title, setTitle] = useState(toolDraft?.title ?? "");
  const [anonymousPassword, setAnonymousPassword] = useState("");
  const [images, setImages] = useState<{ imageKey: string; imageUrl: string }[]>([]);
  const [uploadingImage, setUploadingImage] = useState(false);

  const [userProfile, setUserProfile] = useState<MemberProfile | null>(null);
  const [checkingAuth, setCheckingAuth] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const editorRef = useRef<ToastEditorHandle>(null);
  const hasAlertedRef = useRef(false);

  useEffect(() => {
    let isSubscribed = true;

    const checkLoginStatus = async () => {
      let loggedInMember: MemberProfile | null = null;
      try {
        const res = await fetch(API_ENDPOINTS.members.me, {
          credentials: "include",
        });
        if (res.ok) {
          loggedInMember = await res.json();
          if (isSubscribed) setUserProfile(loggedInMember);
        } else {
          if (isSubscribed) setUserProfile(null);
        }
      } catch {
        if (isSubscribed) setUserProfile(null);
      } finally {
        if (isSubscribed) setCheckingAuth(false);
      }

      const currentCategory = searchParams.get("category");
      if (currentCategory === "NOTICE" && loggedInMember && loggedInMember.role !== "ROLE_ADMIN") {
        router.replace("/posts?category=NOTICE");
        return;
      }
      if (!loggedInMember) {
        if (!currentCategory) {
          setCategoryCode("ANONYMOUS");
        } else if (currentCategory !== "ANONYMOUS") {
          if (!hasAlertedRef.current) {
            hasAlertedRef.current = true;
            const destination = `/posts/create?category=${encodeURIComponent(currentCategory)}${searchParams.get("draft") === "deck" ? "&draft=deck" : ""}`;
            router.replace(`/login?returnUrl=${encodeURIComponent(destination)}`);
          }
        }
      }
    };
    checkLoginStatus();

    return () => {
      isSubscribed = false;
    };
  }, [searchParams, router]);

  const isAnonCategory = categoryCode === "ANONYMOUS";

  const handleCategorySelect = (nextCode: string) => {
    if (!userProfile && nextCode !== "ANONYMOUS") {
      router.push(`/login?returnUrl=${encodeURIComponent(`/posts/create?category=${nextCode}`)}`);
      return;
    }
    setCategoryCode(nextCode);
  };

  const handleImageUpload = async (files: FileList | null) => {
    if (!files?.length || uploadingImage) return;
    if (images.length + files.length > 10) {
      setErrorMsg("사진은 최대 10장까지 등록할 수 있습니다.");
      return;
    }

    setUploadingImage(true);
    setErrorMsg("");
    try {
      for (const file of Array.from(files)) {
      const formData = new FormData();
      formData.append("file", file);
      const response = await csrfFetch(API_ENDPOINTS.images.upload, {
        method: "POST",
        body: formData,
      });
      if (!response.ok) {
        const error = await response.json();
        setErrorMsg(error.message || "이미지 업로드에 실패했습니다.");
        return;
      }

      const uploaded: { imageUrl: string; imageKey: string } = await response.json();
      setImages((current) => [...current, uploaded]);
      }
    } catch {
      setErrorMsg("이미지 업로드 중 서버 통신 오류가 발생했습니다.");
    } finally {
      setUploadingImage(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const editorContent = editorRef.current?.getInstance().getMarkdown() || "";

    if (!title.trim()) {
      setErrorMsg("제목을 입력해 주세요.");
      return;
    }
    if (!editorContent.trim()) {
      setErrorMsg("본문 내용을 입력해 주세요.");
      return;
    }
    if (!isAnonCategory && !userProfile) {
      setErrorMsg("자유/질문/맛집 게시판은 로그인이 필요합니다.");
      return;
    }
    if (isAnonCategory && !userProfile && !anonymousPassword.trim()) {
      setErrorMsg("비로그인 사용자는 익명글 수정/삭제용 비밀번호를 반드시 입력해야 합니다.");
      return;
    }

    setSubmitting(true);
    setErrorMsg("");

    try {
      const res = await csrfFetch(API_ENDPOINTS.posts.create, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryCode,
          title: title.trim(),
          content: editorContent.trim(),
          isAnonymous: isAnonCategory,
          anonymousPassword: isAnonCategory && !userProfile ? anonymousPassword : null,
          imageUrls: images.map((image) => image.imageKey),
        }),
      });

      if (res.ok) {
        const data = await res.json();
        if (toolDraft) { try { sessionStorage.removeItem(DECK_QUESTION_KEY); } catch { /* Draft cleanup must not prevent navigation. */ } }
        router.push(`/posts/${data.publicId}`);
      } else {
        const err = await res.json();
        setErrorMsg(err.message || "게시글 작성 실패");
      }
    } catch {
      setErrorMsg("서버 통신 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  if (checkingAuth) {
    return (
      <div className="max-w-4xl mx-auto px-6 py-20 text-center text-xs font-mono text-[var(--snow-muted)]">
        작성 권한 및 세션을 확인하는 중입니다...
      </div>
    );
  }

  const boardName =
    COMPOSE_CATEGORIES.find((item) => item.code === categoryCode)?.label ?? "자유게시판";
  const writerName = isAnonCategory ? "익명의 사용자" : userProfile?.nickname || "로그인 사용자";
  const writerInitial = writerName.trim().charAt(0) || "익";

  return (
    <main className="compose-page community-container content-compose-page">
      <header className="compose-top-bar">
        <div className="compose-head-left">
          <h1>{boardName} 글쓰기</h1>
        </div>

        <div className="compose-meta-Cluster">
          <div className={`compose-author-pill${isAnonCategory ? " anonymous" : ""}`}>
            {isAnonCategory ? <strong>익명</strong> : <>
              <span className="m-avatar" aria-hidden="true">{writerInitial}</span>
              <span>작성자</span>
              <strong>{writerName}</strong>
            </>}
          </div>

          {isAnonCategory && !userProfile && (
            <label className="compose-anon-pw-pill">
              <span className="material-symbols-outlined">lock</span>
              <input
                type="password"
                placeholder="수정·삭제 비밀번호"
                value={anonymousPassword}
                onChange={(e) => setAnonymousPassword(e.target.value)}
              />
            </label>
          )}
        </div>
      </header>

      <nav className="compose-category-bar" aria-label="작성할 게시판 선택">
        <div className="filter-pills">
          {COMPOSE_CATEGORIES.filter((cat) => !cat.adminOnly || userProfile?.role === "ROLE_ADMIN").map((cat) => (
            <button
              key={cat.code}
              type="button"
              className={categoryCode === cat.code ? "active" : ""}
              onClick={() => handleCategorySelect(cat.code)}
            >
              {cat.label}
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
          <label htmlFor="post-title" className="compose-title-tag">
            제목
          </label>
          <input
            id="post-title"
            type="text"
            maxLength={200}
            placeholder="제목을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            aria-label="글 제목"
          />
          <span className="compose-title-count" aria-hidden="true">
            {title.length}/200
          </span>
        </div>

        <div className="compose-sheet">
          <div className="compose-sheet-editor">
            <ToastEditor ref={editorRef} initialValue={toolDraft?.content ?? ""} height="480px" />
          </div>

          <div className="compose-sheet-attach">
            <div className="compose-attach-row">
              <label
                htmlFor="post-image"
                className={`compose-attach-btn ${!userProfile || uploadingImage || images.length >= 10 ? "disabled" : ""}`}
              >
                <span className="material-symbols-outlined">image</span>
                <span>{uploadingImage ? "업로드 중..." : "사진 첨부"}</span>
              </label>
              <input
                id="post-image"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                disabled={!userProfile || uploadingImage || images.length >= 10}
                onChange={(e) => { void handleImageUpload(e.target.files); e.target.value = ""; }}
                hidden
              />
              <span className="compose-attach-hint">
                {!userProfile
                  ? "이미지 첨부는 로그인한 회원만 사용할 수 있습니다."
                  : uploadingImage
                    ? "이미지를 서버에 업로드하고 있습니다..."
                    : `JPG, PNG, WebP · 장당 최대 5MB · ${images.length}/10장`}
              </span>
            </div>

            <div className="compose-attach-grid">{images.map((image, index) => (
              <div className="compose-attach-preview" key={image.imageKey}>
                <Image
                  src={image.imageUrl}
                  alt={`첨부 이미지 ${index + 1}`}
                  width={320}
                  height={180}
                  unoptimized
                />
                <button
                  type="button"
                  className="compose-attach-remove"
                  aria-label={`${index + 1}번째 사진 제거`}
                  onClick={() => setImages((current) => current.filter((item) => item.imageKey !== image.imageKey))}
                >
                  <span className="material-symbols-outlined">close</span>
                </button>
              </div>
            ))}</div>
          </div>
        </div>

        <footer className="compose-footer-bar">
          <Link href={`/posts${categoryCode ? `?category=${categoryCode}` : ""}`} className="compose-cancel-pill">
            <span className="material-symbols-outlined">arrow_back</span> 목록으로
          </Link>
          <button type="submit" disabled={submitting || uploadingImage} className="compose-submit-pill">
            <span className="material-symbols-outlined">check</span>
            {submitting ? "등록 처리 중..." : `${isAnonCategory ? "익명 " : ""}게시글 등록`}
          </button>
        </footer>
      </form>
    </main>
  );
}

export default function PostCreatePage() {
  return (
    <div className="community-page">
      <TopNav active="posts" />
      <Suspense fallback={<div className="p-20 text-center text-xs font-mono text-[var(--snow-muted)]">글쓰기 화면을 준비하고 있습니다...</div>}>
        <PostCreateForm />
      </Suspense>
      <Footer />
    </div>
  );
}
