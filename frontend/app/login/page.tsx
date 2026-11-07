"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { safeReturnUrl } from "../lib/safeReturnUrl";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS, SocialLoginProvider } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { clearViewerCache } from "../lib/viewer";
import styles from "./login.module.css";
import { errorText } from "../lib/errorText";

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;

const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  OAUTH_PROVIDER_UNSUPPORTED: "지원하지 않는 소셜 로그인 방식입니다.",
  OAUTH_PROVIDER_NOT_CONFIGURED: "현재 해당 소셜 로그인 설정이 준비되지 않았습니다.",
  OAUTH_STATE_INVALID: "소셜 로그인 인증 유효 시간이 만료되었습니다. 다시 시도해 주세요.",
  OAUTH_AUTHENTICATION_FAILED: "소셜 로그인 인증에 실패했습니다. 잠시 후 다시 시도해 주세요.",
  OAUTH_EMAIL_REQUIRED: "소셜 계정의 이메일 제공에 동의해야 로그인할 수 있습니다.",
  OAUTH_EMAIL_NOT_VERIFIED: "인증되지 않은 소셜 이메일 계정으로는 로그인할 수 없습니다.",
  ACCOUNT_SUSPENDED: "이용이 정지된 계정입니다.",
  ACCOUNT_WITHDRAWN: "탈퇴 처리된 계정입니다.",
  WITHDRAWN_EMAIL_RESTRICTED: "탈퇴 후 30일이 지나지 않은 이메일은 다시 가입할 수 없습니다.",
  OAUTH_RATE_LIMIT: "소셜 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
  OAUTH_ACCOUNT_LINK_REQUIRED:
    "이미 가입된 이메일입니다. 기존 로그인 방법을 이용해 주세요.",
  OAUTH_SIGNUP_EXPIRED: "소셜 가입 유효 시간이 지났습니다. 다시 로그인해 주세요.",
};

const LOGIN_FAILURE_MESSAGES: Record<number, string> = {
  401: "이메일 또는 비밀번호가 올바르지 않습니다.",
  429: "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.",
};

