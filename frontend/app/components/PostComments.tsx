"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { CommentProfileAvatar } from "./CommentProfileAvatar";

interface WriterInfo {
  publicId: string | null;
  nickname: string;
  profileImageUrl: string | null;
}

interface CommentItem {
  commentId: string;
  parentId: string | null;
  writer: WriterInfo | null;
  isAnonymous: boolean;
  content: string;
  isDeleted: boolean;
  replyCount: number;
  previewReplies: CommentItem[];
  hasMoreReplies: boolean;
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
}

interface CommentListResponse {
  totalCommentCount: number;
  comments: CommentItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

interface ReplyListResponse {
  totalReplyCount: number;
  replies: CommentItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

const COMMENT_PAGE_SIZE = 20;

function ProfileAvatar({ writer }: { writer: WriterInfo | null }) {
  const nickname = writer?.nickname || "탈퇴한 회원";
  return <CommentProfileAvatar nickname={nickname} profileImageUrl={writer?.profileImageUrl} />;
}

function mergeUnique(current: CommentItem[], added: CommentItem[]) {
  return [...current, ...added].filter(
    (item, index, all) => all.findIndex((candidate) => candidate.commentId === item.commentId) === index,
  );
}

export default function PostComments({ postPublicId }: { postPublicId: string }) {
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [currentUserPublicId, setCurrentUserPublicId] = useState<string | null>(null);
  const [content, setContent] = useState("");
  const [replyParentId, setReplyParentId] = useState<string | null>(null);
  const [replyContent, setReplyContent] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [working, setWorking] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadComments = useCallback(async (cursor: string | null = null, append = false) => {
    setError("");
    if (!append) setLoading(true);
    try {
      const response = await fetch(API_ENDPOINTS.posts.comments(postPublicId, cursor, COMMENT_PAGE_SIZE), {
        credentials: "include",
      });
      if (!response.ok) throw new Error("댓글을 불러오지 못했습니다.");
      const data: CommentListResponse = await response.json();
      setComments((current) => (append ? mergeUnique(current, data.comments || []) : data.comments || []));
      setTotalCount(data.totalCommentCount || 0);
      setNextCursor(data.nextCursor ?? null);
      setHasNext(Boolean(data.hasNext));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "댓글을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [postPublicId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadComments();
      void fetch(API_ENDPOINTS.members.me, { credentials: "include" })
        .then((response) => (response.ok ? response.json() : null))
        .then((member) => setCurrentUserPublicId(member?.publicId ?? null))
        .catch(() => setCurrentUserPublicId(null));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadComments]);

  const createComment = async (parentId: string | null) => {
    const value = (parentId ? replyContent : content).trim();
    if (!currentUserPublicId || !value || working) return;
    setWorking(true);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.posts.comments(postPublicId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ parentId, content: value, isAnonymous: false, anonymousPassword: null }),
      });
      if (!response.ok) throw new Error("댓글을 등록하지 못했습니다.");
      const created: CommentItem = await response.json();
      if (parentId) {
        setComments((current) => current.map((root) => root.commentId === parentId
          ? { ...root, replyCount: root.replyCount + 1, previewReplies: mergeUnique(root.previewReplies, [created]).slice(0, 5), hasMoreReplies: root.hasMoreReplies || root.replyCount + 1 > 5 }
          : root));
        setReplyParentId(null);
        setReplyContent("");
      } else {
        setComments((current) => [...current, created]);
        setContent("");
      }
      setTotalCount((current) => current + 1);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "댓글을 등록하지 못했습니다.");
    } finally {
      setWorking(false);
    }
  };

  const loadMoreReplies = async (root: CommentItem) => {
    const cursor = root.previewReplies.at(-1)?.commentId ?? null;
    setWorking(true);
    setError("");
    try {
      const response = await fetch(API_ENDPOINTS.comments.replies(root.commentId, cursor, COMMENT_PAGE_SIZE), {
        credentials: "include",
      });
      if (!response.ok) throw new Error("답글을 불러오지 못했습니다.");
      const data: ReplyListResponse = await response.json();
      setComments((current) => current.map((item) => item.commentId === root.commentId
        ? { ...item, replyCount: data.totalReplyCount, previewReplies: mergeUnique(item.previewReplies, data.replies || []), hasMoreReplies: data.hasNext }
        : item));
    } catch (replyError) {
      setError(replyError instanceof Error ? replyError.message : "답글을 불러오지 못했습니다.");
    } finally {
      setWorking(false);
    }
  };

  const updateComment = async (commentId: string) => {
    const value = editingContent.trim();
    if (!value || working) return;
    setWorking(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.comments.delete(commentId), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: value, anonymousPassword: null }),
      });
      if (!response.ok) throw new Error("댓글을 수정하지 못했습니다.");
      const replace = (items: CommentItem[]): CommentItem[] => items.map((item) => item.commentId === commentId
        ? { ...item, content: value }
        : { ...item, previewReplies: replace(item.previewReplies) });
      setComments(replace);
      setEditingId(null);
      setEditingContent("");
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "댓글을 수정하지 못했습니다.");
    } finally {
      setWorking(false);
    }
  };

  const deleteComment = async (commentId: string) => {
    if (working || !window.confirm("댓글을 삭제하시겠습니까?")) return;
    setWorking(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.comments.delete(commentId), {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ anonymousPassword: null }),
      });
      if (!response.ok) throw new Error("댓글을 삭제하지 못했습니다.");
      await loadComments();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "댓글을 삭제하지 못했습니다.");
    } finally {
      setWorking(false);
    }
  };

  const renderComment = (item: CommentItem, reply = false) => (
    <article className={reply ? "post-comment-reply" : "post-comment-item"} key={item.commentId}>
      <div className="comment-youtube-row">
        {reply && <span className="material-symbols-outlined comment-reply-arrow">subdirectory_arrow_right</span>}
        <ProfileAvatar writer={item.writer} />
        <div className="comment-youtube-body">
          <header><strong>{item.writer?.nickname || "탈퇴한 회원"}</strong><time>{new Date(item.createdAt).toLocaleString("ko-KR")}</time></header>
          {editingId === item.commentId ? (
            <div className="comment-inline-editor">
              <textarea maxLength={1000} value={editingContent} onChange={(event) => setEditingContent(event.target.value)} />
              <button type="button" onClick={() => void updateComment(item.commentId)}>저장</button>
              <button type="button" onClick={() => setEditingId(null)}>취소</button>
            </div>
          ) : <p className={item.isDeleted ? "deleted" : ""}>{item.content}</p>}
          {!item.isDeleted && editingId !== item.commentId && <div className="comment-youtube-actions">
            {!reply && <button type="button" onClick={() => setReplyParentId(replyParentId === item.commentId ? null : item.commentId)}>답글</button>}
            {item.canEdit && <button type="button" onClick={() => { setEditingId(item.commentId); setEditingContent(item.content); }}>수정</button>}
            {item.canDelete && <button type="button" onClick={() => void deleteComment(item.commentId)}>삭제</button>}
          </div>}
        </div>
      </div>
      {!reply && item.previewReplies.map((child) => renderComment(child, true))}
      {!reply && item.hasMoreReplies && <button className="post-comments-more-button reply-more" type="button" disabled={working} onClick={() => void loadMoreReplies(item)}>답글 더보기</button>}
      {!reply && replyParentId === item.commentId && <div className="post-reply-compose">
        <textarea className="post-comment-textarea" maxLength={1000} value={replyContent} onChange={(event) => setReplyContent(event.target.value)} placeholder="답글을 입력하세요." />
        <div className="post-comment-compose-footer"><button className="post-comment-submit-button" type="button" disabled={working || !replyContent.trim()} onClick={() => void createComment(item.commentId)}>답글 등록</button></div>
      </div>}
    </article>
  );

  return <section className="post-comments-card carpool-comments-card">
    <header><h2>댓글 <span>{totalCount}</span></h2></header>
    {currentUserPublicId ? <div className="post-comment-compose">
      <textarea className="post-comment-textarea" maxLength={1000} value={content} onChange={(event) => setContent(event.target.value)} placeholder="댓글을 입력하세요." />
      <div className="post-comment-compose-footer"><button className="post-comment-submit-button" type="button" disabled={working || !content.trim()} onClick={() => void createComment(null)}>댓글 등록</button></div>
    </div> : <div className="comment-login-guide"><p>댓글을 작성하려면 로그인이 필요합니다.</p><Link href={`/login?returnUrl=${encodeURIComponent(`/carpool/${postPublicId}`)}`}>로그인</Link><Link href="/signup">회원가입</Link></div>}
    {error && <p className="carpool-form-error" role="alert">{error}</p>}
    <div className="post-comment-list">
      {loading ? <p className="carpool-comments-empty">댓글을 불러오는 중입니다.</p> : comments.length === 0 ? <p className="carpool-comments-empty">첫 댓글을 남겨보세요.</p> : comments.map((item) => renderComment(item))}
    </div>
    {hasNext && nextCursor && <div className="post-comments-more"><button className="post-comments-more-button" type="button" disabled={working} onClick={() => void loadComments(nextCursor, true)}>댓글 더보기</button></div>}
  </section>;
}
