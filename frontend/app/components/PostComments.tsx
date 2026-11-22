"use client";

import Link from "next/link";
import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import { fetchViewer } from "../lib/viewer";
import { csrfFetch } from "../lib/csrfFetch";
import { IDEMPOTENCY_HEADER, useIdempotencyKey } from "../lib/idempotencyKey";
import { CommentProfileAvatar } from "./CommentProfileAvatar";
import { AuthorMenu } from "./AuthorMenu";
import { ReportButton } from "./ReportButton";
import { errorText } from "../lib/errorText";
import { formatShortDateTime } from "../lib/koreanDate";
import { mergeReplies, replyCursor } from "../lib/commentReplies";
import { useReplyTarget } from "./useReplyTarget";
import { ReplyComposer } from "./ReplyComposer";
import { getJson, readErrorMessage } from "../lib/http";

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
  const [replyContent, setReplyContent] = useState("");
  const reply = useReplyTarget(setReplyContent);
  // 방금 단 답글 id. 더보기 커서로 쓰면 사이 답글을 건너뛰므로 구분해 둔다.
  const localReplyIdsRef = useRef<Set<string>>(new Set());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState("");
  const [working, setWorking] = useState(false);
  // state는 다음 렌더에 반영되므로 연타는 ref로 동기 차단한다.
  const isCreatingRef = useRef(false);
  const idempotencyKey = useIdempotencyKey();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadComments = useCallback(async (cursor: string | null = null, append = false) => {
    setError("");
    if (!append) setLoading(true);
    try {
      const data = await getJson<CommentListResponse>(API_ENDPOINTS.posts.comments(postPublicId, cursor, COMMENT_PAGE_SIZE), "댓글을 불러오지 못했습니다.");
      setComments((current) => (append ? mergeUnique(current, data.comments || []) : data.comments || []));
      setTotalCount(data.totalCommentCount || 0);
      setNextCursor(data.nextCursor ?? null);
      setHasNext(Boolean(data.hasNext));
    } catch (loadError) {
      setError(errorText(loadError, "댓글을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [postPublicId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadComments();
      void fetchViewer().then((viewer) => setCurrentUserPublicId(viewer?.publicId ?? null));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadComments]);

  const createComment = async (parentId: string | null) => {
    const value = (parentId ? replyContent : content).trim();
    if (!currentUserPublicId || !value || working || isCreatingRef.current) return;
    isCreatingRef.current = true;
    setWorking(true);
    setError("");
    try {
      const body = JSON.stringify({ parentId, content: value, isAnonymous: false, anonymousPassword: null });
      const response = await csrfFetch(API_ENDPOINTS.posts.comments(postPublicId), {
        method: "POST",
        headers: { "Content-Type": "application/json", [IDEMPOTENCY_HEADER]: idempotencyKey.keyFor(body) },
        body,
      });
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "댓글을 등록하지 못했습니다."));
      }
      idempotencyKey.reset();
      const created: CommentItem = await response.json();
      if (parentId) {
        setComments((current) => current.map((root) => root.commentId === parentId
          ? { ...root, replyCount: root.replyCount + 1, previewReplies: mergeReplies(root.previewReplies, [created]) }
          : root));
        localReplyIdsRef.current.add(String(created.commentId));
        reply.close();
        setReplyContent("");
      } else {
        setComments((current) => [...current, created]);
        setContent("");
      }
      setTotalCount((current) => current + 1);
    } catch (submitError) {
      setError(errorText(submitError, "댓글을 등록하지 못했습니다."));
    } finally {
      isCreatingRef.current = false;
      setWorking(false);
    }
  };

  const loadMoreReplies = async (root: CommentItem) => {
    const cursor = replyCursor(root.previewReplies, localReplyIdsRef.current);
    setWorking(true);
    setError("");
    try {
      const data = await getJson<ReplyListResponse>(API_ENDPOINTS.comments.replies(root.commentId, cursor, COMMENT_PAGE_SIZE), "답글을 불러오지 못했습니다.");
      setComments((current) => current.map((item) => item.commentId === root.commentId
        ? { ...item, replyCount: data.totalReplyCount, previewReplies: mergeReplies(item.previewReplies, data.replies || []), hasMoreReplies: data.hasNext }
        : item));
    } catch (replyError) {
      setError(errorText(replyError, "답글을 불러오지 못했습니다."));
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
      setError(errorText(updateError, "댓글을 수정하지 못했습니다."));
    } finally {
      setWorking(false);
    }
  };

  const deleteComment = async (commentId: string) => {
    if (working || !window.confirm("댓글을 삭제할까요?")) return;
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
      setError(errorText(deleteError, "댓글을 삭제하지 못했습니다."));
    } finally {
      setWorking(false);
    }
  };

  // 답글 입력창은 답글 버튼을 누른 댓글 바로 아래에 연다. 답글 아래면 답글 카드 밖에 같은 들여쓰기로 둔다.
  const renderReplyComposer = (item: CommentItem, root: CommentItem | null = null) => reply.isReplyingTo(item.commentId) && <ReplyComposer
    inputRef={reply.inputRef}
    value={replyContent}
    onChange={setReplyContent}
    onCancel={reply.close}
    onSubmit={() => void createComment(root?.commentId ?? item.commentId)}
    submitting={working}
    loginRequired={!currentUserPublicId}
    nested={Boolean(root)}
  />;

  const renderComment = (item: CommentItem, root: CommentItem | null = null) => (
    <article className={root ? "post-comment-reply" : "post-comment-item"} key={item.commentId}>
      <div className="comment-youtube-row">
        {root && <span className="material-symbols-outlined comment-reply-arrow" aria-hidden="true">subdirectory_arrow_right</span>}
        <ProfileAvatar writer={item.writer} />
        <div className="comment-youtube-body">
          <header><AuthorMenu publicId={item.isAnonymous || item.isDeleted ? null : item.writer?.publicId} nickname={item.writer?.nickname || "탈퇴한 회원"} /><time>{formatShortDateTime(item.createdAt)}</time></header>
          {editingId === item.commentId ? (
            <div className="comment-inline-editor">
              <textarea maxLength={1000} value={editingContent} onChange={(event) => setEditingContent(event.target.value)} />
              <button type="button" onClick={() => void updateComment(item.commentId)}>저장</button>
              <button type="button" onClick={() => setEditingId(null)}>취소</button>
            </div>
          ) : <p className={item.isDeleted ? "deleted" : ""}>{item.content}</p>}
          {!item.isDeleted && editingId !== item.commentId && <div className="comment-youtube-actions">
            <button type="button" onClick={() => reply.toggle(root?.commentId ?? item.commentId, item.commentId, item.writer?.nickname || "탈퇴한 회원")}>{reply.isReplyingTo(item.commentId) ? "답글 취소" : "답글"}</button>
            {item.canEdit && <button type="button" onClick={() => { setEditingId(item.commentId); setEditingContent(item.content); }}>수정</button>}
            {item.canDelete && <button type="button" onClick={() => void deleteComment(item.commentId)}>삭제</button>}
            <ReportButton targetType="COMMENT" targetId={String(item.commentId)} />
          </div>}
        </div>
      </div>
      {!root && renderReplyComposer(item)}
      {!root && item.previewReplies.map((child) => <Fragment key={child.commentId}>{renderComment(child, item)}{renderReplyComposer(child, item)}</Fragment>)}
      {!root && item.hasMoreReplies && <button className="post-comments-more-button reply-more" type="button" disabled={working} onClick={() => void loadMoreReplies(item)}>답글 더보기</button>}
    </article>
  );

  return <section id="comments" className="post-comments-card carpool-comments-card">
    <header><h2>댓글 <span>{totalCount}</span></h2></header>
    {currentUserPublicId ? <div className="post-comment-compose">
      <textarea className="post-comment-textarea" aria-label="댓글 입력" maxLength={1000} value={content} onChange={(event) => setContent(event.target.value)} placeholder="댓글을 입력하세요." />
      <div className="post-comment-compose-footer"><button className="post-comment-submit-button" type="button" disabled={working || !content.trim()} onClick={() => void createComment(null)}>댓글 등록</button></div>
    </div> : <div className="comment-login-guide"><p>댓글을 작성하려면 로그인이 필요합니다.</p><Link href={`/login?returnUrl=${encodeURIComponent(`/carpool/${postPublicId}`)}`}>로그인</Link><Link href="/signup">회원가입</Link></div>}
    {error && <p className="carpool-form-error" role="alert">{error}</p>}
    <div className="post-comment-list">
      {loading ? <p className="carpool-comments-empty">댓글을 불러오는 중입니다.</p> : comments.length === 0 ? <p className="carpool-comments-empty">첫 댓글을 남겨보세요.</p> : comments.map((item) => renderComment(item))}
    </div>
    {hasNext && nextCursor && <div className="post-comments-more"><button className="post-comments-more-button" type="button" disabled={working} onClick={() => void loadComments(nextCursor, true)}>댓글 더보기</button></div>}
  </section>;
}

