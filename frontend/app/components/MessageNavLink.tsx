"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { MESSAGE_ENDPOINTS, MESSAGES_UPDATED_EVENT } from "../lib/messageApi";
import styles from "./messages.module.css";

const POLL_INTERVAL_MS = 60_000;

/**
 * 헤더 쪽지 아이콘과 안 읽은 수 배지. 로그인한 회원에게만 그린다.
 * 페이지를 옮길 때, 탭이 다시 보일 때, 탭이 보이는 동안 60초마다 다시 읽는다. 쪽지를 읽거나 지우면 이벤트로 바로 다시 읽는다.
 */
export function MessageNavLink() {
  const pathname = usePathname();
  const [count, setCount] = useState(0);
  const inFlight = useRef(false);

  const refresh = useCallback(async () => {
    if (inFlight.current || document.visibilityState !== "visible") return;
    inFlight.current = true;
    try {
      const response = await fetch(MESSAGE_ENDPOINTS.unreadCount, { credentials: "include", cache: "no-store" });
      if (response.ok) {
        const data: { count: number } = await response.json();
        setCount(data.count);
      } else if (response.status === 401) {
        setCount(0);
      }
    } catch {
      // 배지는 보조 정보라 실패해도 화면을 막지 않는다.
    } finally {
      inFlight.current = false;
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [pathname, refresh]);

  useEffect(() => {
    const interval = window.setInterval(() => void refresh(), POLL_INTERVAL_MS);
    const onVisible = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    const onUpdated = () => void refresh();
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener(MESSAGES_UPDATED_EVENT, onUpdated);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener(MESSAGES_UPDATED_EVENT, onUpdated);
    };
  }, [refresh]);

  const label = count > 0 ? `쪽지함, 안 읽은 쪽지 ${count}통` : "쪽지함";
  return (
    <Link href="/messages" className={`header-login ${styles.navLink}`} aria-label={label} title="쪽지함">
      <span className="material-symbols-outlined" aria-hidden="true">sticky_note_2</span>
      {count > 0 && <span className={styles.badge} aria-hidden="true">{count > 99 ? "99+" : count}</span>}
    </Link>
  );
}
