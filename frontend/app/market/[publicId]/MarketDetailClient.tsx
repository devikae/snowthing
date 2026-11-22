"use client";

import dynamic from "next/dynamic";
import Image from "next/image";
import Link from "next/link";
import { Fragment, use, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { CommentProfileAvatar } from "../../components/CommentProfileAvatar";
import { AuthorMenu } from "../../components/AuthorMenu";
import { MarketMessageButton } from "../../components/MarketMessageButton";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { mergeReplies, replyCursor } from "../../lib/commentReplies";
import { useReplyTarget } from "../../components/useReplyTarget";
import { ReplyComposer } from "../../components/ReplyComposer";
import { IDEMPOTENCY_HEADER, useIdempotencyKey } from "../../lib/idempotencyKey";
import { ReportButton } from "../../components/ReportButton";
import { errorText } from "../../lib/errorText";
import { KOREAN_DATE_TIME, formatKoreanDate } from "../../lib/koreanDate";
import { fetchViewer, isAdminViewer } from "../../lib/viewer";

const ToastViewer = dynamic(() => import("../../components/ToastViewer"), { ssr: false });

interface MarketDetail {
  publicId: string;
  title: string;
  content: string;
  category: { code: string; name: string };
  productCondition: string;
  transactionMethod: string;
  tradeStatus: string;
  price: number;
  negotiable: boolean;
  free: boolean;
  contact: string | null;
  seller: { publicId: string | null; nickname: string; profileImageUrl: string | null };
  images: string[];
  coverImageUrl: string | null;
  viewCount: number;
  commentCount: number;
  version: number;
  canEdit: boolean;
  canDelete: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface CommentItem {
  commentId: string;
  parentId: string | null;
  writer: { publicId: string | null; nickname: string; profileImageUrl: string | null } | null;
  content: string;
  isDeleted: boolean;
  replyCount: number;
  previewReplies: CommentItem[];
  hasMoreReplies: boolean;
  createdAt: string;
}

interface CommentListResponse {
  totalCommentCount: number;
  comments: CommentItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

const conditionLabels: Record<string, string> = {
  NEW: "새 상품",
  LIKE_NEW: "거의 새 상품",
  GOOD: "사용감 적음",
  USED: "사용감 있음",
  DAMAGED: "수리, 하자 있음",
};

const transactionLabels: Record<string, string> = {
  DIRECT: "직거래",
  DELIVERY: "택배",
  BOTH: "직거래, 택배",
};

type ModerationStatus = "NORMAL" | "HIDDEN" | "BLOCKED";

const moderationActions: { status: ModerationStatus; label: string; confirm: string }[] = [
  { status: "HIDDEN", label: "숨기기", confirm: "이 판매글을 숨길까요? 일반 사용자는 목록과 상세 화면에서 볼 수 없게 됩니다." },
  { status: "BLOCKED", label: "차단", confirm: "이 판매글을 차단할까요? 일반 사용자는 목록과 상세 화면에서 볼 수 없게 됩니다." },
];

const restoreAction = { status: "NORMAL" as const, label: "공개로 되돌리기", confirm: "이 판매글을 다시 공개할까요?" };

const hiddenNotices: Record<string, string> = {
  HIDDEN: "숨긴 판매글입니다. 일반 사용자에게는 보이지 않고 관리자에게만 보입니다.",
  BLOCKED: "차단한 판매글입니다. 일반 사용자에게는 보이지 않고 관리자에게만 보입니다.",
  TEMPORARY_HOLD: "신고로 임시조치된 판매글입니다. 해제는 신고 처리 화면에서 합니다.",
};

const tradeLabels: Record<string, string> = {
  ON_SALE: "판매 중",
  RESERVED: "예약 중",
  SOLD: "판매 완료",
};

function formatDate(value: string) {
  return formatKoreanDate(value, KOREAN_DATE_TIME);
}

export default function MarketDetailClient({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);
  const router = useRouter();
  const [listing, setListing] = useState<MarketDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [activeImage, setActiveImage] = useState(0);
  const [comment, setComment] = useState("");
  const [reply, setReply] = useState("");
  const replyTarget = useReplyTarget(setReply);
  // 방금 단 답글 id. 더보기 커서로 쓰면 사이 답글을 건너뛰므로 구분해 둔다.
  const localReplyIdsRef = useRef<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const isSubmittingCommentRef = useRef(false);
  const commentIdempotencyKey = useIdempotencyKey();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");
  const [copyNotice, setCopyNotice] = useState("");
  const [isAdmin, setIsAdmin] = useState(false);
  const [moderating, setModerating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void fetchViewer().then((viewer) => {
      if (!cancelled) setIsAdmin(isAdminViewer(viewer));
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // 복사는 권한이 없거나 오래된 브라우저에서 실패할 수 있어, 결과를 문구로 알려 준다.
  const copyContact = async (contact: string) => {
    try {
      await navigator.clipboard.writeText(contact);
      setCopyNotice("연락처를 복사했습니다.");
    } catch {
      setCopyNotice("복사하지 못했습니다. 연락처를 길게 눌러 복사해 주세요.");
    }
  };

  const redirectToLogin = useCallback(() => {
    router.replace(`/login?returnUrl=${encodeURIComponent(`/market/${publicId}`)}`);
  }, [publicId, router]);

  const loadComments = useCallback(async (cursor: string | null = null, append = false, redirectOnUnauthorized = true) => {
    const response = await fetch(API_ENDPOINTS.posts.comments(publicId, cursor), { credentials: "include" });
    if (response.status === 401) {
      // 판매글과 함께 부를 때는 판매글 응답이 로그인 이동을 정한다.
      if (redirectOnUnauthorized) redirectToLogin();
      return;
    }
    if (!response.ok) throw new Error("댓글을 불러오지 못했습니다.");
    const data: CommentListResponse = await response.json();
    setComments((current) => append ? [...current, ...data.comments] : data.comments);
    setNextCursor(data.nextCursor);
    setHasNext(data.hasNext);
  }, [publicId, redirectToLogin]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    // 댓글은 판매글과 함께 요청하되 기다리지 않는다. 댓글 실패는 판매글 위 안내로만 보여 준다.
    const commentsPromise = loadComments(null, false, false).catch((commentError) => commentError as unknown);
    try {
      const response = await fetch(API_ENDPOINTS.market.detail(publicId), { credentials: "include" });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) {
        const body = response.status === 403 ? await response.json().catch(() => null) : null;
        if (body?.code === "POST_009") throw new Error(body.message);
        throw new Error(response.status === 404 ? "판매글을 찾을 수 없거나 숨김 처리되었습니다." : "판매글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
      setListing(await response.json());
      void commentsPromise.then((commentError) => {
        if (commentError) setError(errorText(commentError, "댓글을 불러오지 못했습니다."));
      });
    } catch (loadError) {
      setError(errorText(loadError, "판매글을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [loadComments, publicId, redirectToLogin]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const loadMoreReplies = async (item: CommentItem) => {
    const cursor = replyCursor(item.previewReplies, localReplyIdsRef.current);
    const response = await fetch(API_ENDPOINTS.comments.replies(item.commentId, cursor), {
      credentials: "include",
    });
    if (!response.ok) {
      setError("답글을 불러오지 못했습니다.");
      return;
    }
    const data: { replies: CommentItem[]; hasNext: boolean } = await response.json();
    setComments((current) => current.map((commentItem) => commentItem.commentId === item.commentId
      ? { ...commentItem, previewReplies: mergeReplies(commentItem.previewReplies, data.replies), hasMoreReplies: data.hasNext }
      : commentItem));
  };

  const createComment = async (parentId: string | null) => {
    const content = (parentId ? reply : comment).trim();
    if (!content || submitting || isSubmittingCommentRef.current) return;
    isSubmittingCommentRef.current = true;
    setSubmitting(true);
    try {
      const body = JSON.stringify({ parentId, content, isAnonymous: false, anonymousPassword: null });
      const response = await csrfFetch(API_ENDPOINTS.posts.comments(publicId), {
        method: "POST",
        headers: { "Content-Type": "application/json", [IDEMPOTENCY_HEADER]: commentIdempotencyKey.keyFor(body) },
        body,
      });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        throw new Error(data?.message || "댓글을 등록하지 못했습니다.");
      }
      commentIdempotencyKey.reset();
      if (parentId) {
        // 답글은 다시 불러오지 않고 붙인다. 다시 불러오면 미리보기 5개 밖의 새 답글이 안 보인다.
        const created: CommentItem = await response.json();
        localReplyIdsRef.current.add(String(created.commentId));
        setComments((current) => current.map((item) => item.commentId === parentId
          ? { ...item, replyCount: item.replyCount + 1, previewReplies: mergeReplies(item.previewReplies, [created]) }
          : item));
        setReply("");
        replyTarget.close();
      } else {
        setComment("");
        await loadComments();
      }
      setListing((current) => current ? { ...current, commentCount: current.commentCount + 1 } : current);
    } catch (submitError) {
      setError(errorText(submitError, "댓글을 등록하지 못했습니다."));
    } finally {
      isSubmittingCommentRef.current = false;
      setSubmitting(false);
    }
  };

  const [changingTradeStatus, setChangingTradeStatus] = useState(false);
  const changeTradeStatus = async (tradeStatus: string) => {
    if (!listing || changingTradeStatus) return;
    setChangingTradeStatus(true);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.market.tradeStatus(publicId), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tradeStatus, version: listing.version }),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        setError(data?.message || "거래 상태를 변경하지 못했습니다. 새로고침 후 다시 시도해 주세요.");
        return;
      }
      const data: { tradeStatus: string; version: number; updatedAt: string } = await response.json();
      setListing((current) => (current ? { ...current, ...data } : current));
    } catch {
      setError("거래 상태를 변경하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.");
    } finally {
      setChangingTradeStatus(false);
    }
  };

  // 관리자가 판매글 화면에서 바로 숨기거나 다시 공개한다. 권한은 서버의 관리자 API가 다시 확인한다.
  const changeModeration = async (action: { status: ModerationStatus; confirm: string }) => {
    if (!listing || moderating || !window.confirm(action.confirm)) return;
    setModerating(true);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.marketModeration(publicId), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ moderationStatus: action.status }),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        setError(data?.message || "판매글 상태를 바꾸지 못했습니다.");
        return;
      }
      setListing((current) => (current ? { ...current, status: action.status } : current));
    } catch {
      setError("판매글 상태를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setModerating(false);
    }
  };

  const deleteListing = async () => {
    if (!listing || !listing.canDelete || deleting || !window.confirm("이 판매글을 삭제할까요?")) return;
    setDeleting(true);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.market.delete(publicId), { method: "DELETE" });
      if (response.ok) {
        router.replace("/market");
        return;
      }
      const data = await response.json().catch(() => null);
      setError(data?.message || "판매글을 삭제하지 못했습니다.");
    } catch {
      setError("판매글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeleting(false);
    }
  };

  if (loading) return <div className="community-page"><TopNav /><main className="community-container market-state">판매글을 불러오는 중입니다.</main></div>;
  if (!listing) return <div className="community-page"><TopNav /><main className="community-container market-state"><p>{error}</p><Link href="/market">목록으로</Link></main></div>;

  // 공개 상태가 아니면 서버가 수정과 거래 상태 변경을 막으므로 그 버튼은 숨긴다. 삭제는 그대로 둔다.
  const isPublic = listing.status === "NORMAL";
  const canEdit = listing.canEdit && isPublic;
  const adminActions = !isAdmin ? [] : isPublic ? moderationActions : listing.status === "HIDDEN" || listing.status === "BLOCKED" ? [restoreAction] : [];
  const galleryImages = listing.images.length ? listing.images : listing.coverImageUrl ? [listing.coverImageUrl] : [];

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container market-detail-page content-detail-page">
        <div className="post-detail-toolbar"><Link href="/market"><span className="material-symbols-outlined" aria-hidden="true">arrow_back</span> 목록으로 돌아가기</Link></div>
        {error && <div className="compose-error" role="alert">{error}</div>}
        <article className="market-detail-card">
          <section className="market-gallery">
            <div className="market-gallery-main">
              {galleryImages.length ? <Image src={galleryImages[activeImage]} alt={`${listing.title} 상품 사진 ${activeImage + 1}`} width={720} height={540} unoptimized /> : <span className="material-symbols-outlined" aria-hidden="true">inventory_2</span>}
            </div>
            {listing.images.length > 1 && <div className="market-gallery-thumbs">{listing.images.map((image, index) => <button type="button" key={image} className={activeImage === index ? "active" : ""} onClick={() => setActiveImage(index)}><Image src={image} alt={`${index + 1}번 사진`} width={96} height={72} unoptimized /></button>)}</div>}
          </section>
          <section className="market-detail-summary">
            {isAdmin && hiddenNotices[listing.status] && <p role="status" className="mb-3 rounded border border-[var(--snow-border)] px-3 py-2 text-sm text-[var(--snow-muted)]">{hiddenNotices[listing.status]}</p>}
            <div className="market-detail-tags"><em data-status={listing.tradeStatus}>{tradeLabels[listing.tradeStatus]}</em><span>{listing.category.name}</span></div>
            <h1>{listing.title}</h1>
            <strong className="market-detail-price">{listing.free ? "무료 나눔" : `${listing.price.toLocaleString()}원`}{listing.negotiable && <small>가격 협의 가능</small>}</strong>
            <dl><div><dt>상품 상태</dt><dd>{conditionLabels[listing.productCondition]}</dd></div><div><dt>거래 방식</dt><dd>{transactionLabels[listing.transactionMethod]}</dd></div><div><dt>등록일</dt><dd>{formatDate(listing.createdAt)}</dd></div><div><dt>조회 / 댓글</dt><dd>{listing.viewCount} / {listing.commentCount}</dd></div></dl>
            <div className="market-seller"><CommentProfileAvatar className="market-seller-avatar" nickname={listing.seller.nickname} profileImageUrl={listing.seller.profileImageUrl} size={36} /><div><small>판매자</small><AuthorMenu publicId={listing.seller.publicId} nickname={listing.seller.nickname} /></div></div>
            {listing.contact && <div className="market-contact"><small>판매자 연락처</small><strong>{listing.contact}</strong><button type="button" onClick={() => void copyContact(listing.contact ?? "")}>복사</button>{copyNotice && <small role="status">{copyNotice}</small>}</div>}
            <MarketMessageButton seller={listing.seller} listing={{ publicId, title: listing.title }} />
            {(canEdit || listing.canDelete || adminActions.length > 0) && <div className="market-owner-tools">{canEdit && <><select value={listing.tradeStatus} onChange={(event) => void changeTradeStatus(event.target.value)}><option value="ON_SALE">판매 중</option><option value="RESERVED">예약 중</option><option value="SOLD">판매 완료</option></select><Link href={`/market/${publicId}/edit`}>수정</Link></>}{adminActions.map((action) => <button type="button" key={action.status} onClick={() => void changeModeration(action)} disabled={moderating}>{action.label}</button>)}{listing.canDelete && <button type="button" onClick={() => void deleteListing()} disabled={deleting}>{deleting ? "삭제 중" : "삭제"}</button>}</div>}
            {canEdit && listing.tradeStatus === "SOLD" && <p className="market-sold-note">판매 완료 글은 장터 목록 기본 보기에서 숨겨져요. 목록의 거래 상태에서 &apos;전체(판매 완료 포함)&apos;를 고르면 보여요.</p>}
            <ReportButton targetType="POST" targetId={publicId} />
          </section>
        </article>

        <section className="market-description"><h2>상품 설명</h2><ToastViewer content={listing.content} /></section>

        <section id="comments" className="market-comments post-comments-card unified-comments-card">
          <header><h2>댓글 <span>{listing.commentCount}</span></h2></header>
          <div className="post-comment-compose"><textarea className="post-comment-textarea" aria-label="댓글 입력" value={comment} onChange={(event) => setComment(event.target.value)} maxLength={1000} placeholder="판매자에게 궁금한 내용을 남겨보세요." /><div className="post-comment-compose-footer"><button className="post-comment-submit-button" type="button" disabled={submitting || !comment.trim()} onClick={() => void createComment(null)}>댓글 등록</button></div></div>
          <div className="market-comment-list post-comment-list">
            {comments.length === 0 && <p className="market-comment-empty">아직 댓글이 없습니다.</p>}
            {comments.map((item) => <div className="market-comment post-comment-item" key={item.commentId}>
              <div className="comment-youtube-row"><MarketCommentAvatar item={item} /><div className="comment-youtube-body"><header><AuthorMenu publicId={item.isDeleted ? null : item.writer?.publicId} nickname={item.writer?.nickname ?? "탈퇴한 회원"} /><time>{formatDate(item.createdAt)}</time></header><p className={item.isDeleted ? "deleted" : ""}>{item.content}</p>{!item.isDeleted && <div className="comment-youtube-actions"><button type="button" onClick={() => replyTarget.toggle(item.commentId, item.commentId, item.writer?.nickname ?? "탈퇴한 회원")}>{replyTarget.isReplyingTo(item.commentId) ? "답글 취소" : "답글"}</button><ReportButton targetType="COMMENT" targetId={item.commentId} /></div>}</div></div>
              {replyTarget.isReplyingTo(item.commentId) && <ReplyComposer inputRef={replyTarget.inputRef} value={reply} onChange={setReply} onCancel={replyTarget.close} onSubmit={() => void createComment(item.commentId)} submitting={submitting} />}
              <div className="comment-reply-list">{item.previewReplies.map((child) => <Fragment key={child.commentId}><div className="market-comment-reply post-comment-reply"><div className="comment-youtube-row"><span className="material-symbols-outlined comment-reply-arrow" aria-hidden="true">subdirectory_arrow_right</span><MarketCommentAvatar item={child} /><div className="comment-youtube-body"><header><AuthorMenu publicId={child.isDeleted ? null : child.writer?.publicId} nickname={child.writer?.nickname ?? "탈퇴한 회원"} /><time>{formatDate(child.createdAt)}</time></header><p>{child.content}</p>{!child.isDeleted && <div className="comment-youtube-actions"><button type="button" onClick={() => replyTarget.toggle(item.commentId, child.commentId, child.writer?.nickname ?? "탈퇴한 회원")}>{replyTarget.isReplyingTo(child.commentId) ? "답글 취소" : "답글"}</button><ReportButton targetType="COMMENT" targetId={child.commentId} /></div>}</div></div></div>{replyTarget.isReplyingTo(child.commentId) && <ReplyComposer inputRef={replyTarget.inputRef} value={reply} onChange={setReply} onCancel={replyTarget.close} onSubmit={() => void createComment(item.commentId)} submitting={submitting} nested />}</Fragment>)}</div>
              {item.hasMoreReplies && <button className="post-comments-more-button reply-more" type="button" onClick={() => void loadMoreReplies(item)}>답글 더보기</button>}
            </div>)}
          </div>
          {hasNext && nextCursor && <button className="market-comments-more" type="button" onClick={() => void loadComments(nextCursor, true)}>댓글 더보기</button>}
        </section>
      </main>
      <Footer />
    </div>
  );
}

function MarketCommentAvatar({ item }: { item: CommentItem }) {
  const nickname = item.writer?.nickname ?? "탈퇴한 회원";
  return <CommentProfileAvatar nickname={nickname} profileImageUrl={item.writer?.profileImageUrl} />;
}
