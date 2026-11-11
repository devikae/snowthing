"use client";

import { useId, useState, type FormEvent } from "react";

const NICKNAME_PATTERN = /^[a-zA-Z0-9가-힣]{2,10}$/;

export function NicknameForm({ submitLabel, initialNickname = "", onSubmit, onCancel }: { submitLabel: string; initialNickname?: string; onSubmit: (nickname: string) => Promise<void>; onCancel?: () => void }) {
  // 한 화면에 입력 칸이 둘 열릴 수 있어 id가 겹치지 않게 한다.
  const inputId = useId();
  const errorId = `${inputId}-error`;
  const noticeId = `${inputId}-notice`;
  const [nickname, setNickname] = useState(initialNickname);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (pending) return;
    if (!NICKNAME_PATTERN.test(nickname)) {
      setError("이름은 2자 이상 10자 이하의 한글, 영문, 숫자로 입력해 주세요.");
      return;
    }
    setError("");
    setPending(true);
    try {
      await onSubmit(nickname);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  };

  return (
    <form className="ct-nickname" onSubmit={(event) => void submit(event)} noValidate>
      <label htmlFor={inputId}>이름</label>
      <input id={inputId} value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={10} autoComplete="off" autoFocus aria-invalid={Boolean(error)} aria-describedby={error ? errorId : noticeId} />
      {error && <p className="ct-error" id={errorId} role="alert">{error}</p>}
      <ul className="ct-join-notice" id={noticeId}>
        <li>이름, 스키나 보드, 답변이 지도를 보여 주려고 서버에 저장돼요.</li>
        <li>같은 모임 사람들은 이름과 캐릭터, 나와의 궁합 점수를 볼 수 있어요.</li>
        <li><strong>나가기를 누르면 바로 지워져요.</strong></li>
        <li><strong>30일 동안 새로 들어온 사람이 없으면 모임이 통째로 지워져요.</strong></li>
      </ul>
      <div className="ct-actions">
        <button type="submit" className="ct-primary" disabled={pending}>{pending ? "처리 중" : submitLabel}</button>
        {onCancel && <button type="button" onClick={onCancel} disabled={pending}>취소</button>}
      </div>
    </form>
  );
}
