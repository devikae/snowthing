"use client";

import { use, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { DeleteConfirmModal } from "../../components/DeleteConfirmModal";
import dynamic from "next/dynamic";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS } from "../../lib/api";

const ToastViewer = dynamic(() => import("../../components/ToastViewer"), {
  ssr: false,
  loading: () => <div className="p-6 font-mono text-sm text-gray-400">📖 본문을 불러오는 중입니다...</div>,
});

interface WriterInfo {
  publicId: string | null;
  nickname: string;
  profileImageUrl: string | null;
}

interface PostDetail {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  content: string;
  status: string;
  isAnonymous: boolean;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  dislikeCount: number;
  writer: WriterInfo;
  images: string[];
  createdAt: string;
}

interface CommentItem {
  commentId: number;
  parentId: number | null;
  writerName: string;
  content: string;
  isDeleted: boolean;
  createdAt: string;
  children: CommentItem[];
}

interface CommentListResponse {
  publicId: string;
  totalCommentCount: number;
  comments: CommentItem[];
}

export default function PostDetailPage({ params }: { params: Promise<{ publicId: string }> }) {
  const router = useRouter();
  const { publicId } = use(params);
  const [post, setPost] = useState<PostDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [totalCommentCount, setTotalCommentCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [reactionMsg, setReactionMsg] = useState("");
  const [newCommentText, setNewCommentText] = useState("");
  const [commentAnonPassword, setCommentAnonPassword] = useState("");
  const [submittingComment, setSubmittingComment] = useState(false);
  const [activeReplyParentId, setActiveReplyParentId] = useState<number | null>(null);
  const [replyText, setReplyText] = useState("");
  const [replyAnonPassword, setReplyAnonPassword] = useState("");
  const [currentUserPublicId, setCurrentUserPublicId] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteModalConfig, setDeleteModalConfig] = useState({
    title: "게시글 삭제 확인",
    description: "정말로 이 게시글을 삭제하시겠습니까?",
    requirePassword: false,
  });

  const handleOpenDeleteModal = () => {
    if (isAdmin) {
      setDeleteModalConfig({
        title: "관리자 게시글 삭제",
        description: "관리자 권한으로 이 게시글을 즉시 삭제 처리합니다.",
        requirePassword: false,
      });
    } else if (post?.isAnonymous && (!currentUserPublicId || post.writer?.publicId !== currentUserPublicId)) {
      setDeleteModalConfig({
        title: "익명 게시글 삭제",
        description: "익명 게시글 삭제를 위해 작성 시 설정한 비밀번호를 입력해주세요.",
        requirePassword: true,
      });
    } else {
      setDeleteModalConfig({
        title: "게시글 삭제 확인",
        description: "정말로 이 게시글을 삭제하시겠습니까?",
        requirePassword: false,
      });
    }
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = async (password: string) => {
    const res = await csrfFetch(API_ENDPOINTS.posts.delete(publicId), {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        anonymousPassword: password || null,
      }),
    });

    if (res.ok) {
      setIsDeleteModalOpen(false);
      alert("게시글이 성공적으로 삭제되었습니다.");
      router.push("/posts");
    } else {
      const data = await res.json();
      throw new Error(data.message || "게시글 삭제 처리에 실패했습니다.");
    }
  };

  const fetchComments = useCallback(async () => {
    try {
      const res = await fetch(API_ENDPOINTS.posts.comments(publicId), { credentials: "include" });
      if (res.ok) {
        const data: CommentListResponse = await res.json();
        setComments(data.comments || []);
        setTotalCommentCount(data.totalCommentCount || 0);
      }
    } catch (error) {
      console.error("댓글 로드 실패:", error);
    }
  }, [publicId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          // 현재 로그인 유저 프로필 조회
          try {
            const meRes = await fetch(API_ENDPOINTS.members.me, { credentials: "include" });
            if (meRes.ok) {
              const meData = await meRes.json();
              setCurrentUserPublicId(meData.publicId || null);
              setIsAdmin(meData.role === "ROLE_ADMIN");
            }
          } catch {
            setCurrentUserPublicId(null);
            setIsAdmin(false);
          }

          const res = await fetch(API_ENDPOINTS.posts.detail(publicId), { credentials: "include" });
          if (res.ok) {
            const data: PostDetail = await res.json();
            setPost(data);
          } else {
            setErrorMsg("게시글을 찾을 수 없거나 삭제되었습니다.");
          }
          await fetchComments();
        } catch {
          setErrorMsg("게시글 로드에 실패했습니다.");
        } finally {
          setLoading(false);
        }
      })();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchComments, publicId]);

  const handleReaction = async (type: "LIKE" | "DISLIKE") => {
    try {
      const res = await csrfFetch(API_ENDPOINTS.posts.reactions(publicId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type }),
      });

      if (res.ok) {
        const data = await res.json();
        setReactionMsg(data.message);
        setPost((current) => {
          if (!current) return current;
          return {
            ...current,
            likeCount: data.likeCount,
            dislikeCount: data.dislikeCount,
          };
        });
        return;
      }

      const errorData = await res.json();
      setReactionMsg(errorData.message || "투표 처리 실패");
    } catch {
      setReactionMsg("서버 통신 중 오류가 발생했습니다.");
    }
  };

  const isAnonymousPost = Boolean(post?.isAnonymous || post?.categoryCode === "ANONYMOUS");

  const handleCreateComment = async (parentId: number | null) => {
    const text = parentId ? replyText : newCommentText;
    if (!text.trim()) {
      alert("댓글 내용을 입력해주세요.");
      return;
    }

    let isAnonymous = false;
    let anonymousPassword: string | null = null;

    if (isAnonymousPost) {
      isAnonymous = true;
      if (!currentUserPublicId) {
        const pwd = parentId ? replyAnonPassword : commentAnonPassword;
        if (!pwd.trim()) {
          alert("익명 댓글 삭제를 위한 비밀번호를 입력해주세요.");
          return;
        }
        anonymousPassword = pwd.trim();
      }
    } else {
      if (!currentUserPublicId) {
        if (confirm("댓글을 작성하려면 로그인이 필요합니다. 로그인 페이지로 이동하시겠습니까?")) {
          router.push(`/login?redirect=${encodeURIComponent(window.location.pathname)}`);
        }
        return;
      }
      isAnonymous = false;
      anonymousPassword = null;
    }

    setSubmittingComment(true);
    try {
      const res = await csrfFetch(API_ENDPOINTS.posts.comments(publicId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          parentId,
          content: text.trim(),
          isAnonymous,
          anonymousPassword,
        }),
      });

      if (res.ok) {
        if (parentId) {
          setReplyText("");
          setReplyAnonPassword("");
          setActiveReplyParentId(null);
        } else {
          setNewCommentText("");
          setCommentAnonPassword("");
        }
        await fetchComments();
        setPost((current) => (current ? { ...current, commentCount: current.commentCount + 1 } : current));
        return;
      }

      const errorData = await res.json();
      alert(`댓글 작성 실패: ${errorData.message || "요청을 처리하지 못했습니다."}`);
    } catch {
      alert("서버 통신 중 오류가 발생했습니다.");
    } finally {
      setSubmittingComment(false);
    }
  };

  const handleDeleteComment = async (commentId: number, isAnonymousWriter: boolean) => {
    let anonymousPassword = "";
    if (isAnonymousWriter) {
      const input = prompt("익명 댓글 삭제 비밀번호를 입력하세요.");
      if (!input) return;
      anonymousPassword = input;
    } else if (!confirm("댓글을 삭제하시겠습니까?")) {
      return;
    }

    try {
      const res = await csrfFetch(API_ENDPOINTS.comments.delete(commentId), {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          anonymousPassword: anonymousPassword || null,
        }),
      });
      if (res.ok) {
        await fetchComments();
        setPost((current) => (current ? { ...current, commentCount: Math.max(0, current.commentCount - 1) } : current));
        return;
      }

      const errorData = await res.json();
      alert(`삭제 실패: ${errorData.message || "요청을 처리하지 못했습니다."}`);
    } catch {
      alert("서버 통신 중 오류가 발생했습니다.");
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[var(--snow-background)]">
        <TopNav active="posts" />
        <div className="p-12 text-center text-sm text-[var(--snow-muted)]">로딩 중입니다.</div>
      </div>
    );
  }

  if (errorMsg || !post) {
    return (
      <div className="min-h-screen bg-[var(--snow-background)]">
        <TopNav active="posts" />
        <main className="snow-container px-5 py-12 text-center">
          <p className="text-[var(--snow-error)]">{errorMsg || "게시글이 존재하지 않습니다."}</p>
          <Link href="/posts" className="snow-btn-secondary mt-5">
            목록으로 돌아가기
          </Link>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="posts" />
      <main className="snow-container px-5 py-8 lg:px-8 lg:py-10">
        <div className="mx-auto max-w-4xl">
          <article className="snow-card bg-white p-6 md:p-8">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <span className="snow-chip snow-chip-dark">{post.categoryName}</span>
              <span className="font-mono text-xs text-[var(--snow-muted)]">{new Date(post.createdAt).toLocaleString()}</span>
            </div>

            <h1 className="text-4xl font-extrabold leading-tight text-black">{post.title}</h1>

            <div className="mt-6 flex flex-wrap items-center gap-4 border-y border-[var(--snow-border)] py-4 font-mono text-xs text-[var(--snow-muted)]">
              <span>
                작성자 <strong className="text-black">{post.writer.nickname}</strong>
              </span>
              <span>조회 {post.viewCount}</span>
              <span>댓글 {post.commentCount}</span>
              <span>추천 {post.likeCount}</span>
              <span>비추천 {post.dislikeCount}</span>
              {(() => {
                const canEdit = Boolean(post.isAnonymous || post.categoryCode === "ANONYMOUS" || (currentUserPublicId && post.writer?.publicId === currentUserPublicId));
                const canDelete = Boolean(isAdmin || canEdit);
                if (!canEdit && !canDelete) return null;

                return (
                  <div className="ml-auto flex items-center gap-3 font-bold">
                    {canEdit && (
                      <Link href={`/posts/${publicId}/edit`} className="text-black hover:underline">
                        수정
                      </Link>
                    )}
                    {canDelete && (
                      <button onClick={handleOpenDeleteModal} className="text-[#dc2626] hover:underline">
                        삭제
                      </button>
                    )}
                  </div>
                );
              })()}
            </div>

            <div className="mt-8 border-t border-b border-[var(--snow-border)] py-6">
              <ToastViewer content={post.content} />
            </div>

            {post.images?.length > 0 && (
              <div className="mt-8 grid gap-4">
                {post.images.map((imageUrl) => (
                  <img key={imageUrl} src={imageUrl} alt="첨부 이미지" className="w-full rounded border border-[var(--snow-border)] object-cover grayscale" />
                ))}
              </div>
            )}

            <div className="mt-8 flex flex-col items-center gap-3 border-t border-[var(--snow-border)] pt-6">
              <div className="flex flex-wrap justify-center gap-3">
                <button onClick={() => handleReaction("LIKE")} className="snow-btn-secondary">
                  <span className="material-symbols-outlined text-[17px]">thumb_up</span>
                  추천 {post.likeCount}
                </button>
                <button onClick={() => handleReaction("DISLIKE")} className="snow-btn-secondary">
                  <span className="material-symbols-outlined text-[17px]">thumb_down</span>
                  비추천 {post.dislikeCount}
                </button>
              </div>
              {reactionMsg && <p className="text-sm font-semibold text-[var(--snow-muted)]">{reactionMsg}</p>}
            </div>
          </article>

          <section className="snow-card mt-8 bg-white p-6 md:p-8">
            <h2 className="mb-6 flex items-center gap-2 text-2xl font-extrabold text-black">
              <span className="material-symbols-outlined">chat_bubble</span>
              댓글 {totalCommentCount}
            </h2>

            <div className="mb-7 border-b border-[var(--snow-border)] pb-7">
              <textarea
                rows={3}
                placeholder={isAnonymousPost ? "익명으로 댓글을 작성해보세요." : currentUserPublicId ? "댓글을 작성해보세요." : "로그인 후 댓글을 작성할 수 있습니다."}
                value={newCommentText}
                onChange={(event) => setNewCommentText(event.target.value)}
                className="snow-textarea min-h-[110px]"
              />
              <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                {isAnonymousPost ? (
                  <div className="flex items-center gap-2">
                    <span className="snow-chip snow-chip-dark text-xs">익명</span>
                    {!currentUserPublicId && (
                      <input
                        type="password"
                        placeholder="익명 비밀번호 입력"
                        value={commentAnonPassword}
                        onChange={(event) => setCommentAnonPassword(event.target.value)}
                        className="snow-input sm:w-52"
                      />
                    )}
                  </div>
                ) : (
                  <div>
                    {!currentUserPublicId && (
                      <span className="text-xs text-[var(--snow-muted)]">
                        * 댓글 작성을 위해 로그인이 필요합니다.
                      </span>
                    )}
                  </div>
                )}
                <button disabled={submittingComment} onClick={() => void handleCreateComment(null)} className="snow-btn-primary sm:ml-auto">
                  댓글 등록
                </button>
              </div>
            </div>

            <div className="grid gap-4">
              {comments.length === 0 ? (
                <p className="py-8 text-center text-sm text-[var(--snow-muted)]">첫 댓글을 작성해보세요.</p>
              ) : (
                comments.map((comment) => (
                  <CommentRow
                    key={comment.commentId}
                    item={comment}
                    isAnonymousPost={isAnonymousPost}
                    currentUserPublicId={currentUserPublicId}
                    activeReplyParentId={activeReplyParentId}
                    setActiveReplyParentId={setActiveReplyParentId}
                    replyText={replyText}
                    setReplyText={setReplyText}
                    replyAnonPassword={replyAnonPassword}
                    setReplyAnonPassword={setReplyAnonPassword}
                    handleCreateComment={handleCreateComment}
                    handleDeleteComment={handleDeleteComment}
                  />
                ))
              )}
            </div>
          </section>
        </div>
      </main>

      <DeleteConfirmModal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleConfirmDelete}
        title={deleteModalConfig.title}
        description={deleteModalConfig.description}
        requirePassword={deleteModalConfig.requirePassword}
      />
      <Footer />
    </div>
  );
}

