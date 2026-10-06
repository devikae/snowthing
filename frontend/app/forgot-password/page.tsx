"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";

const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;

async function errorMessage(response: Response, fallback: string) {
  try { const body = await response.json(); return body.message || body.error || fallback; }
  catch { return fallback; }
}

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [requestId, setRequestId] = useState("");
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const requestCode = async () => {
    setError(""); setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.requestPasswordReset, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      if (!response.ok) throw new Error(await errorMessage(response, "인증번호 요청에 실패했습니다."));
      const body = await response.json();
      setRequestId(body.requestId);
      setNotice("입력한 이메일이 가입된 계정과 일치하면 인증번호가 발송됩니다. 메일을 확인해 주세요.");
    } catch (caught) { setError(caught instanceof Error ? caught.message : "요청을 처리하지 못했습니다."); }
    finally { setLoading(false); }
  };

  const confirmCode = async () => {
    setError(""); setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.confirmPasswordReset, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ requestId, code }) });
      if (!response.ok) throw new Error(await errorMessage(response, "인증번호가 올바르지 않습니다."));
      const body = await response.json();
      setResetToken(body.resetToken);
      setNotice("인증되었습니다. 새 비밀번호를 설정해 주세요.");
    } catch (caught) { setError(caught instanceof Error ? caught.message : "인증번호를 확인하지 못했습니다."); }
    finally { setLoading(false); }
  };

  const resetPassword = async () => {
    setError("");
    if (!PASSWORD_REGEX.test(password)) return setError("비밀번호는 8자 이상이며 영문 대문자와 특수문자를 포함해야 합니다.");
    if (password !== passwordConfirm) return setError("비밀번호 확인이 일치하지 않습니다.");
    setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.resetPassword, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ resetToken, newPassword: password }) });
      if (!response.ok) throw new Error(await errorMessage(response, "비밀번호를 변경하지 못했습니다."));
      setNotice("비밀번호가 변경되었습니다. 모든 기존 로그인 세션이 종료되었습니다.");
      setResetToken("completed");
      router.replace("/login");
    } catch (caught) { setError(caught instanceof Error ? caught.message : "비밀번호를 변경하지 못했습니다."); }
    finally { setLoading(false); }
  };

  return <div className="min-h-screen bg-[var(--snow-background)]"><TopNav active="login" /><main className="snow-container px-5 py-14"><section className="snow-card mx-auto grid max-w-lg gap-6 bg-white p-7"><div><h1 className="text-2xl font-black text-[var(--snow-ink)]">비밀번호 재설정</h1><p className="mt-2 text-sm text-[var(--snow-muted)]">가입 이메일로 받은 6자리 인증번호를 확인합니다.</p></div>{error && <div role="alert" className="rounded border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</div>}{notice && <div className="rounded border border-sky-200 bg-sky-50 p-3 text-sm text-sky-800">{notice}</div>}
    {!requestId && <div className="grid gap-3"><input type="email" className="snow-input" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="가입 이메일" /><button type="button" className="snow-btn-primary" onClick={requestCode} disabled={loading || !email}>이메일 인증</button></div>}
    {requestId && !resetToken && <div className="grid gap-3"><input inputMode="numeric" maxLength={6} className="snow-input" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))} placeholder="인증번호 6자리" /><button type="button" className="snow-btn-primary" onClick={confirmCode} disabled={loading || code.length !== 6}>인증 확인</button></div>}
    {resetToken && resetToken !== "completed" && <div className="grid gap-3"><input type="password" className="snow-input" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="새 비밀번호" autoComplete="new-password" /><input type="password" className="snow-input" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} placeholder="새 비밀번호 확인" autoComplete="new-password" /><button type="button" className="snow-btn-primary" onClick={resetPassword} disabled={loading}>비밀번호 변경</button></div>}
    <Link href="/login" className="text-center text-sm font-bold text-sky-700 underline">로그인으로 돌아가기</Link>
  </section></main><Footer /></div>;
}
