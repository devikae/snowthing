"use client";

import { useEffect, useLayoutEffect, useState, type KeyboardEvent, type RefObject } from "react";
import { CommentProfileAvatar } from "./CommentProfileAvatar";
import { fetchViewer, peekViewer, type Viewer } from "../lib/viewer";

const REPLY_MAX_LENGTH = 1000;

type ReplyComposerProps = {
  inputRef: RefObject<HTMLTextAreaElement | null>;
  value: string;
  onChange: (value: string) => void;
  onCancel: () => void;
  onSubmit: () => void;
  submitting: boolean;
  /** 로그인해야 쓸 수 있는데 로그아웃 상태면 입력칸 대신 안내 문구를 보여 준다. */
  loginRequired?: boolean;
  /** 익명 게시판은 다른 댓글처럼 프로필 사진을 드러내지 않고 기본 아바타를 쓴다. */
  anonymous?: boolean;
  /** 비회원 익명 답글의 비밀번호. 있으면 버튼 줄 왼쪽에 작게 보여 준다. */
  password?: { value: string; onChange: (value: string) => void };
  /** 답글 아래에 열리면 답글 카드와 같은 들여쓰기를 쓴다. */
  nested?: boolean;
};

/**
 * 유튜브식 답글 입력창. 답글 버튼을 누른 댓글 바로 아래에 열린다.
 * 밑줄만 있는 입력칸은 한 줄에서 시작해 내용만큼 늘어나고, Esc나 취소로 닫으면 포커스를 답글 버튼에 돌려준다.
 */
export function ReplyComposer({ inputRef, value, onChange, onCancel, onSubmit, submitting, loginRequired = false, anonymous = false, password, nested = false }: ReplyComposerProps) {
  const [viewer, setViewer] = useState<Viewer | null>(() => peekViewer() ?? null);
  // 열기 직전 포커스는 방금 누른 답글 버튼이다. 닫을 때 그 자리로 돌려준다.
  const [opener] = useState(() => (typeof document === "undefined" ? null : document.activeElement as HTMLElement | null));

  useEffect(() => {
    let alive = true;
    void fetchViewer().then((result) => {
      if (alive) setViewer(result);
    });
    return () => {
      alive = false;
    };
  }, []);

  // 내용이 바뀔 때마다 높이를 다시 재서 스크롤 없이 늘어나게 한다.
  useLayoutEffect(() => {
    const input = inputRef.current;
    if (!input) return;
    input.style.height = "auto";
    input.style.height = `${input.scrollHeight + input.offsetHeight - input.clientHeight}px`;
  }, [inputRef, value]);

  const close = () => {
    onCancel();
    opener?.focus({ preventScroll: true });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "Escape") return;
    event.preventDefault();
    event.stopPropagation();
    close();
  };

  const nickname = anonymous ? "익명" : viewer?.nickname ?? "비회원";

  return (
    <div className={nested ? "reply-composer nested" : "reply-composer"} onKeyDown={handleKeyDown}>
      <CommentProfileAvatar
        className="comment-profile-avatar reply-composer-avatar"
        nickname={nickname}
        profileImageUrl={anonymous ? null : viewer?.profileImageUrl}
        size={24}
      />
      <div className="reply-composer-body">
        {loginRequired ? (
          <p className="reply-composer-note">로그인 후 답글을 작성할 수 있습니다.</p>
        ) : (
          <textarea
            ref={inputRef}
            className="reply-composer-input"
            rows={1}
            maxLength={REPLY_MAX_LENGTH}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder="답글 추가..."
            aria-label="답글 입력"
          />
        )}
        <div className="reply-composer-actions">
          {password && !loginRequired && (
            <input
              className="reply-composer-password"
              type="password"
              aria-label="답글 비밀번호"
              placeholder="비밀번호 (4~20자)"
              minLength={4}
              maxLength={20}
              value={password.value}
              onChange={(event) => password.onChange(event.target.value)}
            />
          )}
          <button type="button" className="reply-composer-cancel" onClick={close}>취소</button>
          {!loginRequired && (
            <button type="button" className="reply-composer-submit" disabled={submitting || !value.trim()} onClick={onSubmit}>답글</button>
          )}
        </div>
      </div>
    </div>
  );
}
