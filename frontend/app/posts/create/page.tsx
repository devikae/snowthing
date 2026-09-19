"use client";

import { useState, useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import { Footer, TopNav } from "../../components/SiteChrome";
import { ToastEditorHandle } from "../../components/ToastEditor";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS } from "../../lib/api";

const ToastEditor = dynamic(() => import("../../components/ToastEditor"), {
  ssr: false,
  loading: () => <div className="p-12 text-center text-sm font-mono text-gray-400">📝 스마트 에디터를 불러오는 중입니다...</div>,
});

interface MemberProfile {
  publicId: string;
  nickname: string;
}

function PostCreateForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialCat = searchParams.get("category") || "FREE";

  const [categoryCode, setCategoryCode] = useState(initialCat);
  const [title, setTitle] = useState("");
  const [anonymousPassword, setAnonymousPassword] = useState("");
  const [imageKey, setImageKey] = useState("");
  const [imagePreviewUrl, setImagePreviewUrl] = useState("");
  const [uploadingImage, setUploadingImage] = useState(false);

  const [userProfile, setUserProfile] = useState<MemberProfile | null>(null);
  const [checkingAuth, setCheckingAuth] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const editorRef = useRef<ToastEditorHandle>(null);
  const hasAlertedRef = useRef(false);

  // 로그인 상태 확인 & 권한 라우트 가드 (Route Guard - 중복 알림 방지 적용)
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

      // 비로그인 사용자 처리:
      // URL 파라미터가 없으면 기본 카테고리를 ANONYMOUS로 세팅하여 로그인 없이 작성 허용.
      // URL 파라미터가 ANONYMOUS가 아닌 멤버 전용 게시판인 경우에만 1회 안내 후 리다이렉트.
      const currentCategory = searchParams.get("category");
      if (!loggedInMember) {
        if (!currentCategory) {
          setCategoryCode("ANONYMOUS");
        } else if (currentCategory !== "ANONYMOUS") {
          if (!hasAlertedRef.current) {
            hasAlertedRef.current = true;
            alert("자유/Q&A/맛집 게시판 글쓰기는 로그인이 필요합니다.");
            router.replace("/login?redirect=/posts/create");
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
  const isCategoryLocked = searchParams.has("category");

  const handleCategoryChange = (newCategory: string) => {
    if (newCategory !== "ANONYMOUS" && !userProfile) {
      alert("자유/Q&A/맛집 게시판 글쓰기는 로그인이 필요합니다.");
      router.push("/login?redirect=/posts/create");
      return;
    }
    setCategoryCode(newCategory);
  };

  const handleImageUpload = async (file: File | undefined) => {
    if (!file) return;

    setUploadingImage(true);
    setErrorMsg("");
    const formData = new FormData();
    formData.append("file", file);

    try {
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
      setImageKey(uploaded.imageKey);
      setImagePreviewUrl(uploaded.imageUrl);
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
      setErrorMsg("자유/Q&A/맛집 게시판은 로그인이 필요합니다.");
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
          imageUrls: imageKey ? [imageKey] : [],
        }),
      });

      if (res.ok) {
        const data = await res.json();
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
      <div className="max-w-4xl mx-auto px-6 py-20 text-center text-xs font-mono text-[#6b7280]">
        🔒 작성 권한 및 세션을 확인하는 중입니다...
      </div>
    );
  }

  const boardName = categoryCode === "ANONYMOUS" ? "익명게시판" : categoryCode === "QNA" ? "장비·테크닉" : categoryCode === "FOOD" ? "리조트 맛집" : "자유게시판";

  return (
    <main className="compose-page community-container">
      <header className="compose-heading">
        <span className="compose-heading-icon material-symbols-outlined">edit_square</span>
        <h1>{boardName} 글쓰기</h1>
      </header>

      <div className="compose-layout">
        <form onSubmit={handleSubmit} className="compose-card">
        {errorMsg && (
          <div className="compose-error" role="alert">
            <span className="material-symbols-outlined">error</span>{errorMsg}
          </div>
        )}

        <section className="compose-section">
          <label htmlFor="post-category" className="compose-label">주제 분류 <b>*필수선택</b></label>
          <select
            id="post-category"
            value={categoryCode}
            disabled={isCategoryLocked}
            onChange={(e) => handleCategoryChange(e.target.value)}
            className="compose-select"
          >
            <option value="FREE">자유게시판</option>
            <option value="ANONYMOUS">익명 게시판</option>
            <option value="QNA">장비 Q&A</option>
            <option value="FOOD">리조트 맛집</option>
          </select>
          {isCategoryLocked && (
            <p className="compose-help">현재 게시판으로 작성 위치가 고정되어 있습니다.</p>
          )}
        </section>

        <section className="compose-author-box">
          <div className="compose-author">
            <span className="material-symbols-outlined">{isAnonCategory ? "theater_comedy" : "person"}</span>
            <strong>{isAnonCategory ? "익명의 사용자" : userProfile?.nickname || "로그인 사용자"}</strong>
          </div>
          {isAnonCategory && !userProfile && (
            <label className="compose-password">
              <span>글 수정/삭제 비밀번호</span>
              <span className="compose-password-field">
                <input type="password" placeholder="비밀번호 입력" value={anonymousPassword} onChange={(e) => setAnonymousPassword(e.target.value)} />
                <span className="material-symbols-outlined">lock</span>
              </span>
            </label>
          )}
        </section>

        <section className="compose-section">
          <label htmlFor="post-title" className="compose-label">글 제목</label>
          <input
            id="post-title"
            type="text"
            maxLength={200}
            placeholder="글 제목을 입력해주세요."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="compose-title-input"
          />
        </section>

        <section className="compose-section">
          <label className="compose-label">본문 내용</label>
          <div className="compose-editor">
            <ToastEditor ref={editorRef} initialValue="" height="500px" />
          </div>
        </section>

        <section className="compose-section">
          <label htmlFor="post-image" className="compose-label"><span className="material-symbols-outlined">attach_file</span> 사진 첨부 <small>(선택, 최대 5MB)</small></label>
          <input
            id="post-image"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            disabled={!userProfile || uploadingImage}
            onChange={(e) => handleImageUpload(e.target.files?.[0])}
            className="compose-image-input"
          />
          <p className="compose-help">
            {!userProfile
              ? "이미지 첨부는 로그인한 회원만 사용할 수 있습니다."
              : uploadingImage
                ? "이미지를 업로드하고 있습니다."
                : imageKey
                  ? "이미지 업로드가 완료되었습니다."
                  : "JPG, PNG, WebP 이미지 1개를 첨부할 수 있습니다."}
          </p>
          {imagePreviewUrl && (
            <img src={imagePreviewUrl} alt="첨부 이미지 미리보기" className="mt-3 max-h-64 rounded-xl object-contain" />
          )}
        </section>

        <footer className="compose-actions">
          <Link
            href={`/posts${categoryCode ? `?category=${categoryCode}` : ""}`}
            className="compose-cancel"
          >
            <span className="material-symbols-outlined">arrow_back</span> 취소 / 뒤로가기
          </Link>
          <button
            type="submit"
            disabled={submitting}
            className="compose-submit"
          >
            <span className="material-symbols-outlined">edit_note</span>
            {submitting ? "등록 처리 중..." : `${isAnonCategory ? "익명 " : ""}게시글 등록`}
          </button>
        </footer>
        </form>

        <aside className="compose-sidebar" aria-label="참고 정보">
          <section className="compose-side-card">
            <header><span className="material-symbols-outlined">ac_unit</span><strong>오늘의 설질</strong><small>참고 정보</small></header>
            <div className="compose-weather-grid">
              <article><span>용평 발왕산</span><b>-6.4°C</b><small>설질: 최상 파우더</small></article>
              <article><span>휘닉스 몽블랑</span><b>-4.8°C</b><small>설질: 압설 양호</small></article>
            </div>
          </section>
          <p className="compose-side-note">설질 영역은 현재 화면 구성을 위한 예시입니다.</p>
        </aside>
      </div>
    </main>
  );
}

export default function PostCreatePage() {
  return (
    <div className="community-page">
      <TopNav active="posts" />
      <Suspense fallback={<div className="p-12 text-center text-xs text-[#6b7280]">로딩 중입니다...</div>}>
        <PostCreateForm />
      </Suspense>
      <Footer />
    </div>
  );
}
