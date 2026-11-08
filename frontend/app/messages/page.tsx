"use client";

import Link from "next/link";
import { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { MESSAGE_ENDPOINTS, MessageBox, MessagePage, MessageSummary, formatMessageDate, readErrorMessage } from "../lib/messageApi";
import styles from "../components/messages.module.css";
import { errorText } from "../lib/errorText";

const boxLabels: Record<MessageBox, string> = { RECEIVED: "받은 쪽지", SENT: "보낸 쪽지" };

function MessageList() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const box: MessageBox = searchParams.get("box") === "sent" ? "SENT" : "RECEIVED";
  const [items, setItems] = useState<MessageSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async (cursor: string | null) => {
    const url = box === "SENT" ? MESSAGE_ENDPOINTS.sent(cursor) : MESSAGE_ENDPOINTS.received(cursor);
    const response = await fetch(url, { credentials: "include", cache: "no-store" });
    if (response.status === 401) {
      router.replace(`/login?returnUrl=${encodeURIComponent(box === "SENT" ? "/messages?box=sent" : "/messages")}`);
      return null;
    }
    if (!response.ok) throw new Error(await readErrorMessage(response, "쪽지함을 불러오지 못했습니다."));
    return (await response.json()) as MessagePage;
  }, [box, router]);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const page = await load(null);
        if (cancelled || !page) return;
        setItems(page.content);
        setNextCursor(page.pageInfo.hasNext ? page.pageInfo.nextCursor : null);
      } catch (loadError) {
        if (!cancelled) setError(errorText(loadError, "쪽지함을 불러오지 못했습니다."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [load]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await load(nextCursor);
      if (!page) return;
      setItems((current) => [...current, ...page.content]);
      setNextCursor(page.pageInfo.hasNext ? page.pageInfo.nextCursor : null);
    } catch (loadError) {
      setError(errorText(loadError, "쪽지함을 불러오지 못했습니다."));
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <>
      <nav className={styles.tabs} aria-label="쪽지함">
        <Link href="/messages" aria-current={box === "RECEIVED" ? "page" : undefined}>{boxLabels.RECEIVED}</Link>
        <Link href="/messages?box=sent" aria-current={box === "SENT" ? "page" : undefined}>{boxLabels.SENT}</Link>
      </nav>
      {error && <p className={styles.error} role="alert">{error}</p>}
      {loading ? (
        <div className={styles.state} aria-busy="true">쪽지함을 불러오는 중입니다.</div>
      ) : items.length === 0 ? (
        !error && <div className={styles.state}>{box === "SENT" ? "보낸 쪽지가 없습니다." : "받은 쪽지가 없습니다."}</div>
      ) : (
        <ul className={styles.list}>
          {items.map((item) => {
            const unread = box === "RECEIVED" && !item.read;
            return (
              <li key={item.id}>
                <Link href={`/messages/${item.id}`} className={`${styles.row} ${unread ? styles.rowUnread : ""}`}>
                  <span className={styles.rowTitle}>
                    {unread && <span className={styles.unreadDot} aria-label="안 읽음" />}
                    <span>{box === "SENT" ? `받는 회원 ${item.counterpart.nickname}` : item.counterpart.nickname}</span>
                    {item.marketListing && (
                      <span className={styles.listingTag}>{item.marketListing.deleted ? "삭제된 판매글" : item.marketListing.title}</span>
                    )}
                  </span>
                  <span className={styles.meta}>
                    {box === "SENT" && (item.read ? "읽음 " : "안 읽음 ")}
                    {formatMessageDate(item.createdAt)}
                  </span>
                  <span className={styles.rowPreview}>{item.preview}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      {nextCursor && (
        <button type="button" className={styles.more} onClick={() => void loadMore()} disabled={loadingMore}>
          {loadingMore ? "불러오는 중" : "더 보기"}
        </button>
      )}
    </>
  );
}

export default function MessagesPage() {
  return (
    <div className="community-page">
      <TopNav active="profile" />
      <main className={`community-container ${styles.page}`}>
        <header className={styles.heading}>
          <div>
            <h1>쪽지함</h1>
            <p>회원끼리 주고받은 1:1 쪽지입니다. 게시글이나 댓글 작성자의 닉네임을 눌러 쪽지를 보낼 수 있습니다.</p>
          </div>
          <Link href="/messages/new" className={styles.button}>쪽지 보내기</Link>
        </header>
        <Suspense fallback={<div className={styles.state} aria-busy="true">쪽지함을 불러오는 중입니다.</div>}>
          <MessageList />
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}
