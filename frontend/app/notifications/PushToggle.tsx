"use client";

import { useEffect, useState } from "react";
import { PushState, disablePush, enablePush, readPushState } from "../lib/push";

const HINTS: Partial<Record<PushState, string>> = {
  "ios-install": "아이폰과 아이패드는 Safari 공유 버튼에서 '홈 화면에 추가'를 누른 뒤, 홈 화면의 눈팅 앱에서 켤 수 있어요.",
  unsupported: "이 브라우저는 휴대폰 알림을 지원하지 않아요.",
  denied: "알림 권한이 차단돼 있어요. 브라우저나 휴대폰 설정에서 눈팅 알림을 허용한 뒤 다시 켜 주세요.",
};

/** 알림 페이지 위쪽의 "휴대폰 알림 받기" 스위치. 서버에 키가 없으면 아무것도 보이지 않는다. */
export default function PushToggle() {
  const [state, setState] = useState<PushState>("loading");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    readPushState()
      .then((next) => { if (active) setState(next); })
      .catch(() => { if (active) setState("off"); });
    return () => { active = false; };
  }, []);

  if (state === "loading" || state === "server-off") return null;

  const on = state === "on";
  const toggle = async () => {
    setBusy(true);
    setError("");
    try {
      setState(on ? await disablePush() : await enablePush());
    } catch {
      setError(on ? "알림을 끄지 못했어요. 잠시 후 다시 시도해 주세요." : "알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };
  const hint = HINTS[state];
  const canToggle = state === "on" || state === "off";

  return (
    <section className="push-toggle" aria-label="휴대폰 알림">
      <div className="push-toggle-row">
        <div>
          <strong id="push-toggle-label">휴대폰 알림 받기</strong>
          <p>댓글, 답글, 문의 답변이 오면 이 기기로 알려 드려요.</p>
        </div>
        {canToggle && (
          <button
            type="button"
            role="switch"
            aria-checked={on}
            aria-labelledby="push-toggle-label"
            className={`push-switch${on ? " on" : ""}`}
            onClick={() => void toggle()}
            disabled={busy}
          >
            <span aria-hidden="true" />
          </button>
        )}
      </div>
      {hint && <p className="push-toggle-hint">{hint}</p>}
      {error && <p className="push-toggle-hint error" role="alert">{error}</p>}
    </section>
  );
}
