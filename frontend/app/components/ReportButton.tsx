"use client";

import { useState } from "react";
import { createPortal } from "react-dom";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { readErrorMessage } from "../lib/http";
import { blockMember } from "../lib/messageApi";

const REASONS: [string, string][] = [
  ["RIGHTS_INFRINGEMENT", "명예훼손, 사생활 침해, 저작권 침해"],
  ["ILLEGAL_CONTENT", "불법촬영물, 음란물 등 불법 정보"],
  ["FRAUD", "사기, 허위 거래"],
  ["SPAM", "도배, 광고"],
  ["OTHER", "기타"],
];

type ReportTargetType = "POST" | "COMMENT" | "MESSAGE";

// blockTarget을 주면 신고 창에 "이 회원 차단하기"를 기본으로 켜 두고, 신고가 접수된 뒤 차단까지 한다 (IKA-123, 받은 쪽지).
export function ReportButton({ targetType, targetId, className, blockTarget, onBlocked }: {
  targetType: ReportTargetType;
  targetId: string;
  className?: string;
  blockTarget?: { publicId: string; nickname: string };
  onBlocked?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0][0]);
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [done, setDone] = useState(false);
  const [alsoBlock, setAlsoBlock] = useState(true);
  const isMessage = targetType === "MESSAGE";

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true); setMessage("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.reports.create, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ targetType, targetId, reason, detail }) });
      if (response.status === 401 || response.status === 403) { setMessage("신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요."); return; }
      if (!response.ok) { setMessage(await readErrorMessage(response, "신고를 접수하지 못했습니다.")); return; }
      setDone(true);
      // 쪽지 신고는 관리자가 확인만 하고 결과 알림을 보내지 않는다.
      const received = isMessage ? "신고를 접수했습니다." : "신고를 접수했습니다. 로그인한 회원에게는 확인 결과를 알림으로 알려 드립니다.";
      if (!blockTarget || !alsoBlock) { setMessage(received); return; }
      try {
        await blockMember(blockTarget.publicId);
        onBlocked?.();
        setMessage(`${received} ${blockTarget.nickname}님도 차단했습니다.`);
      } catch {
        // 신고는 이미 접수됐으므로 되돌리지 않고 차단만 실패했다고 알린다.
        setMessage(`${received} 차단은 하지 못했습니다. 쪽지 화면의 차단 버튼으로 다시 시도해 주세요.`);
      }
    } catch { setMessage("신고를 접수하지 못했습니다."); }
    finally { setBusy(false); }
  };

  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)}>신고</button>
      {/* 부모의 버튼 CSS가 창 안의 버튼을 덮어쓰지 않게 body에 그린다. */}
      {open && createPortal(<dialog ref={(element) => { if (element && !element.open) element.showModal(); }} onClose={() => setOpen(false)} aria-label="신고하기" className="m-auto w-[min(92vw,420px)] rounded-xl border border-[var(--snow-border)] bg-[var(--snow-panel)] p-5 text-left text-sm text-[var(--snow-ink)]">
        <form onSubmit={submit} className="grid gap-3">
          <h2 className="text-base font-bold">신고하기</h2>
          <label className="grid gap-1 font-semibold">사유
            <select value={reason} disabled={done} onChange={(event) => setReason(event.target.value)} className="snow-input">
              {REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="grid gap-1 font-semibold">내용 (선택)
            <textarea value={detail} disabled={done} maxLength={500} rows={3} onChange={(event) => setDetail(event.target.value)} className="snow-input" />
          </label>
          {blockTarget && (
            <label className="flex items-center gap-2 font-semibold">
              <input type="checkbox" checked={alsoBlock} disabled={done} onChange={(event) => setAlsoBlock(event.target.checked)} />
              이 회원 차단하기 (서로 쪽지를 보낼 수 없게 됩니다)
            </label>
          )}
          <p className="text-xs leading-5 text-[var(--snow-muted)]">신고자는 {isMessage ? "보낸 회원" : "작성자"}에게 공개되지 않습니다. 거짓 신고는 이용이 제한될 수 있습니다.</p>
          {message && <p role="status" className="font-semibold">{message}</p>}
          <div className="flex justify-end gap-2">
            <button type="button" className="snow-btn-secondary" onClick={() => setOpen(false)}>닫기</button>
            {!done && <button type="submit" className="snow-btn-primary" disabled={busy}>{busy ? "접수 중" : "신고"}</button>}
          </div>
        </form>
      </dialog>, document.body)}
    </>
  );
}