function getErrorMessage(error: unknown) {
  return errorText(error, "요청을 처리하지 못했습니다.");
}

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");
  const [withdrawn, setWithdrawn] = useState(false);
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("withdrawn") !== "1") return;
    const timer = window.setTimeout(() => setWithdrawn(true), 0);
    return () => window.clearTimeout(timer);
  }, []);
  // 세션이 만료돼 로그인으로 온 경우 헤더가 예전 프로필을 보여 주지 않게 내 정보 캐시를 비운다.
  // 자식인 헤더의 effect가 이 페이지의 effect보다 먼저 돌기 때문에, 헤더가 그려지기 전인 첫 렌더에서 비운다.
  useState(() => clearViewerCache());
  const [fromMarket, setFromMarket] = useState(false);
  useEffect(() => {
    const returnUrl = new URLSearchParams(window.location.search).get("returnUrl") ?? "";
    if (!returnUrl.startsWith("/market")) return;
    const timer = window.setTimeout(() => setFromMarket(true), 0);
    return () => window.clearTimeout(timer);
  }, []);
  useEffect(() => {
    const oauthError = new URLSearchParams(window.location.search).get("error");
    if (!oauthError) return;
    const timer = window.setTimeout(() => setErrorMsg(
      OAUTH_ERROR_MESSAGES[oauthError] || "소셜 로그인 중 오류가 발생했습니다. 다시 시도해 주세요.",
    ), 0);
    return () => window.clearTimeout(timer);
  }, []);
  const [loading, setLoading] = useState(false);

  const handleSocialLogin = (provider: SocialLoginProvider) => {
    const returnUrl = new URLSearchParams(window.location.search).get("returnUrl");
    window.location.assign(API_ENDPOINTS.auth.oauth2Authorize(provider, returnUrl));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMsg("");

    if (!EMAIL_REGEX.test(email)) {
      setErrorMsg("올바른 이메일 형식으로 입력해 주세요. 예: user@snowthing.com");
      return;
    }

    setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.login, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, rememberMe }),
      });

      if (!response.ok) {
        // 프록시가 HTML로 답해도 JSON 파싱 오류가 화면에 나오지 않게 상태 코드별 기본 문구를 둔다.
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.message || errorData?.error || LOGIN_FAILURE_MESSAGES[response.status] || "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }

      const returnUrl = new URLSearchParams(window.location.search).get("returnUrl");
      clearViewerCache();
      router.push(safeReturnUrl(returnUrl));
    } catch (error) {
      setErrorMsg(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`community-page ${styles.page}`}>
      <TopNav active="login" />
      <main className={`community-container ${styles.main}`}>
        <section className={styles.panel} aria-labelledby="login-heading">
          <header className={styles.heading}>
            <h1 id="login-heading">로그인</h1>
            <p>{fromMarket ? "중고장터는 로그인한 회원만 이용할 수 있어요." : "SnowThing 계정으로 로그인하세요."}</p>
          </header>
          {withdrawn && <div className={styles.notice} role="status">탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.</div>}
          {errorMsg && <div className={styles.error} role="alert">{errorMsg}</div>}
          <form onSubmit={handleSubmit} className={styles.form} aria-busy={loading}>
            <label className={styles.field}>이메일<input name="email" type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="user@snowthing.com" autoComplete="email" autoCapitalize="none" required disabled={loading} /></label>
            <label className={styles.field}>비밀번호<input name="password" type="password" value={password} onChange={event => setPassword(event.target.value)} placeholder="비밀번호를 입력해 주세요" autoComplete="current-password" required disabled={loading} /></label>
            <div className={styles.options}>
              <label className={styles.remember}><input type="checkbox" checked={rememberMe} onChange={event => setRememberMe(event.target.checked)} disabled={loading} />로그인 상태 유지</label>
              <Link href="/forgot-password">비밀번호 찾기</Link>
            </div>
            <button type="submit" className={`${styles.button} ${styles.primary}`} disabled={loading}>{loading ? "로그인 중" : "로그인"}</button>
          </form>
          <div className={styles.divider}>간편 로그인</div>
          <div className={styles.social}>
            <button type="button" className={styles.button} onClick={() => handleSocialLogin("naver")} disabled={loading}>
              <svg className={`${styles.providerIcon} ${styles.naver}`} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M16.273 12.845 7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727v12.845z" />
                  </svg>
              네이버로 계속하기
            </button>
            <button type="button" className={styles.button} onClick={() => handleSocialLogin("google")} disabled={loading}>
              <svg className={styles.providerIcon} viewBox="0 0 24 24" aria-hidden="true">
                    <path fill="#4285F4" d="M23.49 12.275c0-.85-.076-1.667-.218-2.455H12v4.642h6.442c-.278 1.498-1.122 2.768-2.392 3.618v3.008h3.873c2.266-2.086 3.567-5.158 3.567-8.813z" />
                    <path fill="#34A853" d="M12 24c3.24 0 5.956-1.074 7.942-2.912l-3.873-3.008c-1.074.72-2.448 1.145-4.069 1.145-3.13 0-5.78-2.114-6.726-4.955H1.27v3.104C3.245 21.295 7.305 24 12 24z" />
                    <path fill="#FBBC05" d="M5.274 14.27A7.218 7.218 0 0 1 4.896 12c0-.79.136-1.555.378-2.27V6.626H1.27A11.996 11.996 0 0 0 0 12c0 1.936.464 3.768 1.27 5.374l4.004-3.104z" />
                    <path fill="#EA4335" d="M12 4.775c1.762 0 3.344.606 4.588 1.794l3.442-3.442C17.95 1.19 15.235 0 12 0 7.305 0 3.245 2.705 1.27 6.626L5.274 9.73C6.22 6.89 8.87 4.775 12 4.775z" />
                  </svg>
              Google로 계속하기
            </button>
          </div>
          <p className={styles.signup}>아직 계정이 없나요?<Link href="/signup">회원가입</Link></p>
        </section>
      </main>
      <Footer />
    </div>
  );
}