function CommentRow({
  item,
  depth = 0,
  isAnonymousPost,
  currentUserPublicId,
  activeReplyParentId,
  setActiveReplyParentId,
  replyText,
  setReplyText,
  replyAnonPassword,
  setReplyAnonPassword,
  handleCreateComment,
  handleDeleteComment,
}: {
  item: CommentItem;
  depth?: number;
  isAnonymousPost: boolean;
  currentUserPublicId: string | null;
  activeReplyParentId: number | null;
  setActiveReplyParentId: (id: number | null) => void;
  replyText: string;
  setReplyText: (text: string) => void;
  replyAnonPassword: string;
  setReplyAnonPassword: (value: string) => void;
  handleCreateComment: (parentId: number | null) => Promise<void>;
  handleDeleteComment: (commentId: number, isAnonymousWriter: boolean) => Promise<void>;
}) {
  return (
    <div className={`${depth > 0 ? "ml-5 border-l-2 border-black pl-5" : ""}`}>
      <div className="border-b border-[var(--snow-border)] pb-4">
        <div className="flex items-center justify-between gap-3">
          <span className={`font-bold ${item.isDeleted ? "text-[var(--snow-faint)]" : "text-black"}`}>{item.writerName}</span>
          <span className="font-mono text-xs text-[var(--snow-muted)]">{new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
        </div>
        <p className={`mt-2 leading-7 ${item.isDeleted ? "text-[var(--snow-faint)] italic" : "text-[var(--snow-ink-soft)]"}`}>{item.content}</p>

        {!item.isDeleted && (
          <div className="mt-3 flex gap-4 font-mono text-xs font-bold uppercase tracking-[0.06em]">
            <button onClick={() => setActiveReplyParentId(activeReplyParentId === item.commentId ? null : item.commentId)} className="text-black">
              {activeReplyParentId === item.commentId ? "답글 취소" : "답글 쓰기"}
            </button>
            <button onClick={() => void handleDeleteComment(item.commentId, item.writerName.includes("익명"))} className="text-[var(--snow-error)]">
              삭제
            </button>
          </div>
        )}

        {activeReplyParentId === item.commentId && (
          <div className="mt-4 rounded border border-[var(--snow-border)] bg-[var(--snow-background)] p-4">
            <textarea
              rows={2}
              value={replyText}
              onChange={(event) => setReplyText(event.target.value)}
              placeholder={isAnonymousPost ? "익명으로 답글을 작성하세요." : currentUserPublicId ? "답글을 작성하세요." : "로그인 후 답글을 작성할 수 있습니다."}
              className="snow-textarea min-h-[90px]"
            />
            <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              {isAnonymousPost ? (
                <div className="flex items-center gap-2">
                  <span className="snow-chip snow-chip-dark text-xs">익명</span>
                  {!currentUserPublicId && (
                    <input
                      type="password"
                      placeholder="익명 비밀번호"
                      value={replyAnonPassword}
                      onChange={(event) => setReplyAnonPassword(event.target.value)}
                      className="snow-input sm:w-44"
                    />
                  )}
                </div>
              ) : (
                <div>
                  {!currentUserPublicId && (
                    <span className="text-xs text-[var(--snow-muted)]">
                      * 답글 작성을 위해 로그인이 필요합니다.
                    </span>
                  )}
                </div>
              )}
              <button onClick={() => void handleCreateComment(item.commentId)} className="snow-btn-primary sm:ml-auto">
                답글 등록
              </button>
            </div>
          </div>
        )}
      </div>

      {item.children?.length > 0 && (
        <div className="mt-4 grid gap-4">
          {item.children.map((child) => (
            <CommentRow
              key={child.commentId}
              item={child}
              depth={depth + 1}
              isAnonymousPost={isAnonymousPost}
              currentUserPublicId={currentUserPublicId}
              activeReplyParentId={activeReplyParentId}
              setActiveReplyParentId={setActiveReplyParentId}
              replyText={replyText}
              setReplyText={setReplyText}
              replyAnonPassword={replyAnonPassword}
              setReplyAnonPassword={setReplyAnonPassword}
              handleCreateComment={handleCreateComment}
              handleDeleteComment={handleDeleteComment}
            />
          ))}
        </div>
      )}
    </div>
  );
}
