"use client";

import dynamic from "next/dynamic";
import Image from "next/image";
import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";

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
  contact: string;
  seller: { publicId: string; nickname: string; profileImageUrl: string | null };
  images: string[];
  viewCount: number;
  commentCount: number;
  version: number;
  canEdit: boolean;
  canDelete: boolean;
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
  DAMAGED: "수리·하자 있음",
};

const transactionLabels: Record<string, string> = {
  DIRECT: "직거래",
  DELIVERY: "택배",
  BOTH: "직거래·택배",
};

const tradeLabels: Record<string, string> = {
  ON_SALE: "판매 중",
  RESERVED: "예약 중",
  SOLD: "판매 완료",
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export default function MarketDetailPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = use(params);
  const router = useRouter();
  const [listing, setListing] = useState<MarketDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [activeImage, setActiveImage] = useState(0);
  const [comment, setComment] = useState("");
  const [replyParentId, setReplyParentId] = useState<string | null>(null);
  const [reply, setReply] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const redirectToLogin = useCallback(() => {
    router.replace(`/login?returnUrl=${encodeURIComponent(`/market/${publicId}`)}`);
  }, [publicId, router]);

  const loadComments = useCallback(async (cursor: string | null = null, append = false) => {
    const response = await fetch(API_ENDPOINTS.posts.comments(publicId, cursor), { credentials: "include" });
    if (response.status === 401) {
      redirectToLogin();
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
    try {
      const response = await fetch(API_ENDPOINTS.market.detail(publicId), { credentials: "include" });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) throw new Error("판매글을 찾을 수 없거나 숨김 처리되었습니다.");
      setListing(await response.json());
      await loadComments();
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "판매글을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [loadComments, publicId, redirectToLogin]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const loadMoreReplies = async (item: CommentItem) => {
    const cursor = item.previewReplies.at(-1)?.commentId ?? null;
    const response = await fetch(API_ENDPOINTS.comments.replies(item.commentId, cursor), {
      credentials: "include",
    });
    if (!response.ok) {
      setError("답글을 불러오지 못했습니다.");
      return;
    }
    const data: { replies: CommentItem[]; hasNext: boolean } = await response.json();
    setComments((current) => current.map((commentItem) => commentItem.commentId === item.commentId
      ? { ...commentItem, previewReplies: [...commentItem.previewReplies, ...data.replies], hasMoreReplies: data.hasNext }
      : commentItem));
  };

  const createComment = async (parentId: string | null) => {
    const content = (parentId ? reply : comment).trim();
    if (!content || submitting) return;
    setSubmitting(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.posts.comments(publicId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ parentId, content, isAnonymous: false, anonymousPassword: null }),
      });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) {
        const data = await response.json().catch(() => null);
        throw new Error(data?.message || "댓글을 등록하지 못했습니다.");
      }
      if (parentId) {
        setReply("");
        setReplyParentId(null);
      } else {
        setComment("");
      }
      await loadComments();
      setListing((current) => current ? { ...current, commentCount: current.commentCount + 1 } : current);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "댓글을 등록하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const changeTradeStatus = async (tradeStatus: string) => {
    if (!listing) return;
    const response = await csrfFetch(API_ENDPOINTS.market.tradeStatus(publicId), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tradeStatus, version: listing.version }),
    });
    if (!response.ok) {
      const data = await response.json().catch(() => null);
      setError(data?.message || "거래 상태를 변경하지 못했습니다. 새로고침 후 다시 시도해주세요.");
      return;
    }
    const data: { tradeStatus: string; version: number; updatedAt: string } = await response.json();
    setListing({ ...listing, ...data });
  };

  const deleteListing = async () => {
    if (!listing || !window.confirm("이 판매글을 삭제하시겠습니까?")) return;
    const response = await csrfFetch(API_ENDPOINTS.market.delete(publicId), { method: "DELETE" });
    if (response.ok) {
      router.replace("/market");
      return;
    }
    const data = await response.json().catch(() => null);
    setError(data?.message || "판매글을 삭제하지 못했습니다.");
  };

  if (loading) return <div className="community-page"><TopNav active="market" /><main className="community-container market-state">판매글을 불러오는 중입니다.</main></div>;
  if (!listing) return <div className="community-page"><TopNav active="market" /><main className="community-container market-state"><p>{error}</p><Link href="/market">목록으로</Link></main></div>;

  return (
    <div className="community-page">
      <TopNav active="market" />
      <main className="community-container market-detail-page">
        <nav className="market-breadcrumb"><Link href="/market">중고장터</Link><span>/</span><span>{listing.category.name}</span></nav>
        {error && <div className="compose-error" role="alert">{error}</div>}
        <article className="market-detail-card">
          <section className="market-gallery">
            <div className="market-gallery-main">
              {listing.images.length ? <Image src={listing.images[activeImage]} alt={`${listing.title} 상품 사진 ${activeImage + 1}`} width={720} height={540} unoptimized /> : <span className="material-symbols-outlined">inventory_2</span>}
            </div>
            {listing.images.length > 1 && <div className="market-gallery-thumbs">{listing.images.map((image, index) => <button type="button" key={image} className={activeImage === index ? "active" : ""} onClick={() => setActiveImage(index)}><Image src={image} alt={`${index + 1}번 사진`} width={96} height={72} unoptimized /></button>)}</div>}
          </section>
          <section className="market-detail-summary">
            <div className="market-detail-tags"><em data-status={listing.tradeStatus}>{tradeLabels[listing.tradeStatus]}</em><span>{listing.category.name}</span></div>
            <h1>{listing.title}</h1>
            <strong className="market-detail-price">{listing.free ? "무료 나눔" : `${listing.price.toLocaleString()}원`}{listing.negotiable && <small>가격 협의 가능</small>}</strong>
            <dl><div><dt>상품 상태</dt><dd>{conditionLabels[listing.productCondition]}</dd></div><div><dt>거래 방식</dt><dd>{transactionLabels[listing.transactionMethod]}</dd></div><div><dt>등록일</dt><dd>{formatDate(listing.createdAt)}</dd></div><div><dt>조회·댓글</dt><dd>{listing.viewCount} · {listing.commentCount}</dd></div></dl>
            <div className="market-seller"><Image src={listing.seller.profileImageUrl || "/images/default-profile-avatar.png"} alt="" width={44} height={44} unoptimized={Boolean(listing.seller.profileImageUrl)} /><div><small>판매자</small><strong>{listing.seller.nickname}</strong></div></div>
            <div className="market-contact"><small>판매자 연락처</small><strong>{listing.contact}</strong><button type="button" onClick={() => void navigator.clipboard.writeText(listing.contact)}>복사</button></div>
            {listing.canEdit && <div className="market-owner-tools"><select value={listing.tradeStatus} onChange={(event) => void changeTradeStatus(event.target.value)}><option value="ON_SALE">판매 중</option><option value="RESERVED">예약 중</option><option value="SOLD">판매 완료</option></select><Link href={`/market/${publicId}/edit`}>수정</Link><button type="button" onClick={() => void deleteListing()}>삭제</button></div>}
          </section>
        </article>

        <section className="market-description"><h2>상품 설명</h2><ToastViewer content={listing.content} /></section>

        <section className="market-comments">
          <h2>댓글 <span>{listing.commentCount}</span></h2>
          <div className="market-comment-compose"><textarea value={comment} onChange={(event) => setComment(event.target.value)} maxLength={1000} placeholder="판매자에게 궁금한 내용을 남겨보세요." /><button type="button" disabled={submitting || !comment.trim()} onClick={() => void createComment(null)}>등록</button></div>
          <div className="market-comment-list">
            {comments.length === 0 && <p className="market-comment-empty">아직 댓글이 없습니다.</p>}
            {comments.map((item) => <div className="market-comment" key={item.commentId}>
              <div><strong>{item.writer?.nickname ?? "탈퇴한 회원"}</strong><time>{formatDate(item.createdAt)}</time></div><p>{item.content}</p>
              {!item.isDeleted && <button type="button" onClick={() => setReplyParentId(replyParentId === item.commentId ? null : item.commentId)}>답글</button>}
              {item.previewReplies.map((child) => <div className="market-comment-reply" key={child.commentId}><div><strong>{child.writer?.nickname ?? "탈퇴한 회원"}</strong><time>{formatDate(child.createdAt)}</time></div><p>{child.content}</p></div>)}
              {item.hasMoreReplies && <button type="button" onClick={() => void loadMoreReplies(item)}>답글 더보기</button>}
              {replyParentId === item.commentId && <div className="market-comment-compose reply"><textarea value={reply} onChange={(event) => setReply(event.target.value)} maxLength={1000} placeholder="답글을 입력하세요." /><button type="button" disabled={submitting || !reply.trim()} onClick={() => void createComment(item.commentId)}>등록</button></div>}
            </div>)}
          </div>
          {hasNext && nextCursor && <button className="market-comments-more" type="button" onClick={() => void loadComments(nextCursor, true)}>댓글 더보기</button>}
        </section>
      </main>
      <Footer />
    </div>
  );
}
