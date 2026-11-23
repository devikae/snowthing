"use client";

import { useEffect, useId, useRef, useState } from "react";
import { errorText } from "../lib/errorText";

interface DeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (password: string) => Promise<void>;
  title?: string;
  description?: string;
  requirePassword?: boolean;
  confirmLabel?: string;
  submittingLabel?: string;
}

export function DeleteConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  title = "게시글 삭제 확인",
  description = "이 게시글을 삭제할까요?",
  requirePassword = true,
  confirmLabel = "삭제",
  submittingLabel = "삭제 중",
}: DeleteConfirmModalProps) {
  const titleId = useId();
  const passwordId = useId();
  const [password, setPassword] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  // 창이 열리면 창 안으로 초점을 옮기고, 닫히면 창을 연 버튼으로 되돌린다.
  // 그러지 않으면 키보드와 화면 읽기 프로그램 사용자는 뒤의 글 화면에 머문 채 창이 열린 줄 모른다.
  useEffect(() => {
    if (!isOpen) return;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    // 비밀번호 칸이 있으면 그 칸, 없으면 취소 버튼. 실수로 Enter를 눌러도 바로 지워지지 않게 삭제 버튼은 고르지 않는다.
    dialogRef.current?.querySelector<HTMLElement>("input, .delete-modal-actions button")?.focus();
    return () => opener?.focus();
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (requirePassword && !password.trim()) {
      setErrorMsg("비밀번호를 입력해 주세요.");
      return;
    }

    setErrorMsg("");
    setSubmitting(true);
    try {
      await onConfirm(password.trim());
      setPassword("");
    } catch (err: unknown) {
      setErrorMsg(errorText(err, "삭제하지 못했습니다. 비밀번호를 확인한 뒤 다시 시도해 주세요."));
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    if (submitting) return;
    setPassword("");
    setErrorMsg("");
    onClose();
  };

  return (
    <div
      className="delete-modal-backdrop"
      onKeyDown={(event) => {
        if (event.key === "Escape") handleClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="delete-modal"
      >
        <div className="delete-modal-head">
          <h3 id={titleId}>{title}</h3>
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            aria-label="삭제 확인 모달 닫기"
          >
            <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>

        <p className="delete-modal-description">{description}</p>

        <form onSubmit={handleSubmit} className="delete-modal-form">
          {requirePassword && (
            <label htmlFor={passwordId}>
              <span>비밀번호</span>
              <input
                id={passwordId}
                type="password"
                placeholder="작성할 때 입력한 비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
          )}

          {errorMsg && (
            <p className="delete-modal-error" role="alert">{errorMsg}</p>
          )}

          <div className="delete-modal-actions">
            <button
              type="button"
              onClick={handleClose}
              disabled={submitting}
            >
              취소
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="delete-modal-confirm"
            >
              {submitting ? submittingLabel : confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
