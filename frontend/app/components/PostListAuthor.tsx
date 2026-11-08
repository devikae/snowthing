"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { KeyboardEvent, MouseEvent } from "react";
import { createPortal } from "react-dom";
import { composeMessageHref, fetchViewerPublicId } from "../lib/messageApi";
import { CommentProfileAvatar } from "./CommentProfileAvatar";

interface PostListAuthorProps {
  nickname: string;
  profileImageUrl?: string | null;
  publicId?: string | null;
}

export function PostListAuthor({ nickname, profileImageUrl, publicId }: PostListAuthorProps) {
  const [zoomOpen, setZoomOpen] = useState(false);
  const [viewer, setViewer] = useState<string | null>(null);
  const anonymousIp = /^익명\s*\((\d{1,3}\.\d{1,3})\.\*{3}\.\*{3}\)$/.exec(nickname)?.[1];

  useEffect(() => {
    if (!zoomOpen) return;
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") setZoomOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [zoomOpen]);

  // 목록 줄마다 요청이 나가지 않게 창을 열 때만 로그인 회원을 확인한다.
  useEffect(() => {
    if (!zoomOpen || !publicId) return;
    let cancelled = false;
    void fetchViewerPublicId().then((value) => {
      if (!cancelled) setViewer(value);
    });
    return () => {
      cancelled = true;
    };
  }, [zoomOpen, publicId]);

  // 목록 한 줄 전체가 글 링크라서, 사진이나 닉네임을 누를 때는 글 이동을 막고 프로필 창만 연다.
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

  if (anonymousIp) {
    return (
      <span className="m-author m-author-anonymous">
        <span className="m-anonymous-badge">익명</span>
        <b>{anonymousIp}</b>
      </span>
    );
  }

  return (
    <span className="m-author">
      <span
        role="button"
        tabIndex={0}
        className="m-author-trigger"
        aria-label={`${nickname} 프로필 보기`}
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
        <b>{nickname}</b>
      </span>
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
              {publicId && viewer && viewer !== publicId && (
                <Link
                  href={composeMessageHref({ publicId, nickname })}
                  className="author-zoom-message"
                  // 바깥 창의 closeZoom이 기본 동작을 막으면 페이지가 이동하지 않는다.
                  onClick={(event) => event.stopPropagation()}
                >
                  쪽지 보내기
                </Link>
              )}
            </div>
          </div>,
          document.body,
        )}
    </span>
  );
}
