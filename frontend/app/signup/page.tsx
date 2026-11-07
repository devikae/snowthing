"use client";

import { registrationPolicy, registrationPolicyUrl } from "../lib/registrationPolicy";

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
const NICKNAME_REGEX = /^[a-zA-Z0-9가-힣]{2,10}$/;

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
  const [isAtLeastFourteen, setIsAtLeastFourteen] = useState(false);
  const [showSignUpForm, setShowSignUpForm] = useState(false);
  const [privacyAgreed, setPrivacyAgreed] = useState(false);
  const [marketingAgreed, setMarketingAgreed] = useState(false);
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
  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [ridingStyles, setRidingStyles] = useState<RidingStyleMaster[]>([]);
  const [selectedResortIds, setSelectedResortIds] = useState<number[]>([]);
  const [selectedStyleIds, setSelectedStyleIds] = useState<number[]>([]);
  const [notice, setNotice] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [emailVerificationError, setEmailVerificationError] = useState("");
  const [loading, setLoading] = useState(false);

  const isEmailVerified = emailVerificationToken.length > 0;
  const isPasswordValid = password.length >= 8 && password.length <= 20 && password.trim().length > 0;
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
      if (!availability.available) throw new Error("이미 가입된 이메일이거나 탈퇴 후 30일이 지나지 않은 이메일입니다.");
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
    if (!isPasswordValid) return setErrorMsg("비밀번호는 8~20자로 입력해 주세요.");
    if (!isPasswordMatch) return setErrorMsg("비밀번호 확인이 일치하지 않습니다.");
    if (!NICKNAME_REGEX.test(nickname)) return setErrorMsg("닉네임은 2자 이상 10자 이하의 한글, 영문, 숫자로 입력해 주세요.");
    setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.signup, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, emailVerificationToken, password, nickname, resortIds: selectedResortIds, ridingStyleIds: selectedStyleIds, ageConfirmed: isAtLeastFourteen, privacyAgreed, privacyVersion: registrationPolicy.version, marketingEmailAgreed: marketingAgreed }) });
      if (!response.ok) throw new Error(await readError(response, "회원가입에 실패했습니다."));
      const loginResponse = await csrfFetch(API_ENDPOINTS.auth.login, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password, rememberMe: true }) });
      router.push(loginResponse.ok ? "/" : "/login");
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "회원가입을 처리하지 못했습니다.");
    } finally { setLoading(false); }
  };

  const toggle = (id: number, values: number[], setter: React.Dispatch<React.SetStateAction<number[]>>) => setter(values.includes(id) ? values.filter((value) => value !== id) : [...values, id]);

  if (!showSignUpForm) {
    return (
      <div className="min-h-screen bg-[var(--snow-background)]">
        <TopNav active="signup" />
        <main className="snow-container px-5 py-10 lg:px-8">
          <section className="mx-auto max-w-2xl">
            <h1 className="mb-6 text-center text-3xl font-extrabold text-[var(--snow-ink)]">회원가입</h1>
            <div className="snow-card grid gap-5 p-6 md:p-8">
              <h2 className="text-lg font-bold text-[var(--snow-ink)]">가입 전 확인해 주세요</h2>
              <label className="flex cursor-pointer items-center gap-3 border-b border-[var(--snow-border)] pb-4 text-base font-bold text-[var(--snow-ink)]">
                <input type="checkbox" checked={isAtLeastFourteen && privacyAgreed && marketingAgreed} onChange={(event) => { const checked = event.target.checked; setIsAtLeastFourteen(checked); setPrivacyAgreed(checked); setMarketingAgreed(checked); }} className="h-4 w-4 accent-sky-700" />
                <span>전체 동의</span>
              </label>
              <p className="-mt-3 text-xs text-[var(--snow-muted)]">필수 및 선택 항목에 모두 동의합니다. 선택 항목은 동의하지 않아도 가입할 수 있습니다.</p>
              <div className="grid gap-2">
                <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
                  <input type="checkbox" checked={isAtLeastFourteen} onChange={(event) => setIsAtLeastFourteen(event.target.checked)} className="h-4 w-4 accent-sky-700" />
                  <span>[필수] 만 14세 이상입니다.</span>
                </label>
                <p className="pl-7 text-xs leading-5 text-[var(--snow-muted)]">SnowThing은 만 14세 이상만 회원으로 가입할 수 있습니다. 본인의 나이를 확인해 주세요.</p>
              </div>
              <div className="rounded-lg border border-[var(--snow-border)] bg-[var(--snow-surface-low)] p-4 text-sm leading-6 text-[var(--snow-ink-soft)]">
                <p><strong>수집 항목:</strong> {registrationPolicy.collection.items}</p>
                <p><strong>수집 목적:</strong> {registrationPolicy.collection.purpose}</p>
                <p><strong>보유 및 이용 기간:</strong> {registrationPolicy.collection.retention}</p>
                <p>{registrationPolicy.collection.refusal}</p>
                <p>개인정보 처리 및 문의 방법은 개인정보처리방침에서 확인할 수 있습니다.</p>
                <Link href={registrationPolicyUrl} target="_blank" className="font-bold text-sky-600 underline">개인정보처리방침 보기</Link>
                <p className="text-xs text-[var(--snow-muted)]">문서 버전: {registrationPolicy.version}</p>
              </div>
              <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
                <input type="checkbox" checked={privacyAgreed} onChange={(event) => setPrivacyAgreed(event.target.checked)} className="h-4 w-4 accent-sky-700" />
                <span>[필수] 개인정보 수집·이용에 동의합니다.</span>
              </label>
              <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
                <input type="checkbox" checked={marketingAgreed} onChange={(event) => setMarketingAgreed(event.target.checked)} className="h-4 w-4 accent-sky-700" />
                <span>[선택] 이벤트·혜택 등 광고성 이메일 수신에 동의합니다.</span>
              </label>
              <p className="text-xs text-[var(--snow-muted)]">선택 항목에 동의하지 않아도 회원가입을 할 수 있습니다.</p>
              <p className="text-xs leading-5 text-[var(--snow-muted)]">광고성 이메일 수신에 관한 문의 및 동의 철회 요청: 82snowthing@gmail.com</p>
              <button type="button" className="snow-btn-primary w-full" disabled={!isAtLeastFourteen || !privacyAgreed} onClick={() => setShowSignUpForm(true)}>다음</button>
            </div>
          </section>
        </main>
        <Footer />
      </div>
    );
  }

  return <div className="min-h-screen bg-[var(--snow-background)]"><TopNav active="signup" /><main className="snow-container px-5 py-10 lg:px-8"><section className="mx-auto max-w-2xl"><header className="mb-6 text-center"><h1 className="text-3xl font-extrabold text-[var(--snow-ink)]">회원가입</h1></header><form onSubmit={handleSubmit} noValidate className="snow-card grid gap-7 bg-white p-6 md:p-8">
    {errorMsg && <div role="alert" className="rounded border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-700">{errorMsg}</div>}
    {notice && <div className="rounded border border-sky-200 bg-sky-50 p-4 text-sm font-semibold text-sky-800">{notice}</div>}
    <div ref={emailSectionRef} className="grid gap-3"><RequiredLabel>이메일</RequiredLabel><div className="flex gap-2"><input ref={emailInputRef} type="email" value={email} onChange={(event) => changeEmail(event.target.value)} className="snow-input flex-1" placeholder="user@snowthing.org" disabled={isEmailVerified} required aria-invalid={emailVerificationError ? true : undefined} aria-describedby={emailVerificationError ? "email-verification-error" : undefined} /><button type="button" className="snow-btn-secondary whitespace-nowrap" onClick={requestVerification} disabled={loading || isEmailVerified || resendSeconds > 0}>{isEmailVerified ? "인증 완료" : resendSeconds > 0 ? `${resendSeconds}초 후 재전송` : verificationRequestId ? "다시 받기" : "이메일 인증"}</button></div>{emailVerificationError && <p id="email-verification-error" role="alert" className="text-sm font-semibold text-rose-600">{emailVerificationError}</p>}{verificationRequestId && !isEmailVerified && <><div className="flex gap-2"><input inputMode="numeric" maxLength={6} value={verificationCode} onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, ""))} className="snow-input flex-1" placeholder="인증번호 6자리" /><button type="button" className="snow-btn-primary whitespace-nowrap" onClick={confirmVerification} disabled={loading || verificationExpiresSeconds === 0}>인증 확인</button></div><p className="text-xs font-semibold text-[var(--snow-muted)]">{verificationExpiresSeconds > 0 ? `인증번호 유효 시간 ${Math.floor(verificationExpiresSeconds / 60)}:${String(verificationExpiresSeconds % 60).padStart(2, "0")}` : "인증번호가 만료되었습니다. 다시 받아 주세요."}</p></>}</div>
    <div className="grid gap-5 md:grid-cols-2"><label className="grid gap-2"><RequiredLabel>비밀번호</RequiredLabel><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="snow-input" autoComplete="new-password" minLength={8} maxLength={20} placeholder="8~20자 입력" required /></label><label className="grid gap-2"><RequiredLabel>비밀번호 확인</RequiredLabel><input type="password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} className="snow-input" autoComplete="new-password" minLength={8} maxLength={20} required /></label><label className="grid gap-2 md:col-span-2"><RequiredLabel>닉네임</RequiredLabel><input value={nickname} onChange={(event) => setNickname(event.target.value)} className="snow-input" minLength={2} maxLength={10} pattern="[a-zA-Z0-9가-힣]{2,10}" aria-describedby="nickname-help" required /><small id="nickname-help" className="text-xs text-[var(--snow-muted)]">한글, 영문, 숫자 2~10자</small></label></div>
    <SelectionGrid title="선호 리조트" items={resorts.map((item) => ({ id: item.id, label: item.name }))} selectedIds={selectedResortIds} onToggle={(id) => toggle(id, selectedResortIds, setSelectedResortIds)} /><SelectionGrid title="라이딩 성향" items={ridingStyles.map((item) => ({ id: item.id, label: item.styleName }))} selectedIds={selectedStyleIds} onToggle={(id) => toggle(id, selectedStyleIds, setSelectedStyleIds)} /><button type="submit" className="snow-btn-primary w-full" disabled={loading}>{loading ? "처리 중" : "회원가입 완료"}</button>
  </form><p className="mt-6 text-center text-sm text-[var(--snow-muted)]">이미 계정이 있나요? <Link href="/login" className="font-bold text-sky-700 underline">로그인</Link></p></section></main><Footer /></div>;
}

function RequiredLabel({ children }: { children: React.ReactNode }) { return <span className="snow-label">{children}<b className="ml-1 text-rose-500">*</b></span>; }

function SelectionGrid({ title, items, selectedIds, onToggle }: { title: string; items: { id: number; label: string }[]; selectedIds: number[]; onToggle: (id: number) => void; }) {
  return <fieldset className="grid gap-3"><legend className="snow-label mb-1">{title} <small className="font-normal text-[var(--snow-muted)]">선택</small></legend><div className="grid gap-2 sm:grid-cols-2">{items.map((item) => { const checked = selectedIds.includes(item.id); return <label key={item.id} className={`flex cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-sm font-semibold ${checked ? "border-sky-500 bg-sky-50 text-sky-800" : "border-[var(--snow-border)]"}`}><input type="checkbox" checked={checked} onChange={() => onToggle(item.id)} className="h-4 w-4 accent-sky-700" />{item.label}</label>; })}</div></fieldset>;
}
