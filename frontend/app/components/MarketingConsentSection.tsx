"use client";

import { useEffect, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";

interface MarketingConsent { agreed: boolean; changedAt: string | null; }

export function MarketingConsentSection() {
  const [consent, setConsent] = useState<MarketingConsent | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    void fetch(API_ENDPOINTS.members.marketingConsent, { credentials: "include" })
      .then(async (response) => { if (response.ok) setConsent(await response.json()); else setMessage("수신 설정을 불러오지 못했습니다."); })
      .catch(() => setMessage("수신 설정을 불러오지 못했습니다."));
  }, []);

  const change = async (agreed: boolean) => {
    setBusy(true); setMessage("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.marketingConsent, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ agreed }) });
      if (!response.ok) throw new Error();
      const next: MarketingConsent = await response.json();
      setConsent(next);
      setMessage(`SnowThing 광고성 이메일 수신 ${next.agreed ? "동의" : "거부"}가 ${next.changedAt?.slice(0, 10) ?? ""} 처리되었습니다.`);
    } catch { setMessage("수신 설정을 변경하지 못했습니다. 다시 시도해 주세요."); }
    finally { setBusy(false); }
  };

  return <section className="profile-section"><div><h2>광고성 이메일</h2><p>이벤트와 혜택 안내 메일의 수신 여부를 정합니다.</p></div><div className="profile-fields">
    <label className="profile-password-toggle"><input type="checkbox" checked={consent?.agreed ?? false} disabled={!consent || busy} onChange={event => void change(event.target.checked)} />광고성 이메일 수신에 동의합니다</label>
    <small role="status">{message || (consent?.changedAt ? `마지막 처리일: ${consent.changedAt.slice(0, 10)}` : "동의한 기록이 없습니다.")}</small>
  </div></section>;
}
