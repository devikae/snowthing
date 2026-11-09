"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import {
  NOTIFICATION_ENDPOINTS,
  NotificationItem,
  NotificationPage,
  formatNotificationTime,
  markAllNotificationsRead,
  markNotificationRead,
  notificationHref,
  notificationMessage,
} from "../lib/notifications";
import PushToggle from "./PushToggle";
import "./notifications.css";

type Tab = "all" | "unread";

const PAGE_SIZE = 20;
const LOGIN_URL = "/login?returnUrl=/notifications";

export default function NotificationsPage() {
  const router = useRouter();
  const [tab, setTab] = useState<Tab>("all");
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const requestVersion = useRef(0);

  const load = useCallback(async (targetTab: Tab, cursor: string | null) => {
    const version = ++requestVersion.current;
    setLoading(true);
    setError("");
    try {
      const response = await fetch(
        NOTIFICATION_ENDPOINTS.list({ unreadOnly: targetTab === "unread", cursor, size: PAGE_SIZE }),
        { credentials: "include" },
      );
      if (response.status === 401) {
        router.replace(LOGIN_URL);
        return;
      }
      if (!response.ok) throw new Error();
      const data: NotificationPage = await response.json();
      if (version !== requestVersion.current) return;
      setItems((current) => (cursor ? [...current, ...data.items] : data.items));
      setNextCursor(data.nextCursor);
      setHasNext(data.hasNext);
    } catch {
      if (version === requestVersion.current) setError("알림을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(tab, null); }, 0);
    return () => window.clearTimeout(timer);
  }, [tab, load]);

  const openItem = async (item: NotificationItem) => {
    if (!item.read) await markNotificationRead(item.notificationId).catch(() => false);
    router.push(notificationHref(item));
  };

  const readAll = async () => {
    const ok = await markAllNotificationsRead().catch(() => false);
    if (!ok) {
      setError("모두 읽음 처리에 실패했어요. 잠시 후 다시 시도해 주세요.");
      return;
    }
    if (tab === "unread") {
      setItems([]);
      setNextCursor(null);
      setHasNext(false);
    } else {
      setItems((current) => current.map((entry) => ({ ...entry, read: true })));
    }
  };

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container notifications-page">
        <div className="notifications-head">
          <h1>알림</h1>
          <button type="button" className="notifications-read-all" onClick={() => void readAll()} disabled={items.every((item) => item.read)}>
            모두 읽음
          </button>
        </div>
        <PushToggle />
        <div className="notifications-tabs" role="tablist" aria-label="알림 보기">
          {(["all", "unread"] as const).map((value) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              className={tab === value ? "active" : ""}
              onClick={() => setTab(value)}
            >
              {value === "all" ? "전체" : "안 읽음"}
            </button>
          ))}
        </div>
        <section className="notifications-panel" aria-busy={loading}>
          {error && <p className="notification-error" role="alert">{error}</p>}
          {!loading && !error && items.length === 0 && (
            <p className="notification-empty">{tab === "unread" ? "안 읽은 알림이 없어요." : "받은 알림이 없어요."}</p>
          )}
          {items.length > 0 && (
            <ul className="notification-list">
              {items.map((item) => (
                <li key={item.notificationId}>
                  <button
                    type="button"
                    className={`notification-item${item.read ? "" : " unread"}`}
                    onClick={() => void openItem(item)}
                  >
                    <span className="notification-item-title">{notificationMessage(item)}</span>
                    {item.preview && <span className="notification-item-preview">{item.preview}</span>}
                    <span className="notification-item-time">{formatNotificationTime(item.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {loading && <p className="notification-empty">불러오는 중...</p>}
          {!loading && hasNext && (
            <button type="button" className="notifications-more" onClick={() => void load(tab, nextCursor)}>
              더 보기
            </button>
          )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
