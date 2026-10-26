"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS, SocialLoginProvider } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";

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
  OAUTH_RATE_LIMIT: "소셜 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
};

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [errorMsg, setErrorMsg] = useState(() => {
    if (typeof window === "undefined") {
      return "";
    }

    const oauthError = new URLSearchParams(window.location.search).get("error");
    return oauthError
      ? OAUTH_ERROR_MESSAGES[oauthError] ||
          "소셜 로그인 중 오류가 발생했습니다. 다시 시도해 주세요."
      : "";
  });
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
        const errorData = await response.json();
        throw new Error(errorData.message || errorData.error || "이메일 또는 비밀번호가 올바르지 않습니다.");
      }

      const returnUrl = new URLSearchParams(window.location.search).get("returnUrl");
      router.push(returnUrl?.startsWith("/") && !returnUrl.startsWith("//") ? returnUrl : "/");
    } catch (error) {
      setErrorMsg(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="community-page">
      <TopNav active="login" />
      <main className="community-container flex min-h-[calc(100vh-160px)] items-center py-10 md:py-16">
        <section className="grid w-full overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_18px_50px_rgba(15,41,66,0.08)] lg:grid-cols-[1.05fr_0.95fr]">
          <div className="relative hidden min-h-[560px] overflow-hidden bg-[#0b1e32] p-10 text-white lg:flex lg:flex-col lg:justify-between">
            <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full border-[34px] border-[#1e4668] opacity-70" />
            <div className="absolute -bottom-32 -left-24 h-80 w-80 rounded-full border-[44px] border-[#123b5c] opacity-80" />
            <div className="relative z-10">
              <span className="mb-8 inline-flex items-center gap-2 rounded-full border border-sky-300/30 bg-sky-200/10 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.16em] text-sky-200">SnowThing Community</span>
              <h1 className="max-w-md text-5xl font-black leading-[1.05] tracking-[-0.06em]">다시 슬로프에<br />오를 준비를 해요.</h1>
              <p className="mt-6 max-w-sm text-sm leading-7 text-slate-300">리조트 정보부터 장비 세팅, 라이딩 동행까지<br />겨울을 기다리는 사람들의 이야기를 만나보세요.</p>
            </div>
            <div className="relative z-10 flex items-end justify-between gap-6 text-xs text-slate-400"><span>RIDE MORE · WAIT LESS</span><span className="text-sky-200">01 / 24</span></div>
          </div>

          <div className="flex items-center justify-center p-6 sm:p-10 lg:p-14">
            <div className="w-full max-w-[390px]">
              <div className="mb-9"><p className="mb-3 text-xs font-bold uppercase tracking-[0.18em] text-sky-600">Welcome back</p><h2 className="text-3xl font-black tracking-[-0.06em] text-[#0b1e32]">로그인</h2><p className="mt-3 text-sm leading-6 text-slate-500">SnowThing에서 라이딩 이야기를 계속 이어가세요.</p></div>
              {errorMsg && <div className="mb-5 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-semibold leading-6 text-rose-700" role="alert">{errorMsg}</div>}
              <form onSubmit={handleSubmit} className="grid gap-5">
                <label className="grid gap-2"><span className="text-xs font-bold text-slate-600">이메일</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="user@snowthing.com" className="h-12 rounded-lg border border-slate-200 bg-slate-50 px-4 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-500 focus:bg-white focus:ring-4 focus:ring-sky-100" autoComplete="email" required /></label>
                <label className="grid gap-2"><span className="text-xs font-bold text-slate-600">비밀번호</span><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호를 입력하세요" className="h-12 rounded-lg border border-slate-200 bg-slate-50 px-4 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-sky-500 focus:bg-white focus:ring-4 focus:ring-sky-100" autoComplete="current-password" required /></label>
                <div className="flex items-center justify-between gap-3"><label className="flex cursor-pointer items-center gap-2 text-sm text-slate-500"><input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500" />로그인 상태 유지</label><Link href="/forgot-password" className="text-sm font-bold text-sky-700 hover:text-sky-900">비밀번호 찾기</Link></div>
                <button type="submit" className="mt-1 flex h-12 items-center justify-center rounded-lg bg-[#0b1e32] px-5 text-sm font-bold text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={loading}>{loading ? "로그인 중..." : "로그인"}</button>
              </form>
              <div className="my-6 flex items-center gap-3 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-400"><span className="h-px flex-1 bg-slate-200" />간편 로그인<span className="h-px flex-1 bg-slate-200" /></div>
              <div className="grid gap-2.5">
                <button
                  type="button"
                  onClick={() => handleSocialLogin("naver")}
                  className="flex h-11 w-full items-center justify-center gap-2.5 rounded-lg bg-[#03C75A] px-4 text-sm font-bold text-white transition hover:brightness-95"
                >
                  <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M16.273 12.845 7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727v12.845z" />
                  </svg>
                  네이버로 계속하기
                </button>
                <button
                  type="button"
                  onClick={() => handleSocialLogin("google")}
                  className="flex h-11 w-full items-center justify-center gap-2.5 rounded-lg border border-slate-200 bg-white px-4 text-sm font-bold text-slate-700 transition hover:bg-slate-50"
                >
                  <svg className="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
                    <path fill="#4285F4" d="M23.49 12.275c0-.85-.076-1.667-.218-2.455H12v4.642h6.442c-.278 1.498-1.122 2.768-2.392 3.618v3.008h3.873c2.266-2.086 3.567-5.158 3.567-8.813z" />
                    <path fill="#34A853" d="M12 24c3.24 0 5.956-1.074 7.942-2.912l-3.873-3.008c-1.074.72-2.448 1.145-4.069 1.145-3.13 0-5.78-2.114-6.726-4.955H1.27v3.104C3.245 21.295 7.305 24 12 24z" />
                    <path fill="#FBBC05" d="M5.274 14.27A7.218 7.218 0 0 1 4.896 12c0-.79.136-1.555.378-2.27V6.626H1.27A11.996 11.996 0 0 0 0 12c0 1.936.464 3.768 1.27 5.374l4.004-3.104z" />
                    <path fill="#EA4335" d="M12 4.775c1.762 0 3.344.606 4.588 1.794l3.442-3.442C17.95 1.19 15.235 0 12 0 7.305 0 3.245 2.705 1.27 6.626L5.274 9.73C6.22 6.89 8.87 4.775 12 4.775z" />
                  </svg>
                  Google로 계속하기
                </button>
              </div>
              <p className="mt-7 text-center text-sm text-slate-500">아직 계정이 없나요? <Link href="/signup" className="font-bold text-sky-700 underline decoration-sky-300 underline-offset-4 hover:text-sky-900">회원가입</Link></p>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
