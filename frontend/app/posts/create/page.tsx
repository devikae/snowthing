"use client";

import { useState, useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
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
  const [content, setContent] = useState("");
  const [anonymousPassword, setAnonymousPassword] = useState("");
  const [imageUrl, setImageUrl] = useState("");

  const [userProfile, setUserProfile] = useState<MemberProfile | null>(null);
  const [checkingAuth, setCheckingAuth] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const editorRef = useRef<ToastEditorHandle>(null);
  const hasAlertedRef = useRef(false);

  // URL 카테고리 동기화
  useEffect(() => {
    const catFromUrl = searchParams.get("category");
    if (catFromUrl) {
      setCategoryCode(catFromUrl);
    }
  }, [searchParams]);

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const editorContent = editorRef.current?.getInstance().getMarkdown() || content;

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

  return (
    <main className="max-w-4xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-black italic tracking-wide text-[#111827]">
          {isAnonCategory ? "🕵️ 익명 게시판 게시글 작성" : "NEW POST"}
        </h1>
        <p className="text-xs text-[#6b7280] mt-1 font-mono">
          {isAnonCategory
            ? "작성자 닉네임이 외부 화면에 노출되지 않는 익명 글쓰기 공간"
            : "자유게시판 및 장비 Q&A 본문 작성"}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white border border-[#e5e7eb] rounded p-6 shadow-sm space-y-5">
        {errorMsg && (
          <div className="bg-[#fef2f2] border border-[#fecaca] p-3 rounded text-xs text-[#dc2626] font-semibold">
            🚨 {errorMsg}
          </div>
        )}

        {/* Category Selection */}
        <div className="space-y-1">
          <label className="text-xs font-mono font-bold text-[#45464c]">카테고리</label>
          <select
            value={categoryCode}
            disabled={isCategoryLocked}
            onChange={(e) => handleCategoryChange(e.target.value)}
            className="w-full bg-[#f9f9f9] border border-[#e5e7eb] rounded px-3 py-2 text-xs font-bold text-[#111827] focus:outline-none focus:border-[#111827] disabled:opacity-80 disabled:cursor-not-allowed"
          >
            <option value="FREE">자유게시판</option>
            <option value="ANONYMOUS">익명 게시판</option>
            <option value="QNA">장비 Q&A</option>
            <option value="FOOD">리조트 맛집</option>
          </select>
          {isCategoryLocked && (
            <p className="text-[11px] font-mono text-[#6b7280]">
              * 진입 게시판 카테고리로 작성 위치가 고정되었습니다.
            </p>
          )}
        </div>

        {/* Title */}
        <div className="space-y-1">
          <label className="text-xs font-mono font-bold text-[#45464c]">게시글 제목</label>
          <input
            type="text"
            placeholder="제목을 입력하세요..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full bg-white border border-[#e5e7eb] rounded px-3 py-2 text-sm text-[#111827] focus:outline-none focus:border-[#111827]"
          />
        </div>

        {/* Content - Toast UI Editor */}
        <div className="space-y-1">
          <label className="text-xs font-mono font-bold text-[#45464c]">본문 내용 (스마트 에디터 / 마크다운 듀얼)</label>
          <div className="rounded border border-[#e5e7eb] bg-white p-1">
            <ToastEditor ref={editorRef} initialValue="" height="500px" />
          </div>
        </div>

        {/* Image URL Optional */}
        <div className="space-y-1">
          <label className="text-xs font-mono font-bold text-[#45464c]">첨부 이미지 URL (선택)</label>
          <input
            type="text"
            placeholder="https://cdn.example.com/image.jpg"
            value={imageUrl}
            onChange={(e) => setImageUrl(e.target.value)}
            className="w-full bg-white border border-[#e5e7eb] rounded px-3 py-2 text-xs font-mono text-[#111827] focus:outline-none focus:border-[#111827]"
          />
        </div>

        {/* Anonymous Category Condition Handling */}
        {isAnonCategory && (
          <div className="bg-[#e6f7f0] border border-[#a7f3d0] rounded p-4 space-y-3">
            <div className="text-xs font-bold text-[#10b981] flex items-center gap-1">
              <span className="material-symbols-outlined text-sm">visibility_off</span>
              <span>익명 게시판 작성 안내</span>
            </div>

            {userProfile ? (
              <div className="text-xs text-[#065f46] space-y-1">
                <p className="font-bold">
                  🟢 로그인 상태입니다 ({userProfile.nickname} 님).
                </p>
                <p>
                  작성자 닉네임은 외부 화면에서 완전히 숨겨져 "익명 보더"로 표시되며, 본인 계정 정보는 안전하게 DB에 보존되어 비밀번호 입력 없이 즉시 등록 및 삭제가 가능합니다.
                </p>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-xs text-[#065f46]">
                  비로그인 사용자로 익명글을 작성합니다. 글 수정 및 삭제에 필요한 비밀번호를 설정해 주세요.
                </p>
                <input
                  type="password"
                  placeholder="익명 글 수정/삭제용 비밀번호 (4자리 이상)"
                  value={anonymousPassword}
                  onChange={(e) => setAnonymousPassword(e.target.value)}
                  className="w-full bg-white border border-[#a7f3d0] rounded px-3 py-2 text-xs text-[#111827] focus:outline-none focus:border-[#10b981]"
                />
                <p className="text-[11px] font-bold text-[#dc2626] bg-[#fef2f2] p-2 rounded border border-[#fecaca]">
                  ⚠️ 익명 비밀번호 분실 시 게시글 수정 및 삭제가 불가능합니다.
                </p>
              </div>
            )}
          </div>
        )}

        {/* Buttons */}
        <div className="flex justify-end gap-3 pt-3 border-t border-[#e5e7eb]">
          <Link
            href={isAnonCategory ? "/posts/anonymous" : "/posts"}
            className="px-4 py-2 rounded border border-[#e5e7eb] text-xs font-semibold text-[#45464c] hover:border-[#111827] transition"
          >
            취소
          </Link>
          <button
            type="submit"
            disabled={submitting}
            className="px-5 py-2 rounded bg-[#111827] text-white text-xs font-semibold hover:bg-[#1f2937] transition shadow-sm"
          >
            {submitting ? "등록 처리 중..." : "게시글 등록하기"}
          </button>
        </div>
      </form>
    </main>
  );
}

export default function PostCreatePage() {
  return (
    <div className="min-h-screen bg-[#f9f9f9]">
      <header className="border-b border-[#e5e7eb] bg-white px-6 py-4 sticky top-0 z-50 shadow-sm">
        <div className="max-w-6xl mx-auto flex justify-between items-center">
          <Link href="/posts" className="text-xl font-extrabold tracking-tight italic flex items-center gap-2 text-[#111827]">
            <span className="material-symbols-outlined text-2xl">downhill_skiing</span>
            <span>SNOWBOARDERS</span>
          </Link>
          <Link href="/posts" className="text-xs font-mono font-semibold text-[#6b7280] hover:text-[#111827]">
            ◀ 목록으로 돌아가기
          </Link>
        </div>
      </header>

      <Suspense fallback={<div className="p-12 text-center text-xs text-[#6b7280]">로딩 중입니다...</div>}>
        <PostCreateForm />
      </Suspense>
    </div>
  );
}
