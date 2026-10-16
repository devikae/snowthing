"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";

interface NotificationItem {
  publicId: string;
  type: "POST_COMMENT" | "COMMENT_REPLY" | "POST_BEST";
  message: string;
  targetUrl: string | null;
  targetAvailable: boolean;
  read: boolean;
  createdAt: string;
}

interface NotificationPage {
  notifications: NotificationItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

export function NotificationBell({ visible }: { visible: boolean }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);

  const refreshUnread = useCallback(async () => {
    if (!visible) return;
    try {
      const response = await fetch(API_ENDPOINTS.notifications.unreadCount, { credentials: "include" });
      if (response.ok) setUnread((await response.json()).count ?? 0);
    } catch { /* 로그인 전 요청은 조용히 무시합니다. */ }
  }, [visible]);

  const load = async (cursor?: string | null) => {
    try {
      const response = await fetch(API_ENDPOINTS.notifications.list(cursor), { credentials: "include" });
      if (!response.ok) return;
      const page: NotificationPage = await response.json();
      setItems((current) => cursor ? [...current, ...page.notifications] : page.notifications);
      setNextCursor(page.hasNext ? page.nextCursor : null);
    } catch { /* 네트워크 오류는 다음 폴링에서 재시도합니다. */ }
  };

  useEffect(() => {
    if (!visible) return;
    void refreshUnread();
    const timer = window.setInterval(() => void refreshUnread(), 30000);
    return () => window.clearInterval(timer);
  }, [visible, refreshUnread]);

  const markRead = async (item: NotificationItem) => {
    if (!item.read) {
      await csrfFetch(API_ENDPOINTS.notifications.read(item.publicId), { method: "PATCH" });
      setItems((current) => current.map((entry) => entry.publicId === item.publicId ? { ...entry, read: true } : entry));
      setUnread((count) => Math.max(0, count - 1));
    }
    if (item.targetAvailable && item.targetUrl) router.push(item.targetUrl);
  };

  const markAllRead = async () => {
    await csrfFetch(API_ENDPOINTS.notifications.readAll, { method: "PATCH" });
    setItems((current) => current.map((item) => ({ ...item, read: true })));
    setUnread(0);
  };

  if (!visible) return null;
  return (
    <div className="notification-bell-wrap">
      <button type="button" className="header-notification-button" aria-label="알림" onClick={() => { setOpen((current) => !current); if (!open) void load(); }}>
        <span className="material-symbols-outlined">notifications</span>
        {unread > 0 && <span className="notification-badge">{unread > 99 ? "99+" : unread}</span>}
      </button>
      {open && <div className="notification-panel" role="dialog" aria-label="알림 목록">
        <div className="notification-panel-header"><strong>알림</strong><button type="button" onClick={() => void markAllRead()}>모두 읽음</button></div>
        {items.length === 0 ? <p className="notification-empty">새 알림이 없습니다.</p> : items.map((item) => <button type="button" key={item.publicId} className={`notification-item${item.read ? " read" : ""}`} onClick={() => void markRead(item)}><span>{item.message}</span><small>{new Date(item.createdAt).toLocaleString("ko-KR")}</small></button>)}
        {nextCursor && <button type="button" className="notification-more" onClick={() => void load(nextCursor)}>더 보기</button>}
      </div>}
    </div>
  );
}
