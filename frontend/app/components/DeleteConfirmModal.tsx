"use client";

import { useId, useState } from "react";

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
  submittingLabel = "삭제 중...",
}: DeleteConfirmModalProps) {
  const titleId = useId();
  const passwordId = useId();
  const [password, setPassword] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (requirePassword && !password.trim()) {
      setErrorMsg("비밀번호를 입력해주세요.");
      return;
    }

    setErrorMsg("");
    setSubmitting(true);
    try {
      await onConfirm(password.trim());
      setPassword("");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setErrorMsg(err.message);
      } else {
        setErrorMsg("비밀번호가 일치하지 않거나 삭제 처리에 실패했습니다.");
      }
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
    <div className="delete-modal-backdrop">
      <div
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
            <span className="material-symbols-outlined" aria-hidden="true">close</span>
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
                autoFocus
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
