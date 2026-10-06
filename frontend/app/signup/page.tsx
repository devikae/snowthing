"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS } from "../lib/api";

interface ResortMaster { id: number; name: string; regionName: string; }
interface RidingStyleMaster { id: number; styleName: string; description: string; }
interface VerificationSendResponse { requestId: string; expiresInSeconds: number; resendAvailableInSeconds: number; }
interface VerificationTokenResponse { verificationToken: string; expiresInSeconds: number; }

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;

async function readError(response: Response, fallback: string) {
  try {
    const body = await response.json();
    return body.message || body.error || fallback;
  } catch {
    return fallback;
  }
}

export default function SignUpPage() {
  const router = useRouter();
  const emailSectionRef = useRef<HTMLDivElement>(null);
  const emailInputRef = useRef<HTMLInputElement>(null);
  const [email, setEmail] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationRequestId, setVerificationRequestId] = useState("");
  const [emailVerificationToken, setEmailVerificationToken] = useState("");
  const [resendSeconds, setResendSeconds] = useState(0);
  const [verificationExpiresSeconds, setVerificationExpiresSeconds] = useState(0);
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [nickname, setNickname] = useState("");
  const [departureRegion, setDepartureRegion] = useState("");
  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [ridingStyles, setRidingStyles] = useState<RidingStyleMaster[]>([]);
  const [selectedResortIds, setSelectedResortIds] = useState<number[]>([]);
  const [selectedStyleIds, setSelectedStyleIds] = useState<number[]>([]);
  const [notice, setNotice] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [emailVerificationError, setEmailVerificationError] = useState("");
  const [loading, setLoading] = useState(false);

  const isEmailVerified = emailVerificationToken.length > 0;
  const isPasswordValid = PASSWORD_REGEX.test(password);
  const isPasswordMatch = password.length > 0 && password === passwordConfirm;

  useEffect(() => {
    void Promise.all([fetch(API_ENDPOINTS.master.resorts), fetch(API_ENDPOINTS.master.ridingStyles)])
      .then(async ([resortResponse, styleResponse]) => {
        if (resortResponse.ok) setResorts(await resortResponse.json());
        if (styleResponse.ok) setRidingStyles(await styleResponse.json());
      })
      .catch(() => setErrorMsg("선택 정보를 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    if (resendSeconds <= 0) return;
    const timer = window.setInterval(() => setResendSeconds((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  useEffect(() => {
    if (verificationExpiresSeconds <= 0 || isEmailVerified) return;
    const timer = window.setInterval(
      () => setVerificationExpiresSeconds((current) => Math.max(0, current - 1)),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [verificationExpiresSeconds, isEmailVerified]);

  const changeEmail = (value: string) => {
    setEmail(value);
    setVerificationRequestId("");
    setEmailVerificationToken("");
    setVerificationCode("");
    setResendSeconds(0);
    setVerificationExpiresSeconds(0);
    setNotice("");
    setEmailVerificationError("");
  };

  const requestVerification = async () => {
    setErrorMsg(""); setEmailVerificationError(""); setNotice("");
    if (!EMAIL_REGEX.test(email)) return setErrorMsg("올바른 이메일 형식으로 입력해 주세요.");
    setLoading(true);
    try {
      const availabilityResponse = await csrfFetch(API_ENDPOINTS.members.emailAvailability, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      if (!availabilityResponse.ok) throw new Error(await readError(availabilityResponse, "이메일 중복 확인에 실패했습니다."));
      const availability = await availabilityResponse.json();
      if (!availability.available) throw new Error("이미 가입된 이메일입니다.");
      const response = await csrfFetch(API_ENDPOINTS.auth.requestSignUpVerification, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      if (!response.ok) throw new Error(await readError(response, "인증번호를 보내지 못했습니다."));
      const body: VerificationSendResponse = await response.json();
      setVerificationRequestId(body.requestId);
      setResendSeconds(body.resendAvailableInSeconds);
      setVerificationExpiresSeconds(body.expiresInSeconds);
      setNotice("인증번호를 보냈습니다. 5분 안에 입력해 주세요.");
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "인증 요청을 처리하지 못했습니다.");
    } finally { setLoading(false); }
  };

  const confirmVerification = async () => {
    setErrorMsg("");
    if (!verificationRequestId || !/^\d{6}$/.test(verificationCode)) return setErrorMsg("이메일로 받은 숫자 6자리를 입력해 주세요.");
    setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.confirmSignUpVerification, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ requestId: verificationRequestId, code: verificationCode }) });
      if (!response.ok) throw new Error(await readError(response, "인증번호를 확인하지 못했습니다."));
      const body: VerificationTokenResponse = await response.json();
      setEmailVerificationToken(body.verificationToken);
      setVerificationExpiresSeconds(0);
      setEmailVerificationError("");
      setNotice("이메일 인증이 완료되었습니다.");
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "인증번호 확인에 실패했습니다.");
    } finally { setLoading(false); }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault(); setErrorMsg("");
    if (!isEmailVerified) {
      setEmailVerificationError("이메일 인증이 필요합니다.");
      emailSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
      emailInputRef.current?.focus({ preventScroll: true });
      return;
    }
    if (!isPasswordValid) return setErrorMsg("비밀번호는 8자 이상이며 영문 대문자와 특수문자를 포함해야 합니다.");
    if (!isPasswordMatch) return setErrorMsg("비밀번호 확인이 일치하지 않습니다.");
    if (!nickname.trim()) return setErrorMsg("닉네임을 입력해 주세요.");
    setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.signup, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, emailVerificationToken, password, nickname, departureRegion, resortIds: selectedResortIds, ridingStyleIds: selectedStyleIds }) });
      if (!response.ok) throw new Error(await readError(response, "회원가입에 실패했습니다."));
      const loginResponse = await csrfFetch(API_ENDPOINTS.auth.login, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password, rememberMe: true }) });
      router.push(loginResponse.ok ? "/" : "/login");
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "회원가입을 처리하지 못했습니다.");
    } finally { setLoading(false); }
  };

  const toggle = (id: number, values: number[], setter: React.Dispatch<React.SetStateAction<number[]>>) => setter(values.includes(id) ? values.filter((value) => value !== id) : [...values, id]);

  return <div className="min-h-screen bg-[var(--snow-background)]"><TopNav active="signup" /><main className="snow-container px-5 py-10 lg:px-8"><section className="mx-auto max-w-2xl"><header className="mb-6 text-center"><h1 className="text-3xl font-extrabold text-[var(--snow-ink)]">회원가입</h1></header><form onSubmit={handleSubmit} noValidate className="snow-card grid gap-7 bg-white p-6 md:p-8">
    {errorMsg && <div role="alert" className="rounded border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-700">{errorMsg}</div>}
    {notice && <div className="rounded border border-sky-200 bg-sky-50 p-4 text-sm font-semibold text-sky-800">{notice}</div>}
    <div ref={emailSectionRef} className="grid gap-3"><RequiredLabel>이메일</RequiredLabel><div className="flex gap-2"><input ref={emailInputRef} type="email" value={email} onChange={(event) => changeEmail(event.target.value)} className="snow-input flex-1" placeholder="user@snowthing.org" disabled={isEmailVerified} required aria-invalid={emailVerificationError ? true : undefined} aria-describedby={emailVerificationError ? "email-verification-error" : undefined} /><button type="button" className="snow-btn-secondary whitespace-nowrap" onClick={requestVerification} disabled={loading || isEmailVerified || resendSeconds > 0}>{isEmailVerified ? "인증 완료" : resendSeconds > 0 ? `${resendSeconds}초 후 재전송` : verificationRequestId ? "다시 받기" : "이메일 인증"}</button></div>{emailVerificationError && <p id="email-verification-error" role="alert" className="text-sm font-semibold text-rose-600">{emailVerificationError}</p>}{verificationRequestId && !isEmailVerified && <><div className="flex gap-2"><input inputMode="numeric" maxLength={6} value={verificationCode} onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, ""))} className="snow-input flex-1" placeholder="인증번호 6자리" /><button type="button" className="snow-btn-primary whitespace-nowrap" onClick={confirmVerification} disabled={loading || verificationExpiresSeconds === 0}>인증 확인</button></div><p className="text-xs font-semibold text-[var(--snow-muted)]">{verificationExpiresSeconds > 0 ? `인증번호 유효 시간 ${Math.floor(verificationExpiresSeconds / 60)}:${String(verificationExpiresSeconds % 60).padStart(2, "0")}` : "인증번호가 만료되었습니다. 다시 받아 주세요."}</p></>}</div>
    <div className="grid gap-5 md:grid-cols-2"><label className="grid gap-2"><RequiredLabel>비밀번호</RequiredLabel><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="snow-input" autoComplete="new-password" minLength={8} required /></label><label className="grid gap-2"><RequiredLabel>비밀번호 확인</RequiredLabel><input type="password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} className="snow-input" autoComplete="new-password" minLength={8} required /></label><label className="grid gap-2"><RequiredLabel>닉네임</RequiredLabel><input value={nickname} onChange={(event) => setNickname(event.target.value)} className="snow-input" required /></label><label className="grid gap-2"><span className="snow-label">출발 지역 <small className="font-normal text-[var(--snow-muted)]">선택</small></span><input value={departureRegion} onChange={(event) => setDepartureRegion(event.target.value)} className="snow-input" /></label></div>
    <SelectionGrid title="선호 리조트" items={resorts.map((item) => ({ id: item.id, label: item.name }))} selectedIds={selectedResortIds} onToggle={(id) => toggle(id, selectedResortIds, setSelectedResortIds)} /><SelectionGrid title="라이딩 성향" items={ridingStyles.map((item) => ({ id: item.id, label: item.styleName }))} selectedIds={selectedStyleIds} onToggle={(id) => toggle(id, selectedStyleIds, setSelectedStyleIds)} /><button type="submit" className="snow-btn-primary w-full" disabled={loading}>{loading ? "처리 중" : "회원가입 완료"}</button>
  </form><p className="mt-6 text-center text-sm text-[var(--snow-muted)]">이미 계정이 있나요? <Link href="/login" className="font-bold text-sky-700 underline">로그인</Link></p></section></main><Footer /></div>;
}

function RequiredLabel({ children }: { children: React.ReactNode }) { return <span className="snow-label">{children}<b className="ml-1 text-rose-500">*</b></span>; }

function SelectionGrid({ title, items, selectedIds, onToggle }: { title: string; items: { id: number; label: string }[]; selectedIds: number[]; onToggle: (id: number) => void; }) {
  return <fieldset className="grid gap-3"><legend className="snow-label mb-1">{title} <small className="font-normal text-[var(--snow-muted)]">선택</small></legend><div className="grid gap-2 sm:grid-cols-2">{items.map((item) => { const checked = selectedIds.includes(item.id); return <label key={item.id} className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-sm font-semibold ${checked ? "border-sky-500 bg-sky-50 text-sky-800" : "border-[var(--snow-border)]"}`}><input type="checkbox" checked={checked} onChange={() => onToggle(item.id)} className="h-4 w-4 accent-sky-700" />{item.label}</label>; })}</div></fieldset>;
}
