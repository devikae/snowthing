"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import {
  NOTIFICATION_ENDPOINTS,
  NOTIFICATIONS_CHANGED_EVENT,
  NotificationItem,
  NotificationPage,
  formatNotificationTime,
  markAllNotificationsRead,
  markNotificationRead,
  notificationHref,
  notificationMessage,
} from "../lib/notifications";
import "../notifications/notifications.css";

const DROPDOWN_SIZE = 10;

function badgeText(count: number) {
  return count >= 10 ? "9+" : String(count);
}

/** 헤더 종 아이콘. 로그인한 회원에게만 그린다. 배지는 페이지 이동과 탭 복귀 때만 다시 센다(주기 조회 없음). */
export function NotificationBell() {
  const router = useRouter();
  const pathname = usePathname();
  const menuRef = useRef<HTMLDetailsElement>(null);
  const [count, setCount] = useState(0);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [listError, setListError] = useState("");

  const refreshCount = useCallback(async () => {
    try {
      const response = await fetch(NOTIFICATION_ENDPOINTS.unreadCount, { credentials: "include" });
      if (!response.ok) return;
      const data: { count: number } = await response.json();
      setCount(data.count);
    } catch {
      // 배지는 부가 정보라 실패해도 헤더를 깨지 않는다.
    }
  }, []);

  const loadList = useCallback(async () => {
    setLoadingList(true);
    setListError("");
    try {
      const response = await fetch(NOTIFICATION_ENDPOINTS.list({ size: DROPDOWN_SIZE }), { credentials: "include" });
      if (!response.ok) throw new Error();
      const data: NotificationPage = await response.json();
      setItems(data.items);
    } catch {
      setListError("알림을 불러오지 못했어요.");
    } finally {
      setLoadingList(false);
    }
  }, []);

  useEffect(() => {
    menuRef.current?.removeAttribute("open");
    const timer = window.setTimeout(() => { void refreshCount(); }, 0);
    return () => window.clearTimeout(timer);
  }, [pathname, refreshCount]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") void refreshCount();
    };
    const onChanged = () => { void refreshCount(); };
    const onOutsideClick = (event: MouseEvent) => {
      if (menuRef.current?.hasAttribute("open") && !menuRef.current.contains(event.target as Node)) {
        menuRef.current.removeAttribute("open");
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, onChanged);
    document.addEventListener("mousedown", onOutsideClick);
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, onChanged);
      document.removeEventListener("mousedown", onOutsideClick);
    };
  }, [refreshCount]);

  const handleToggle = () => {
    if (menuRef.current?.open) void loadList();
  };

  const openItem = async (item: NotificationItem) => {
    menuRef.current?.removeAttribute("open");
    if (!item.read) {
      setItems((current) => current.map((entry) => entry.notificationId === item.notificationId ? { ...entry, read: true } : entry));
      await markNotificationRead(item.notificationId).catch(() => false);
    }
    router.push(notificationHref(item));
  };

  const readAll = async () => {
    const ok = await markAllNotificationsRead().catch(() => false);
    if (ok) {
      setItems((current) => current.map((entry) => ({ ...entry, read: true })));
      setCount(0);
    }
  };

  return (
    <details ref={menuRef} className="header-notification-menu" onToggle={handleToggle}>
      <summary
        className="header-notification-button"
        aria-label={count > 0 ? `알림 ${count}개 안 읽음` : "알림"}
        title="알림"
      >
        <span className="material-symbols-outlined" aria-hidden="true">notifications</span>
        {count > 0 && <span className="header-notification-badge" aria-hidden="true">{badgeText(count)}</span>}
      </summary>
      <div className="header-notification-dropdown" role="dialog" aria-label="최근 알림">
        <div className="notification-dropdown-head">
          <strong>알림</strong>
          <button type="button" onClick={() => void readAll()} disabled={count === 0}>모두 읽음</button>
        </div>
        <div className="notification-dropdown-body">
          {loadingList && items.length === 0 ? (
            <p className="notification-empty">불러오는 중</p>
          ) : listError ? (
            <p className="notification-empty">{listError}</p>
          ) : items.length === 0 ? (
            <p className="notification-empty">새 알림이 없어요.</p>
          ) : (
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
        </div>
        <Link
          href="/notifications"
          className="notification-dropdown-all"
          onClick={() => menuRef.current?.removeAttribute("open")}
        >
          전체 보기
        </Link>
      </div>
    </details>
  );
}
