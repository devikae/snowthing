"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { API_ENDPOINTS, SocialLoginProvider } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { errorText } from "../lib/errorText";

type Provider = "KAKAO" | "NAVER" | "GOOGLE";
interface SocialAccounts { passwordLogin: boolean; accounts: { provider: Provider; connectedAt: string }[]; }

const PROVIDERS: { key: Provider; label: string; path: SocialLoginProvider }[] = [
  { key: "KAKAO", label: "카카오", path: "kakao" },
  { key: "NAVER", label: "네이버", path: "naver" },
  { key: "GOOGLE", label: "구글", path: "google" },
];

// 연결 콜백은 화면 이동으로 끝나므로 서버가 오류 코드를 주소에 실어 보낸다.
const LINK_ERROR_MESSAGES: Record<string, string> = {
  SOCIAL_ACCOUNT_IN_USE: "이미 다른 눈팅 계정에서 쓰고 있는 소셜 계정입니다. 이 계정에 연결하려면 그 계정을 먼저 탈퇴해 주세요.",
  SOCIAL_PROVIDER_ALREADY_LINKED: "이미 연결된 로그인 방법입니다. 다른 계정으로 바꾸려면 먼저 연결을 해제해 주세요.",
  SOCIAL_LINK_SESSION_MISMATCH: "연결을 시작한 계정과 지금 로그인한 계정이 다릅니다. 다시 시도해 주세요.",
  OAUTH_EMAIL_REQUIRED: "소셜 계정의 이메일 제공에 동의해야 연결할 수 있습니다.",
  OAUTH_PROVIDER_NOT_CONFIGURED: "현재 해당 소셜 로그인 설정이 준비되지 않았습니다.",
  OAUTH_AUTHENTICATION_FAILED: "소셜 계정 인증에 실패했습니다. 잠시 후 다시 시도해 주세요.",
  ACCOUNT_SUSPENDED: "이용이 정지된 계정입니다.",
};

const labelOf = (provider: string) => PROVIDERS.find(x => x.key === provider)?.label ?? provider;

async function message(response: Response, fallback: string) {
  const data = await response.json().catch(() => null);
  return data?.message ?? fallback;
}

export function SocialAccountsSection() {
  const [data, setData] = useState<SocialAccounts | null>(null);
  const [busy, setBusy] = useState<Provider | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  // 비밀번호가 있는 회원은 연결 전에 현재 비밀번호를 한 번 더 받는다. 소셜 전용 회원은 서버가 최근 로그인 여부로 확인한다.
  const [confirming, setConfirming] = useState<Provider | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");

  const load = useCallback(async () => {
    try {
      const response = await fetch(API_ENDPOINTS.members.socialAccounts, { credentials: "include" });
      if (!response.ok) throw new Error(await message(response, "로그인 방법을 불러오지 못했습니다."));
      setData(await response.json());
    } catch (e) { setError(errorText(e, "로그인 방법을 불러오지 못했습니다.")); }
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const linked = params.get("socialLinked");
    const linkError = params.get("socialLinkError");
    if (linked || linkError) {
      params.delete("socialLinked"); params.delete("socialLinkError");
      const query = params.toString();
      window.history.replaceState(null, "", `${window.location.pathname}${query ? `?${query}` : ""}`);
    }
    const timer = window.setTimeout(() => {
      if (linked) setNotice(`${labelOf(linked)} 로그인을 연결했습니다.`);
      if (linkError) setError(LINK_ERROR_MESSAGES[linkError] ?? "소셜 계정을 연결하지 못했습니다. 다시 시도해 주세요.");
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const linkedCount = data?.accounts.length ?? 0;
  const loginMethods = linkedCount + (data?.passwordLogin ? 1 : 0);

  const startLink = async (provider: (typeof PROVIDERS)[number], password?: string) => {
    if (busy) return;
    setBusy(provider.key); setNotice(""); setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.oauth2Link(provider.path), password === undefined
        ? { method: "POST" }
        : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ currentPassword: password }) });
      if (!response.ok) throw new Error(await message(response, "소셜 계정 연결을 시작하지 못했습니다."));
      const { authorizationUrl }: { authorizationUrl: string } = await response.json();
      setCurrentPassword("");
      window.location.assign(authorizationUrl);
    } catch (e) { setError(errorText(e, "소셜 계정 연결을 시작하지 못했습니다.")); setBusy(null); }
  };

  const link = (provider: (typeof PROVIDERS)[number]) => {
    if (busy) return;
    if (data?.passwordLogin) {
      setConfirming(provider.key); setCurrentPassword(""); setNotice(""); setError("");
      return;
    }
    void startLink(provider);
  };

  const confirmLink = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const provider = PROVIDERS.find(x => x.key === confirming);
    if (!provider || !currentPassword) return;
    void startLink(provider, currentPassword);
  };

  const cancelConfirm = () => { setConfirming(null); setCurrentPassword(""); setError(""); };

  const unlink = async (provider: (typeof PROVIDERS)[number]) => {
    if (busy || !window.confirm(`${provider.label} 로그인 연결을 해제할까요?`)) return;
    setBusy(provider.key); setNotice(""); setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.socialAccount(provider.path), { method: "DELETE" });
      if (!response.ok) throw new Error(await message(response, "연결을 해제하지 못했습니다."));
      setNotice(`${provider.label} 로그인 연결을 해제했습니다.`);
      await load();
    } catch (e) { setError(errorText(e, "연결을 해제하지 못했습니다.")); }
    finally { setBusy(null); }
  };

  return <section className="profile-section"><div><h2>로그인 연결</h2><p>다른 소셜 계정으로도 이 계정에 로그인할 수 있습니다.</p></div><div className="profile-fields">
    {data && <ul className="profile-social-list">{PROVIDERS.map(provider => {
      const account = data.accounts.find(x => x.provider === provider.key);
      const lastMethod = Boolean(account) && loginMethods <= 1;
      return <li key={provider.key}>
        <div><strong>{provider.label}</strong><small>{account ? `연결됨, ${account.connectedAt.slice(0, 10)}` : "연결 안 됨"}</small></div>
        {account
          ? <button type="button" disabled={!!busy || lastMethod} title={lastMethod ? "마지막 남은 로그인 방법은 해제할 수 없습니다." : undefined} onClick={() => void unlink(provider)}>{busy === provider.key ? "해제 중" : "해제"}</button>
          : <button type="button" className="profile-button-primary" disabled={!!busy || confirming === provider.key} onClick={() => link(provider)}>{busy === provider.key ? "이동 중" : "연결"}</button>}
      </li>;
    })}</ul>}
    {confirming && <form className="profile-social-confirm" onSubmit={confirmLink}>
      <label>
        {labelOf(confirming)} 연결 전에 현재 비밀번호를 입력해 주세요.
        <input type="password" autoComplete="current-password" maxLength={200} required autoFocus value={currentPassword} disabled={!!busy} onChange={event => setCurrentPassword(event.target.value)} />
      </label>
      <div className="profile-actions">
        <button type="button" className="profile-button-cancel" disabled={!!busy} onClick={cancelConfirm}>취소</button>
        <button type="submit" className="profile-button-primary" disabled={!!busy || !currentPassword}>{busy ? "확인 중" : "확인하고 연결"}</button>
      </div>
    </form>}
    <small role="status">{error || notice || (data && loginMethods <= 1 ? "마지막 남은 로그인 방법은 해제할 수 없습니다." : "")}</small>
  </div></section>;
}
