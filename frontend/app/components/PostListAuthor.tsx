"use client";

import { useEffect, useState } from "react";
import type { KeyboardEvent, MouseEvent } from "react";
import { createPortal } from "react-dom";
import { CommentProfileAvatar } from "./CommentProfileAvatar";

interface PostListAuthorProps {
  nickname: string;
  profileImageUrl?: string | null;
}

export function PostListAuthor({ nickname, profileImageUrl }: PostListAuthorProps) {
  const [zoomOpen, setZoomOpen] = useState(false);
  const anonymousIp = /^익명\s*\((\d{1,3}\.\d{1,3})\.\*{3}\.\*{3}\)$/.exec(nickname)?.[1];

  useEffect(() => {
    if (!zoomOpen) return;
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") setZoomOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [zoomOpen]);

  // 목록 한 줄 전체가 글 링크라서, 사진을 누를 때는 글 이동을 막고 확대 창만 연다.
  const openZoom = (event: MouseEvent | KeyboardEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setZoomOpen(true);
  };

  // 포털로 띄워도 React 이벤트는 부모 링크까지 올라가므로 여기서 끊는다.
  const closeZoom = (event: MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    if (event.target === event.currentTarget || (event.target as HTMLElement).closest(".author-zoom-close")) {
      setZoomOpen(false);
    }
  };

  return (
    <span className={`m-author${anonymousIp ? " m-author-anonymous" : ""}`}>
      {anonymousIp ? (
        <span className="m-anonymous-badge">익명</span>
      ) : (
        <span
          role="button"
          tabIndex={0}
          className="m-avatar-button"
          aria-label={`${nickname} 프로필 사진 크게 보기`}
          onClick={openZoom}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") openZoom(event);
          }}
        >
          <CommentProfileAvatar
            nickname={nickname}
            profileImageUrl={profileImageUrl}
            size={18}
            className="m-avatar"
          />
        </span>
      )}
      <b>{anonymousIp || nickname}</b>
      {zoomOpen &&
        createPortal(
          <div className="author-zoom-backdrop" onClick={closeZoom}>
            <div role="dialog" aria-modal="true" aria-label={`${nickname} 프로필`} className="author-zoom-card">
              <button type="button" className="author-zoom-close" aria-label="닫기" autoFocus>
                <span className="material-symbols-outlined" aria-hidden="true">close</span>
              </button>
              <CommentProfileAvatar
                nickname={nickname}
                profileImageUrl={profileImageUrl}
                size={160}
                className="author-zoom-avatar"
              />
              <strong>{nickname}</strong>
            </div>
          </div>,
          document.body,
        )}
    </span>
  );
}
