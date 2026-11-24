"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { MessageComposeForm } from "../../components/MessageComposeForm";
import { csrfFetch } from "../../lib/csrfFetch";
import { BlockedMember, MESSAGE_ENDPOINTS, MessageDetail, blockMember, formatMessageDate, notifyMessagesUpdated, unblockMember } from "../../lib/messageApi";
import { ReportButton } from "../../components/ReportButton";
import styles from "../../components/messages.module.css";
import { errorText } from "../../lib/errorText";
import { readErrorMessage } from "../../lib/http";
import { confirm, toast } from "../../lib/feedback";

export default function MessageDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [message, setMessage] = useState<MessageDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [replying, setReplying] = useState(false);
  const [deleting, setDeleting] = useState(false);
  // null이면 아직 차단 목록을 읽지 못했다. 그동안 차단 버튼을 숨긴다.
  const [blocked, setBlocked] = useState<boolean | null>(null);
  const [blocking, setBlocking] = useState(false);

  const redirectToLogin = useCallback(() => {
    router.replace(`/login?returnUrl=${encodeURIComponent(`/messages/${id}`)}`);
  }, [id, router]);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(MESSAGE_ENDPOINTS.detail(id), { credentials: "include", cache: "no-store" });
        if (response.status === 401) {
          redirectToLogin();
          return;
        }
        if (!response.ok) throw new Error(await readErrorMessage(response, "쪽지를 불러오지 못했습니다."));
        const data: MessageDetail = await response.json();
        if (cancelled) return;
        setMessage(data);
        // 받은 쪽지를 처음 열면 서버가 읽음으로 바꾸므로 헤더 배지를 다시 읽는다.
        if (data.box === "RECEIVED") notifyMessagesUpdated();
      } catch (loadError) {
        if (!cancelled) setError(errorText(loadError, "쪽지를 불러오지 못했습니다."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [id, redirectToLogin]);

  // 받은 쪽지에서만 차단과 신고를 보인다. 탈퇴한 회원은 publicId가 없다.
  const sender = message?.box === "RECEIVED" && message.counterpart.publicId
    ? { publicId: message.counterpart.publicId, nickname: message.counterpart.nickname }
    : null;
  const senderPublicId = sender?.publicId ?? null;

  useEffect(() => {
    if (!senderPublicId) return;
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(MESSAGE_ENDPOINTS.blocks, { credentials: "include", cache: "no-store" });
        if (!response.ok) return;
        const blocks: BlockedMember[] = await response.json();
        if (!cancelled) setBlocked(blocks.some((member) => member.publicId === senderPublicId));
      } catch {
        // 차단 목록을 못 읽으면 차단 버튼만 숨긴다. 신고는 그대로 할 수 있다.
      }
    }, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [senderPublicId]);

  const toggleBlock = async () => {
    if (!sender || blocking || blocked === null) return;
    const confirmed = blocked
      ? await confirm({ title: `${sender.nickname}님의 차단을 해제할까요?`, confirmLabel: "해제" })
      : await confirm({ title: `${sender.nickname}님을 차단할까요?`, description: "서로 쪽지를 보낼 수 없게 됩니다. 이미 받은 쪽지는 그대로 남습니다.", confirmLabel: "차단", danger: true });
    if (!confirmed) return;
    setBlocking(true);
    setError("");
    try {
      if (blocked) {
        await unblockMember(sender.publicId);
        setBlocked(false);
        toast.success(`${sender.nickname}님의 차단을 해제했습니다.`);
      } else {
        await blockMember(sender.publicId);
        setBlocked(true);
        setReplying(false);
        toast.success(`${sender.nickname}님을 차단했습니다.`);
      }
    } catch (blockError) {
      setError(errorText(blockError, blocked ? "차단을 해제하지 못했습니다." : "차단하지 못했습니다."));
    } finally {
      setBlocking(false);
    }
  };

  const remove = async () => {
    if (!message || deleting) return;
    if (!(await confirm({ title: "이 쪽지를 내 쪽지함에서 삭제할까요?", description: "상대 쪽지함에는 남습니다.", confirmLabel: "삭제", danger: true }))) return;
    setDeleting(true);
    setError("");
    try {
      const response = await csrfFetch(MESSAGE_ENDPOINTS.detail(message.id), { method: "DELETE" });
      if (response.status === 401) {
        redirectToLogin();
        return;
      }
      if (!response.ok) throw new Error(await readErrorMessage(response, "쪽지를 삭제하지 못했습니다."));
      notifyMessagesUpdated();
      toast.success("쪽지를 삭제했습니다.");
      router.replace(message.box === "SENT" ? "/messages?box=sent" : "/messages");
    } catch (deleteError) {
      setError(errorText(deleteError, "쪽지를 삭제하지 못했습니다."));
      setDeleting(false);
    }
  };

  const backHref = message?.box === "SENT" ? "/messages?box=sent" : "/messages";
  const listing = message?.marketListing;

  return (
    <div className="community-page">
      <TopNav />
      <main className={`community-container ${styles.page}`}>
        <header className={styles.heading}>
          <div>
            <h1>{message?.box === "SENT" ? "보낸 쪽지" : "받은 쪽지"}</h1>
          </div>
          <Link href={backHref} className={styles.button}>목록으로</Link>
        </header>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {notice && <p className={styles.notice} role="status">{notice}</p>}
        {loading ? (
          <div className={styles.state} aria-busy="true">쪽지를 불러오는 중입니다.</div>
        ) : message && (
          <article className={styles.card}>
            <header className={styles.cardHeader}>
              <strong>{message.box === "SENT" ? `받는 회원 ${message.counterpart.nickname}` : `보낸 회원 ${message.counterpart.nickname}`}</strong>
              <span className={styles.meta}>
                {formatMessageDate(message.createdAt)}
                {message.box === "SENT" && (message.read ? " 읽음" : " 안 읽음")}
              </span>
            </header>
            {listing && (listing.deleted || !listing.publicId ? (
              <p className={styles.listingDeleted}>삭제된 판매글</p>
            ) : (
              <Link href={`/market/${listing.publicId}`} className={styles.listingLink}>
                <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">sell</span>
                {listing.title}
              </Link>
            ))}
            <p className={styles.body}>{message.content}</p>
            <div className={styles.actions}>
              <button type="button" className={styles.button} onClick={() => void remove()} disabled={deleting}>
                {deleting ? "삭제 중" : "삭제"}
              </button>
              {sender && blocked !== null && (
                <button type="button" className={styles.button} onClick={() => void toggleBlock()} disabled={blocking}>
                  {blocking ? "처리 중" : blocked ? "차단 해제" : "차단"}
                </button>
              )}
              {sender && (
                <ReportButton
                  targetType="MESSAGE"
                  targetId={message.id}
                  className={styles.button}
                  blockTarget={blocked ? undefined : sender}
                  onBlocked={() => { setBlocked(true); setReplying(false); }}
                />
              )}
              {message.canReply && message.counterpart.publicId && !blocked && !replying && (
                <button type="button" className={styles.buttonPrimary} onClick={() => { setReplying(true); setNotice(""); }}>답장</button>
              )}
            </div>
            {replying && message.counterpart.publicId && (
              <MessageComposeForm
                receiver={{ publicId: message.counterpart.publicId, nickname: message.counterpart.nickname }}
                // 답장은 판매글을 다시 참조하지 않는다. 판매자가 구매자에게 답장하면 받는 회원이 판매글 작성자가 아니라서 서버가 거부한다.
                quote={message.content}
                onCancel={() => setReplying(false)}
                onSent={() => {
                  setReplying(false);
                  setNotice("답장을 보냈습니다.");
                }}
              />
            )}
          </article>
        )}
      </main>
      <Footer />
    </div>
  );
}
