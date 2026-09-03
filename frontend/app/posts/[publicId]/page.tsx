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
  writer: WriterInfo | null;
  isAnonymous: boolean;
  writerIp: string;
  content: string;
  isDeleted: boolean;
  replyCount: number;
  previewReplies: CommentItem[];
  hasMoreReplies: boolean;
  createdAt: string;
}

interface CommentListResponse {
  publicId: string;
  totalCommentCount: number;
  comments: CommentItem[];
  nextCursor: number | null;
  hasNext: boolean;
}

interface CommentReplyListResponse {
  rootCommentId: number;
  totalReplyCount: number;
  replies: CommentItem[];
  nextCursor: number | null;
  hasNext: boolean;
}

interface ReplyPagingState {
  nextCursor: number | null;
  hasNext: boolean;
  loading: boolean;
}

interface CommentUpdateResponse {
  commentId: number;
  content: string;
  updatedAt: string;
}

export default function PostDetailPage({ params }: { params: Promise<{ publicId: string }> }) {
  const router = useRouter();
  const { publicId } = use(params);
  const [post, setPost] = useState<PostDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [totalCommentCount, setTotalCommentCount] = useState(0);
  const [commentNextCursor, setCommentNextCursor] = useState<number | null>(null);
  const [hasNextComments, setHasNextComments] = useState(false);
  const [isLoadingMoreComments, setIsLoadingMoreComments] = useState(false);
  const [replyPagingByRootId, setReplyPagingByRootId] = useState<Record<number, ReplyPagingState>>({});
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [reactionMsg, setReactionMsg] = useState("");
  const [newCommentText, setNewCommentText] = useState("");
  const [commentAnonPassword, setCommentAnonPassword] = useState("");
  const [submittingComment, setSubmittingComment] = useState(false);
  const [activeReplyParentId, setActiveReplyParentId] = useState<number | null>(null);
  const [replyMentionName, setReplyMentionName] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const [replyAnonPassword, setReplyAnonPassword] = useState("");
  const [activeEditCommentId, setActiveEditCommentId] = useState<number | null>(null);
  const [editCommentText, setEditCommentText] = useState("");
  const [editCommentPassword, setEditCommentPassword] = useState("");
  const [editCommentError, setEditCommentError] = useState("");
  const [submittingEditComment, setSubmittingEditComment] = useState(false);
  const [activeDeleteCommentId, setActiveDeleteCommentId] = useState<number | null>(null);
  const [deleteCommentPassword, setDeleteCommentPassword] = useState("");
  const [deleteCommentError, setDeleteCommentError] = useState("");
  const [submittingDeleteComment, setSubmittingDeleteComment] = useState(false);
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

  const fetchComments = useCallback(async (cursor: number | null = null, append = false) => {
    try {
      const res = await fetch(API_ENDPOINTS.posts.comments(publicId, cursor), { credentials: "include" });
      if (res.ok) {
        const data: CommentListResponse = await res.json();
        setComments((current) => {
          if (!append) return data.comments || [];
          const merged = [...current, ...(data.comments || [])];
          return merged.filter(
            (comment, index) => merged.findIndex((candidate) => candidate.commentId === comment.commentId) === index,
          );
        });
        setTotalCommentCount(data.totalCommentCount || 0);
        setCommentNextCursor(data.nextCursor ?? null);
        setHasNextComments(Boolean(data.hasNext));
      }
    } catch (error) {
      console.error("댓글 로드 실패:", error);
    }
  }, [publicId]);

  const handleLoadMoreComments = async () => {
    if (isLoadingMoreComments || !hasNextComments || commentNextCursor == null) return;

    setIsLoadingMoreComments(true);
    try {
      await fetchComments(commentNextCursor, true);
    } finally {
      setIsLoadingMoreComments(false);
    }
  };

  const handleLoadMoreReplies = async (rootCommentId: number) => {
    const root = comments.find((comment) => comment.commentId === rootCommentId);
    if (!root) return;

    const paging = replyPagingByRootId[rootCommentId];
    if (paging?.loading) return;

    const cursor = paging?.nextCursor ?? root.previewReplies.at(-1)?.commentId ?? null;
    setReplyPagingByRootId((current) => ({
      ...current,
      [rootCommentId]: {
        nextCursor: cursor,
        hasNext: paging?.hasNext ?? root.hasMoreReplies,
        loading: true,
      },
    }));

    try {
      const res = await fetch(API_ENDPOINTS.comments.replies(rootCommentId, cursor), {
        credentials: "include",
      });
      if (!res.ok) throw new Error("답글을 불러오지 못했습니다.");

      const data: CommentReplyListResponse = await res.json();
      setComments((current) =>
        current.map((comment) => {
          if (comment.commentId !== rootCommentId) return comment;
          const merged = [...comment.previewReplies, ...(data.replies || [])];
          return {
            ...comment,
            replyCount: data.totalReplyCount,
            previewReplies: merged.filter(
              (reply, index) => merged.findIndex((candidate) => candidate.commentId === reply.commentId) === index,
            ),
            hasMoreReplies: data.hasNext,
          };
        }),
      );
      setReplyPagingByRootId((current) => ({
        ...current,
        [rootCommentId]: {
          nextCursor: data.nextCursor ?? null,
          hasNext: data.hasNext,
          loading: false,
        },
      }));
    } catch (error) {
      console.error("답글 로드 실패:", error);
      setReplyPagingByRootId((current) => ({
        ...current,
        [rootCommentId]: {
          nextCursor: current[rootCommentId]?.nextCursor ?? cursor,
          hasNext: current[rootCommentId]?.hasNext ?? root.hasMoreReplies,
          loading: false,
        },
      }));
    }
  };

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
        const createdComment: CommentItem = await res.json();
        if (parentId) {
          setReplyText("");
          setReplyAnonPassword("");
          setActiveReplyParentId(null);
          setReplyMentionName(null);
          setComments((current) =>
            current.map((comment) => {
              if (comment.commentId !== parentId) return comment;
              return {
                ...comment,
                replyCount: comment.replyCount + 1,
                previewReplies: comment.hasMoreReplies
                  ? comment.previewReplies
                  : [...comment.previewReplies, createdComment],
              };
            }),
          );
          setTotalCommentCount((current) => current + 1);
        } else {
          setNewCommentText("");
          setCommentAnonPassword("");
          if (hasNextComments) {
            await fetchComments();
          } else {
            setComments((current) => [...current, createdComment]);
            setTotalCommentCount((current) => current + 1);
          }
        }
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

  const handleStartEditComment = (comment: CommentItem) => {
    setActiveReplyParentId(null);
    setReplyMentionName(null);
    setActiveEditCommentId(comment.commentId);
    setEditCommentText(comment.content);
    setEditCommentPassword("");
    setEditCommentError("");
  };

  const handleCancelEditComment = () => {
    if (submittingEditComment) return;
    setActiveEditCommentId(null);
    setEditCommentText("");
    setEditCommentPassword("");
    setEditCommentError("");
  };

  const handleUpdateComment = async (comment: CommentItem) => {
    const content = editCommentText.trim();
    const requiresPassword = comment.isAnonymous && !currentUserPublicId;
    if (!content) {
      setEditCommentError("댓글 내용을 입력해주세요.");
      return;
    }
    if (content.length > 1000) {
      setEditCommentError("댓글은 1,000자 이하로 입력해주세요.");
      return;
    }
    if (requiresPassword && !editCommentPassword.trim()) {
      setEditCommentError("익명 댓글 비밀번호를 입력해주세요.");
      return;
    }
    if (content === comment.content) {
      setEditCommentError("변경된 내용이 없습니다.");
      return;
    }

    setSubmittingEditComment(true);
    setEditCommentError("");
    try {
      const res = await csrfFetch(API_ENDPOINTS.comments.delete(comment.commentId), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          content,
          anonymousPassword: editCommentPassword.trim() || null,
        }),
      });
      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || "댓글 수정에 실패했습니다.");
      }

      const updated: CommentUpdateResponse = await res.json();
      setComments((current) => updateCommentContent(current, updated.commentId, updated.content));
      setActiveEditCommentId(null);
      setEditCommentText("");
      setEditCommentPassword("");
      setEditCommentError("");
    } catch (error) {
      setEditCommentError(error instanceof Error ? error.message : "서버 통신 중 오류가 발생했습니다.");
    } finally {
      setSubmittingEditComment(false);
    }
  };

  const handleStartDeleteComment = (commentId: number) => {
    setActiveDeleteCommentId(commentId);
    setDeleteCommentPassword("");
    setDeleteCommentError("");
  };

  const handleCancelDeleteComment = () => {
    setActiveDeleteCommentId(null);
    setDeleteCommentPassword("");
    setDeleteCommentError("");
  };

  const handleConfirmDeleteComment = async (comment: CommentItem) => {
    const isOwnerMember = !comment.isAnonymous && currentUserPublicId && comment.writer?.publicId === currentUserPublicId;
    const isOwnerAnonMember = comment.isAnonymous && currentUserPublicId && comment.writer?.publicId === currentUserPublicId;
    const requiresPassword = !isAdmin && !isOwnerMember && !isOwnerAnonMember;

    if (requiresPassword && !deleteCommentPassword.trim()) {
      setDeleteCommentError("비밀번호를 입력해주세요.");
      return;
    }

    setSubmittingDeleteComment(true);
    setDeleteCommentError("");
    try {
      const res = await csrfFetch(API_ENDPOINTS.comments.delete(comment.commentId), {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ anonymousPassword: deleteCommentPassword.trim() || null }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || "댓글 삭제에 실패했습니다.");
      }

      handleCancelDeleteComment();
      await fetchComments();
      setPost((current) => (current ? { ...current, commentCount: Math.max(0, current.commentCount - 1) } : current));
    } catch (error) {
      setDeleteCommentError(error instanceof Error ? error.message : "서버 통신 중 오류가 발생했습니다.");
    } finally {
      setSubmittingDeleteComment(false);
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
                    replyMentionName={replyMentionName}
                    setReplyMentionName={setReplyMentionName}
                    replyText={replyText}
                    setReplyText={setReplyText}
                    replyAnonPassword={replyAnonPassword}
                    setReplyAnonPassword={setReplyAnonPassword}
                    handleCreateComment={handleCreateComment}
                    activeEditCommentId={activeEditCommentId}
                    editCommentText={editCommentText}
                    setEditCommentText={setEditCommentText}
                    editCommentPassword={editCommentPassword}
                    setEditCommentPassword={setEditCommentPassword}
                    editCommentError={editCommentError}
                    submittingEditComment={submittingEditComment}
                    handleStartEditComment={handleStartEditComment}
                    handleCancelEditComment={handleCancelEditComment}
                    handleUpdateComment={handleUpdateComment}
                    activeDeleteCommentId={activeDeleteCommentId}
                    deleteCommentPassword={deleteCommentPassword}
                    setDeleteCommentPassword={setDeleteCommentPassword}
                    deleteCommentError={deleteCommentError}
                    submittingDeleteComment={submittingDeleteComment}
                    handleStartDeleteComment={handleStartDeleteComment}
                    handleCancelDeleteComment={handleCancelDeleteComment}
                    handleConfirmDeleteComment={handleConfirmDeleteComment}
                    isAdmin={isAdmin}
                    handleLoadMoreReplies={handleLoadMoreReplies}
                    isLoadingReplies={Boolean(replyPagingByRootId[comment.commentId]?.loading)}
                  />
                ))
              )}
            </div>

            {hasNextComments && (
              <button
                type="button"
                disabled={isLoadingMoreComments}
                onClick={() => void handleLoadMoreComments()}
                className="snow-btn-secondary mt-6 w-full"
              >
                {isLoadingMoreComments ? "댓글을 불러오는 중..." : "댓글 더보기 (20개)"}
              </button>
            )}
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
  isAnonymousPost,
  currentUserPublicId,
  activeReplyParentId,
  setActiveReplyParentId,
  replyMentionName,
  setReplyMentionName,
  replyText,
  setReplyText,
  replyAnonPassword,
  setReplyAnonPassword,
  handleCreateComment,
  activeEditCommentId,
  editCommentText,
  setEditCommentText,
  editCommentPassword,
  setEditCommentPassword,
  editCommentError,
  submittingEditComment,
  handleStartEditComment,
  handleCancelEditComment,
  handleUpdateComment,
  activeDeleteCommentId,
  deleteCommentPassword,
  setDeleteCommentPassword,
  deleteCommentError,
  submittingDeleteComment,
  handleStartDeleteComment,
  handleCancelDeleteComment,
  handleConfirmDeleteComment,
  isAdmin,
  handleLoadMoreReplies,
  isLoadingReplies,
}: {
  item: CommentItem;
  isAnonymousPost: boolean;
  currentUserPublicId: string | null;
  activeReplyParentId: number | null;
  setActiveReplyParentId: (id: number | null) => void;
  replyMentionName: string | null;
  setReplyMentionName: (name: string | null) => void;
  replyText: string;
  setReplyText: (text: string) => void;
  replyAnonPassword: string;
  setReplyAnonPassword: (value: string) => void;
  handleCreateComment: (parentId: number | null) => Promise<void>;
  activeEditCommentId: number | null;
  editCommentText: string;
  setEditCommentText: (text: string) => void;
  editCommentPassword: string;
  setEditCommentPassword: (password: string) => void;
  editCommentError: string;
  submittingEditComment: boolean;
  handleStartEditComment: (comment: CommentItem) => void;
  handleCancelEditComment: () => void;
  handleUpdateComment: (comment: CommentItem) => Promise<void>;
  activeDeleteCommentId: number | null;
  deleteCommentPassword: string;
  setDeleteCommentPassword: (password: string) => void;
  deleteCommentError: string;
  submittingDeleteComment: boolean;
  handleStartDeleteComment: (commentId: number) => void;
  handleCancelDeleteComment: () => void;
  handleConfirmDeleteComment: (comment: CommentItem) => Promise<void>;
  isAdmin: boolean;
  handleLoadMoreReplies: (rootCommentId: number) => Promise<void>;
  isLoadingReplies: boolean;
}) {
  const canEdit = canEditComment(item, currentUserPublicId);
  const canDelete = canDeleteComment(item, currentUserPublicId, isAdmin);
  const isEditing = activeEditCommentId === item.commentId;
  const openReplyEditor = (target: CommentItem) => {
    if (activeReplyParentId === item.commentId && replyMentionName === getWriterName(target)) {
      setActiveReplyParentId(null);
      setReplyMentionName(null);
      return;
    }
    setActiveReplyParentId(item.commentId);
    setReplyMentionName(getWriterName(target));
  };

  return (
    <div>
      <div className="border-b border-[var(--snow-border)] pb-4">
        <div className="flex items-center justify-between gap-3">
          <span className={`font-bold ${item.isDeleted ? "text-[var(--snow-faint)]" : "text-black"}`}>{getWriterName(item)}</span>
          <div className="flex items-center">
            <span className="font-mono text-xs text-[var(--snow-muted)]">{formatCommentDate(item.createdAt)}</span>
            {canDelete && (
              <CommentDeleteInline
                comment={item}
                currentUserPublicId={currentUserPublicId}
                isAdmin={isAdmin}
                isActive={activeDeleteCommentId === item.commentId}
                onOpen={() => handleStartDeleteComment(item.commentId)}
                onClose={handleCancelDeleteComment}
                password={deleteCommentPassword}
                setPassword={setDeleteCommentPassword}
                error={activeDeleteCommentId === item.commentId ? deleteCommentError : ""}
                submitting={submittingDeleteComment}
                onConfirm={() => void handleConfirmDeleteComment(item)}
              />
            )}
          </div>
        </div>
        {isEditing ? (
          <CommentEditForm
            comment={item}
            content={editCommentText}
            setContent={setEditCommentText}
            password={editCommentPassword}
            setPassword={setEditCommentPassword}
            error={editCommentError}
            submitting={submittingEditComment}
            requiresPassword={item.isAnonymous && !currentUserPublicId}
            onCancel={handleCancelEditComment}
            onSubmit={() => void handleUpdateComment(item)}
          />
        ) : (
          <>
            <p className={`mt-2 leading-7 ${item.isDeleted ? "text-[var(--snow-faint)] italic" : "text-[var(--snow-ink-soft)]"}`}>{item.content}</p>
            <div className="mt-3 flex gap-4 font-mono text-xs font-bold uppercase tracking-[0.06em]">
              <button onClick={() => openReplyEditor(item)} className="text-black">
                {activeReplyParentId === item.commentId ? "답글 취소" : "답글 쓰기"}
              </button>
              {canEdit && (
                <button type="button" onClick={() => handleStartEditComment(item)} className="text-black">
                  수정
                </button>
              )}
            </div>
          </>
        )}

        {item.previewReplies.length > 0 && (
          <div className="mt-4 grid gap-4">
            {item.previewReplies.map((reply) => (
              <ReplyRow
                key={reply.commentId}
                item={reply}
                onReply={() => openReplyEditor(reply)}
                isEditing={activeEditCommentId === reply.commentId}
                editCommentText={editCommentText}
                setEditCommentText={setEditCommentText}
                editCommentPassword={editCommentPassword}
                setEditCommentPassword={setEditCommentPassword}
                editCommentError={editCommentError}
                submittingEditComment={submittingEditComment}
                handleStartEditComment={handleStartEditComment}
                handleCancelEditComment={handleCancelEditComment}
                handleUpdateComment={handleUpdateComment}
                currentUserPublicId={currentUserPublicId}
                activeDeleteCommentId={activeDeleteCommentId}
                deleteCommentPassword={deleteCommentPassword}
                setDeleteCommentPassword={setDeleteCommentPassword}
                deleteCommentError={deleteCommentError}
                submittingDeleteComment={submittingDeleteComment}
                handleStartDeleteComment={handleStartDeleteComment}
                handleCancelDeleteComment={handleCancelDeleteComment}
                handleConfirmDeleteComment={handleConfirmDeleteComment}
                isAdmin={isAdmin}
              />
            ))}
          </div>
        )}

        {item.hasMoreReplies && (
          <div className="mt-3 flex gap-4 font-mono text-xs font-bold uppercase tracking-[0.06em]">
            <button
              type="button"
              disabled={isLoadingReplies}
              onClick={() => void handleLoadMoreReplies(item.commentId)}
              className="text-black disabled:text-[var(--snow-muted)]"
            >
              {isLoadingReplies ? "답글을 불러오는 중..." : `답글 더보기 (총 ${item.replyCount}개)`}
            </button>
          </div>
        )}

        {activeReplyParentId === item.commentId && (
          <div className="mt-4 rounded border border-[var(--snow-border)] bg-[var(--snow-background)] p-4">
            {replyMentionName && (
              <p className="mb-2 text-xs font-bold text-[var(--snow-muted)]">@{replyMentionName} 님에게 답글</p>
            )}
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
    </div>
  );
}

function ReplyRow({
  item,
  onReply,
  currentUserPublicId,
  isEditing,
  editCommentText,
  setEditCommentText,
  editCommentPassword,
  setEditCommentPassword,
  editCommentError,
  submittingEditComment,
  handleStartEditComment,
  handleCancelEditComment,
  handleUpdateComment,
  activeDeleteCommentId,
  deleteCommentPassword,
  setDeleteCommentPassword,
  deleteCommentError,
  submittingDeleteComment,
  handleStartDeleteComment,
  handleCancelDeleteComment,
  handleConfirmDeleteComment,
  isAdmin,
}: {
  item: CommentItem;
  onReply: () => void;
  currentUserPublicId: string | null;
  isEditing: boolean;
  editCommentText: string;
  setEditCommentText: (text: string) => void;
  editCommentPassword: string;
  setEditCommentPassword: (password: string) => void;
  editCommentError: string;
  submittingEditComment: boolean;
  handleStartEditComment: (comment: CommentItem) => void;
  handleCancelEditComment: () => void;
  handleUpdateComment: (comment: CommentItem) => Promise<void>;
  activeDeleteCommentId: number | null;
  deleteCommentPassword: string;
  setDeleteCommentPassword: (password: string) => void;
  deleteCommentError: string;
  submittingDeleteComment: boolean;
  handleStartDeleteComment: (commentId: number) => void;
  handleCancelDeleteComment: () => void;
  handleConfirmDeleteComment: (comment: CommentItem) => Promise<void>;
  isAdmin: boolean;
}) {
  const canEdit = canEditComment(item, currentUserPublicId);
  const canDelete = canDeleteComment(item, currentUserPublicId, isAdmin);

  return (
    <div className="ml-5 border-l-2 border-black pl-5">
      <div className="flex items-center justify-between gap-3">
        <span className={`font-bold ${item.isDeleted ? "text-[var(--snow-faint)]" : "text-black"}`}>{getWriterName(item)}</span>
        <div className="flex items-center">
          <span className="font-mono text-xs text-[var(--snow-muted)]">
            {formatCommentDate(item.createdAt)}
          </span>
          {canDelete && (
            <CommentDeleteInline
              comment={item}
              currentUserPublicId={currentUserPublicId}
              isAdmin={isAdmin}
              isActive={activeDeleteCommentId === item.commentId}
              onOpen={() => handleStartDeleteComment(item.commentId)}
              onClose={handleCancelDeleteComment}
              password={deleteCommentPassword}
              setPassword={setDeleteCommentPassword}
              error={activeDeleteCommentId === item.commentId ? deleteCommentError : ""}
              submitting={submittingDeleteComment}
              onConfirm={() => void handleConfirmDeleteComment(item)}
            />
          )}
        </div>
      </div>
      {isEditing ? (
        <CommentEditForm
          comment={item}
          content={editCommentText}
          setContent={setEditCommentText}
          password={editCommentPassword}
          setPassword={setEditCommentPassword}
          error={editCommentError}
          submitting={submittingEditComment}
          requiresPassword={item.isAnonymous && !currentUserPublicId}
          onCancel={handleCancelEditComment}
          onSubmit={() => void handleUpdateComment(item)}
        />
      ) : (
        <p className={`mt-2 leading-7 ${item.isDeleted ? "text-[var(--snow-faint)] italic" : "text-[var(--snow-ink-soft)]"}`}>{item.content}</p>
      )}
      {!item.isDeleted && !isEditing && (
        <div className="mt-3 flex gap-4 font-mono text-xs font-bold uppercase tracking-[0.06em]">
          <button type="button" onClick={onReply} className="text-black">
            답글 쓰기
          </button>
          {canEdit && (
            <button type="button" onClick={() => handleStartEditComment(item)} className="text-black">
              수정
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function CommentEditForm({
  comment,
  content,
  setContent,
  password,
  setPassword,
  error,
  submitting,
  requiresPassword,
  onCancel,
  onSubmit,
}: {
  comment: CommentItem;
  content: string;
  setContent: (content: string) => void;
  password: string;
  setPassword: (password: string) => void;
  error: string;
  submitting: boolean;
  requiresPassword: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  const contentId = `comment-edit-content-${comment.commentId}`;
  const passwordId = `comment-edit-password-${comment.commentId}`;

  return (
    <div className="mt-3 rounded border border-[var(--snow-border)] bg-[var(--snow-background)] p-4">
      <label htmlFor={contentId} className="font-mono text-xs font-bold text-[var(--snow-muted)]">
        댓글 내용
      </label>
      <textarea
        id={contentId}
        rows={3}
        maxLength={1000}
        value={content}
        disabled={submitting}
        onChange={(event) => setContent(event.target.value)}
        className="snow-textarea mt-2 min-h-[100px]"
      />
      <div className="mt-1 text-right font-mono text-xs text-[var(--snow-muted)]">{content.length}/1000</div>
      {requiresPassword && (
        <div className="mt-3">
          <label htmlFor={passwordId} className="font-mono text-xs font-bold text-[var(--snow-muted)]">
            익명 비밀번호
          </label>
          <input
            id={passwordId}
            type="password"
            value={password}
            disabled={submitting}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="●●●●"
            className="snow-input mt-2 w-full sm:w-52"
          />
        </div>
      )}
      {error && <p className="mt-3 text-sm font-bold text-[var(--snow-error)]">{error}</p>}
      <div className="mt-4 flex justify-end gap-2">
        <button type="button" disabled={submitting} onClick={onCancel} className="snow-btn-secondary">
          취소
        </button>
        <button type="button" disabled={submitting} onClick={onSubmit} className="snow-btn-primary">
          {submitting ? "수정 중..." : "수정 완료"}
        </button>
      </div>
    </div>
  );
}

function getWriterName(comment: CommentItem) {
  if (comment.isAnonymous) return `익명 (${comment.writerIp})`;
  return comment.writer?.nickname || "알 수 없음";
}

function canEditComment(comment: CommentItem, currentUserPublicId: string | null) {
  if (comment.isDeleted) return false;
  if (comment.isAnonymous) return true;
  return Boolean(currentUserPublicId && comment.writer?.publicId === currentUserPublicId);
}

function updateCommentContent(comments: CommentItem[], commentId: number, content: string) {
  return comments.map((comment) => {
    if (comment.commentId === commentId) return { ...comment, content };
    return {
      ...comment,
      previewReplies: comment.previewReplies.map((reply) =>
        reply.commentId === commentId ? { ...reply, content } : reply,
      ),
    };
  });
}

function CommentDeleteInline({
  comment,
  currentUserPublicId,
  isAdmin,
  isActive,
  onOpen,
  onClose,
  password,
  setPassword,
  error,
  submitting,
  onConfirm,
}: {
  comment: CommentItem;
  currentUserPublicId: string | null;
  isAdmin: boolean;
  isActive: boolean;
  onOpen: () => void;
  onClose: () => void;
  password: string;
  setPassword: (val: string) => void;
  error: string;
  submitting: boolean;
  onConfirm: () => void;
}) {
  const isOwnerMember = !comment.isAnonymous && currentUserPublicId && comment.writer?.publicId === currentUserPublicId;
  const isOwnerAnonMember = comment.isAnonymous && currentUserPublicId && comment.writer?.publicId === currentUserPublicId;
  const requiresPassword = !isAdmin && !isOwnerMember && !isOwnerAnonMember;

  return (
    <div className="relative inline-flex items-center">
      <button
        type="button"
        onClick={isActive ? onClose : onOpen}
        className={`ml-1.5 inline-flex h-4 w-4 items-center justify-center rounded-[2px] text-[10px] font-bold transition cursor-pointer ${
          isActive ? "bg-black text-white" : "bg-gray-300/80 text-white hover:bg-gray-400"
        }`}
        title={isActive ? "취소" : "댓글 삭제"}
      >
        ✕
      </button>

      {isActive && (
        <>
          {/* 외부 클릭 시 닫히도록 투명 백드롭 오버레이 */}
          <div className="fixed inset-0 z-40" onClick={onClose} />

          {/* 시간 및 아이콘 바로 아래에 완벽하게 플로팅되는 팝오버 (레이아웃 밀림 0) */}
          <div className="absolute right-0 top-full mt-1.5 z-50 flex items-center bg-[#1c2e5c] border border-black shadow-xl rounded-xs">
            {requiresPassword ? (
              <input
                type="password"
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    onConfirm();
                  } else if (e.key === "Escape") {
                    onClose();
                  }
                }}
                autoFocus
                className="h-6 w-28 bg-white px-1.5 text-xs text-black outline-none border-r border-black placeholder:text-gray-400"
              />
            ) : (
              <span className="h-6 flex items-center px-2 text-[11px] font-medium text-white select-none whitespace-nowrap border-r border-[#2a4078]">
                삭제할까요?
              </span>
            )}
            <button
              type="button"
              onClick={onConfirm}
              disabled={submitting}
              className="h-6 px-2.5 text-xs font-bold text-white hover:bg-[#283f7a] transition-colors border-r border-[#2a4078] disabled:opacity-50 whitespace-nowrap cursor-pointer"
            >
              {submitting ? "..." : "확인"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="h-6 px-1.5 text-xs font-bold text-white hover:bg-[#283f7a] transition-colors cursor-pointer"
              title="취소"
            >
              ✕
            </button>
            {error && (
              <div className="absolute right-0 top-full mt-1 z-50 rounded border border-red-300 bg-red-50 px-2 py-0.5 text-[11px] font-bold text-red-600 shadow-md whitespace-nowrap">
                {error}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function formatCommentDate(dateString: string): string {
  try {
    const d = new Date(dateString);
    if (isNaN(d.getTime())) return dateString;
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const hh = String(d.getHours()).padStart(2, "0");
    const min = String(d.getMinutes()).padStart(2, "0");
    const ss = String(d.getSeconds()).padStart(2, "0");
    return `${mm}.${dd} ${hh}:${min}:${ss}`;
  } catch {
    return dateString;
  }
}

function canDeleteComment(comment: CommentItem, currentUserPublicId: string | null, isAdmin: boolean): boolean {
  if (comment.isDeleted) return false;
  if (isAdmin) return true;
  if (comment.isAnonymous) return true;
  return Boolean(currentUserPublicId && comment.writer?.publicId === currentUserPublicId);
}
