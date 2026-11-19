"use client";

import { useEffect, useState } from "react";
import { MESSAGE_ENDPOINTS } from "../lib/messageApi";
import { csrfFetch } from "../lib/csrfFetch";
import "../notifications/notifications.css";

interface NotificationSetting {
  dmEmailEnabled: boolean;
  emailDeliverable: boolean;
}

/**
 * 쪽지함 위쪽의 "쪽지 오면 메일로 알림" 스위치 (IKA-88).
 * 받을 수 있는 이메일이 없으면(미인증, 소셜 내부 주소) 켤 수 없게 막고 이유를 보여 준다.
 * 로그인이 풀려 있으면 아무것도 보이지 않는다. 쪽지 목록이 로그인 화면으로 보낸다.
 */
export default function MessageMailToggle() {
  const [setting, setSetting] = useState<NotificationSetting | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    fetch(MESSAGE_ENDPOINTS.notificationSettings, { credentials: "include", cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) return null;
        return (await response.json()) as NotificationSetting;
      })
      .then((next) => { if (active && next) setSetting(next); })
      .catch(() => { if (active) setSetting(null); });
    return () => { active = false; };
  }, []);

  if (!setting) return null;

  const on = setting.dmEmailEnabled && setting.emailDeliverable;
  const toggle = async () => {
    setBusy(true);
    setError("");
    try {
      const response = await csrfFetch(MESSAGE_ENDPOINTS.notificationSettings, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dmEmailEnabled: !on }),
      });
      if (!response.ok) throw new Error();
      setSetting((await response.json()) as NotificationSetting);
    } catch {
      setError(on ? "메일 알림을 끄지 못했어요. 잠시 후 다시 시도해 주세요." : "메일 알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="push-toggle" aria-label="쪽지 메일 알림">
      <div className="push-toggle-row">
        <div>
          <strong id="message-mail-toggle-label">쪽지 오면 메일로 알림</strong>
          <p>새 쪽지가 오면 가입한 이메일로 알려 드려요. 쪽지 내용은 메일에 넣지 않고, 연달아 오면 10분 동안 모아서 한 번에 보내요.</p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={on}
          aria-labelledby="message-mail-toggle-label"
          className={`push-switch${on ? " on" : ""}`}
          onClick={() => void toggle()}
          disabled={busy || !setting.emailDeliverable}
        >
          <span aria-hidden="true" />
        </button>
      </div>
      {!setting.emailDeliverable && (
        <p className="push-toggle-hint">메일을 받을 수 있는 인증된 이메일이 없어 메일 알림을 켤 수 없어요.</p>
      )}
      {error && <p className="push-toggle-hint error" role="alert">{error}</p>}
    </section>
  );
}
