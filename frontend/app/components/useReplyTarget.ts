"use client";

import { useEffect, useRef, useState } from "react";
import { mentionPrefix } from "../lib/commentReplies";

export type ReplyTarget = {
  /** 답글이 달릴 최상위 댓글. 답글은 한 단계만 있어 답글의 답글도 여기에 달린다. */
  rootId: string;
  /** 답글 버튼을 누른 댓글. 최상위 댓글이면 rootId와 같다. */
  commentId: string;
  name: string;
};

/**
 * 답글 입력창 상태. 입력창은 댓글 묶음 맨 아래에 열고, 열리면 그 자리로 스크롤하고 커서를 끝에 둔다.
 * 답글의 답글이면 입력창에 @닉네임을 넣어 누구에게 답하는지 남긴다. 대상이 바뀌면 쓰던 내용은 비운다.
 */
export function useReplyTarget(setText: (text: string) => void) {
  const [target, setTarget] = useState<ReplyTarget | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const input = inputRef.current;
    if (!target || !input) return;
    input.focus({ preventScroll: true });
    input.setSelectionRange(input.value.length, input.value.length);
    input.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [target]);

  const toggle = (rootId: string, commentId: string, name: string) => {
    if (target?.commentId === commentId) {
      setTarget(null);
      return;
    }
    setText(rootId === commentId ? "" : mentionPrefix(name));
    setTarget({ rootId, commentId, name });
  };

  return {
    target,
    inputRef,
    toggle,
    close: () => setTarget(null),
    isOpenFor: (rootId: string) => target?.rootId === rootId,
    isReplyingTo: (commentId: string) => target?.commentId === commentId,
    label: (value: ReplyTarget) => (value.rootId === value.commentId ? `${value.name}님 댓글에 답글` : `${value.name}님에게 답글`),
  };
}
