"use client";

import { useId, useState, type FormEvent } from "react";

const NICKNAME_PATTERN = /^[a-zA-Z0-9가-힣]{2,10}$/;
// 서버와 같은 규칙. 앞뒤 공백을 빼고 겹친 공백을 하나로 줄인 뒤 본다.
const GROUP_NAME_PATTERN = /^[a-zA-Z0-9가-힣 ]{2,20}$/;
const GROUP_NAME_MAX = 20;

// withGroupName이면 모임을 만드는 화면이라 모임 이름(선택) 칸을 함께 보여 준다. 비우면 서버가 "이름의 모임"을 붙인다.
export function NicknameForm({ submitLabel, initialNickname = "", onSubmit, onCancel, withGroupName = false }: { submitLabel: string; initialNickname?: string; onSubmit: (nickname: string, groupName?: string) => Promise<void>; onCancel?: () => void; withGroupName?: boolean }) {
  // 한 화면에 입력 칸이 둘 열릴 수 있어 id가 겹치지 않게 한다.
  const inputId = useId();
  const errorId = `${inputId}-error`;
  const noticeId = `${inputId}-notice`;
  const groupInputId = `${inputId}-group`;
  const [nickname, setNickname] = useState(initialNickname);
  const [groupName, setGroupName] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (pending) return;
    if (!NICKNAME_PATTERN.test(nickname)) {
      setError("이름은 2자 이상 10자 이하의 한글, 영문, 숫자로 입력해 주세요.");
      return;
    }
    const cleanGroupName = groupName.trim().replace(/\s+/g, " ");
    if (withGroupName && cleanGroupName && !GROUP_NAME_PATTERN.test(cleanGroupName)) {
      setError("모임 이름은 2자 이상 20자 이하의 한글, 영문, 숫자, 띄어쓰기로 입력해 주세요.");
      return;
    }
    setError("");
    setPending(true);
    try {
      await onSubmit(nickname, withGroupName && cleanGroupName ? cleanGroupName : undefined);
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
      {withGroupName && (
        <>
          <label htmlFor={groupInputId}>모임 이름 <span className="ct-optional">(선택)</span></label>
          <input id={groupInputId} value={groupName} onChange={(event) => setGroupName(event.target.value)} maxLength={GROUP_NAME_MAX} autoComplete="off" placeholder={`비우면 ${NICKNAME_PATTERN.test(nickname) ? nickname : "내 이름"}의 모임`} />
          <p className="ct-fineprint">초대 링크 미리보기에 이 이름이 보여요. 예: 주말 하이원 크루</p>
        </>
      )}
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
