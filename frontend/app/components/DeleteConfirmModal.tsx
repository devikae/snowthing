"use client";

import { useState } from "react";

interface DeleteConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (password: string) => Promise<void>;
  title?: string;
  description?: string;
  requirePassword?: boolean;
}

export function DeleteConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  title = "게시글 삭제 확인",
  description = "이 게시글을 삭제하시겠습니까?",
  requirePassword = true,
}: DeleteConfirmModalProps) {
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
    setPassword("");
    setErrorMsg("");
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <div className="w-full max-w-md rounded-lg border-2 border-black bg-white p-6 shadow-[8px_8px_0px_0px_rgba(0,0,0,1)]">
        <div className="flex items-center justify-between border-b-2 border-black pb-3">
          <h3 className="text-xl font-extrabold italic text-black">{title}</h3>
          <button
            onClick={handleClose}
            className="text-gray-400 hover:text-black font-bold text-lg"
          >
            ✕
          </button>
        </div>

        <p className="mt-4 text-sm font-medium text-[var(--snow-ink-soft)]">{description}</p>

        <form onSubmit={handleSubmit} className="mt-5 grid gap-4">
          {requirePassword && (
            <label className="grid gap-2">
              <span className="font-mono text-xs font-bold uppercase tracking-wider text-[var(--snow-muted)]">
                익명 비밀번호 (Password)
              </span>
              <input
                type="password"
                placeholder="작성 당시 등록한 비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoFocus
                className="snow-input text-sm"
              />
            </label>
          )}

          {errorMsg && (
            <div className="rounded border border-[#fecaca] bg-[#fef2f2] p-3 text-xs font-bold text-[#dc2626]">
              ⚠️ {errorMsg}
            </div>
          )}

          <div className="mt-3 flex items-center justify-end gap-3 font-bold">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-xs font-mono uppercase tracking-wider text-gray-500 hover:text-black"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="bg-[#dc2626] text-white px-5 py-2 text-xs font-mono uppercase tracking-wider hover:bg-black transition border-2 border-black disabled:opacity-50"
            >
              {submitting ? "Deleting..." : "Delete"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
