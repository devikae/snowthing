"use client";

import { Suspense, use, useEffect, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import dynamic from "next/dynamic";
import { ToastEditorHandle } from "../../../components/ToastEditor";
import { Footer, TopNav } from "../../../components/SiteChrome";
import { csrfFetch } from "../../../lib/csrfFetch";
import { useLeaveWarning } from "../../../lib/useLeaveWarning";
import { useImageUploadLease } from "../../../lib/useImageUploadLease";
import { API_ENDPOINTS } from "../../../lib/api";
import { errorText } from "../../../lib/errorText";
import { OVERSIZED_IMAGE_MESSAGE, isOversizedImage, prepareImagesForUpload } from "../../../lib/imageFile";

const ToastEditor = dynamic(() => import("../../../components/ToastEditor"), {
  ssr: false,
  loading: () => <div className="p-12 text-center text-sm text-[var(--snow-muted)]">에디터를 불러오는 중입니다...</div>,
});

interface PostDetail {
  publicId: string;
  categoryCode: string;
  title: string;
  content: string;
  isAnonymous: boolean;
  isAuthor: boolean;
  images: string[];
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
  useLeaveWarning();
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
  const [images, setImages] = useState<string[]>([]);
  useImageUploadLease(images);
  const [canUploadImages, setCanUploadImages] = useState(false);
  const [uploadingImages, setUploadingImages] = useState(false);

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
            if (data.categoryCode === "CARPOOL") {
              router.replace(`/carpool/${publicId}/edit`);
              return;
            }

            const isWriter = data.isAuthor;
            setIsAuthor(isWriter);
            setCanUploadImages(Boolean(currentUserPublicId));

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
            setImages(Array.isArray(data.images) ? data.images : []);
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

  const uploadImages = async (files: FileList | null) => {
    if (!files?.length || uploadingImages) return;
    if (images.length + files.length > 10) { setErrorMsg("사진은 최대 10장까지 등록할 수 있습니다."); return; }
    // 축소가 끝날 때까지 등록 버튼과 파일 칸을 막는다.
    setUploadingImages(true);
    setErrorMsg("");
    const selected = Array.from(files);
    const prepared = await prepareImagesForUpload(selected).catch(() => selected);
    if (prepared.some(isOversizedImage)) {
      setErrorMsg(OVERSIZED_IMAGE_MESSAGE);
      setUploadingImages(false);
      return;
    }
    try {
      for (const file of prepared) {
        const form = new FormData();
        form.append("file", file);
        const response = await csrfFetch(API_ENDPOINTS.images.upload, { method: "POST", body: form });
        if (!response.ok) { const data = await response.json().catch(() => null); throw new Error(data?.message || "사진 업로드에 실패했습니다."); }
        const uploaded: { imageUrl: string } = await response.json();
        setImages((current) => [...current, uploaded.imageUrl]);
      }
    } catch (error) { setErrorMsg(errorText(error, "사진 업로드에 실패했습니다.")); }
    finally { setUploadingImages(false); }
  };

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
          imageUrls: images,
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
    <main className="compose-page community-container content-compose-page">
      <header className="compose-top-bar">
        <div className="compose-head-left">
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

      {categoryCode !== "INQUIRY" && (
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
      )}

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
          <div className="compose-sheet-attach">
            <div className="compose-attach-row">
              <label htmlFor="post-edit-images" className={`compose-attach-btn ${!canUploadImages || uploadingImages || images.length >= 10 ? "disabled" : ""}`}><span className="material-symbols-outlined">image</span><span>{uploadingImages ? "업로드 중..." : "사진 첨부"}</span></label>
              <input id="post-edit-images" type="file" accept="image/jpeg,image/png,image/webp" multiple hidden disabled={!canUploadImages || uploadingImages || images.length >= 10} onChange={(event) => { void uploadImages(event.target.files); event.target.value = ""; }} />
              <span className="compose-attach-hint">{canUploadImages ? `장당 최대 5MB · ${images.length}/10장` : "사진 첨부는 로그인한 회원만 가능합니다."}</span>
            </div>
            <div className="compose-attach-grid">{images.map((url, index) => <div className="compose-attach-preview" key={`${url}-${index}`}><Image src={url} alt={`첨부 이미지 ${index + 1}`} width={320} height={180} unoptimized /><button type="button" className="compose-attach-remove" aria-label={`${index + 1}번째 사진 제거`} onClick={() => setImages((current) => current.filter((_, i) => i !== index))}><span className="material-symbols-outlined" aria-hidden="true">close</span></button></div>)}</div>
          </div>
        </div>

        <footer className="compose-footer-bar">
          <Link href={`/posts/${publicId}`} className="compose-cancel-pill">
            <span className="material-symbols-outlined">arrow_back</span> 취소
          </Link>
          <button type="submit" disabled={submitting || uploadingImages} className="compose-submit-pill">
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
