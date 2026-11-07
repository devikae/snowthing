"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { MessageComposeForm } from "../../components/MessageComposeForm";
import { csrfFetch } from "../../lib/csrfFetch";
import { MESSAGE_ENDPOINTS, MessageDetail, formatMessageDate, notifyMessagesUpdated, readErrorMessage } from "../../lib/messageApi";
import styles from "../../components/messages.module.css";
import { errorText } from "../../lib/errorText";

export default function MessageDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [message, setMessage] = useState<MessageDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [replying, setReplying] = useState(false);
  const [deleting, setDeleting] = useState(false);

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

  const remove = async () => {
    if (!message || deleting) return;
    if (!window.confirm("이 쪽지를 내 쪽지함에서 삭제할까요? 상대 쪽지함에는 남습니다.")) return;
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
      <TopNav active="profile" />
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
                <span className="material-symbols-outlined" aria-hidden="true">sell</span>
                {listing.title}
              </Link>
            ))}
            <p className={styles.body}>{message.content}</p>
            <div className={styles.actions}>
              <button type="button" className={styles.button} onClick={() => void remove()} disabled={deleting}>
                {deleting ? "삭제 중" : "삭제"}
              </button>
              {message.canReply && message.counterpart.publicId && !replying && (
                <button type="button" className={styles.buttonPrimary} onClick={() => { setReplying(true); setNotice(""); }}>답장</button>
              )}
            </div>
            {replying && message.counterpart.publicId && (
              <MessageComposeForm
                receiver={{ publicId: message.counterpart.publicId, nickname: message.counterpart.nickname }}
                // 답장은 판매글을 다시 참조하지 않는다. 판매자가 구매자에게 답장하면 받는 회원이 판매글 작성자가 아니라서 서버가 거부한다.
                quote={message.content}
                onCancel={() => setReplying(false)}
                onUnauthorized={redirectToLogin}
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
