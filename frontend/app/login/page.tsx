"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

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

      router.push("/");
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
                <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-500"><input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} className="h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500" />로그인 상태 유지</label>
                <button type="submit" className="mt-1 flex h-12 items-center justify-center rounded-lg bg-[#0b1e32] px-5 text-sm font-bold text-white transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-50" disabled={loading}>{loading ? "로그인 중..." : "로그인"}</button>
              </form>
              <div className="my-8 flex items-center gap-3 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-300"><span className="h-px flex-1 bg-slate-200" />SnowThing<span className="h-px flex-1 bg-slate-200" /></div>
              <p className="text-center text-sm text-slate-500">아직 계정이 없나요? <Link href="/signup" className="font-bold text-sky-700 underline decoration-sky-300 underline-offset-4 hover:text-sky-900">회원가입</Link></p>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
