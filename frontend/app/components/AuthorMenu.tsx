"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { blockMember, composeMessageHref, fetchViewerPublicId } from "../lib/messageApi";
import styles from "./messages.module.css";

interface AuthorMenuProps {
  publicId: string | null | undefined;
  nickname: string;
  className?: string;
}

/**
 * 게시글과 댓글 작성자 닉네임. 로그인한 회원이 다른 회원의 닉네임을 누르면 "쪽지 보내기", "차단" 메뉴를 연다.
 * 비로그인, 본인, 익명 글처럼 작성자 publicId가 없는 경우에는 닉네임만 보여 준다.
 */
export function AuthorMenu({ publicId, nickname, className }: AuthorMenuProps) {
  const [viewer, setViewer] = useState<string | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const menuRef = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    if (!publicId) return;
    let cancelled = false;
    void fetchViewerPublicId().then((value) => {
      if (!cancelled) setViewer(value);
    });
    return () => {
      cancelled = true;
    };
  }, [publicId]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (menuRef.current?.hasAttribute("open") && !menuRef.current.contains(event.target as Node)) {
        menuRef.current.removeAttribute("open");
      }
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  if (!publicId || !viewer || viewer === publicId) {
    return <strong className={className}>{nickname}</strong>;
  }

  const handleBlock = async () => {
    if (busy || blocked) return;
    if (!window.confirm(`${nickname}님을 차단할까요? 서로 쪽지를 보낼 수 없게 됩니다. 차단은 내 정보 수정 화면에서 풀 수 있습니다.`)) return;
    setBusy(true);
    setError("");
    try {
      await blockMember(publicId);
      setBlocked(true);
    } catch (blockError) {
      setError(blockError instanceof Error ? blockError.message : "차단하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <details ref={menuRef} className={styles.authorMenu}>
      <summary aria-label={`${nickname} 회원 메뉴`}><strong className={className}>{nickname}</strong></summary>
      <div className={styles.authorMenuList} role="menu">
        <Link role="menuitem" href={composeMessageHref({ publicId, nickname })} onClick={() => menuRef.current?.removeAttribute("open")}>
          쪽지 보내기
        </Link>
        <button type="button" role="menuitem" className={styles.authorMenuDanger} disabled={busy || blocked} onClick={() => void handleBlock()}>
          {blocked ? "차단했습니다" : busy ? "차단 중" : "차단"}
        </button>
        {error && <p className={styles.authorMenuNote} role="alert">{error}</p>}
      </div>
    </details>
  );
}
