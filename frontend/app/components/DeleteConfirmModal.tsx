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
  description = "이 게시글을 삭제하시겠습니까?",
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 font-sans">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md rounded-2xl border border-[var(--snow-border)] bg-[var(--snow-canvas)] p-6 shadow-2xl shadow-black/30"
      >
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-[var(--snow-line-soft)] pb-3">
          <h3 id={titleId} className="text-base font-bold text-[var(--snow-ink)]">
            {title}
          </h3>
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            aria-label="삭제 확인 모달 닫기"
            className="text-[var(--snow-muted)] hover:text-[var(--snow-ink)] font-bold text-base p-1 rounded-lg hover:bg-[var(--snow-surface-low)] transition"
          >
            ✕
          </button>
        </div>

        {/* Modal Description */}
        <p className="mt-3.5 text-xs leading-relaxed text-[var(--snow-ink-soft)]">
          {description}
        </p>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="mt-4 grid gap-3.5">
          {requirePassword && (
            <label htmlFor={passwordId} className="grid gap-1.5">
              <span className="text-xs font-semibold text-[var(--snow-ink-soft)]">
                비밀번호 입력
              </span>
              <input
                id={passwordId}
                type="password"
                placeholder="비밀번호를 입력하세요"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoFocus
                className="snow-input text-xs"
              />
            </label>
          )}

          {errorMsg && (
            <div className="rounded-xl border border-rose-200 bg-rose-50 dark:bg-rose-950/40 dark:border-rose-900/50 p-2.5 text-xs font-semibold text-rose-700 dark:text-rose-400 flex items-center gap-1.5">
              <span>⚠️</span>
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Action Buttons */}
          <div className="mt-2 flex items-center justify-end gap-2.5">
            <button
              type="button"
              onClick={handleClose}
              disabled={submitting}
              className="px-4 py-2 text-xs font-semibold rounded-xl border border-[var(--snow-border)] text-[var(--snow-ink-soft)] hover:bg-[var(--snow-surface-low)] hover:text-[var(--snow-ink)] transition"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-xs font-bold rounded-xl bg-rose-600 hover:bg-rose-700 text-white shadow-xs transition disabled:opacity-50"
            >
              {submitting ? submittingLabel : confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
