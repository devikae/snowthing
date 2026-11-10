"use client";

import Link from "next/link";
import { registrationPolicy, registrationPolicyUrl, termsDocument, termsDocumentUrl } from "../lib/registrationPolicy";

export interface RegistrationConsent {
  ageConfirmed: boolean;
  termsAgreed: boolean;
  privacyAgreed: boolean;
  marketingEmailAgreed: boolean;
}

export const EMPTY_CONSENT: RegistrationConsent = { ageConfirmed: false, termsAgreed: false, privacyAgreed: false, marketingEmailAgreed: false };

export const hasRequiredConsent = (consent: RegistrationConsent) => consent.ageConfirmed && consent.termsAgreed && consent.privacyAgreed;

export const consentPayload = (consent: RegistrationConsent) => ({ ...consent, privacyVersion: registrationPolicy.version, termsVersion: termsDocument.version });

export function RegistrationConsentForm({ value, onChange }: { value: RegistrationConsent; onChange: (next: RegistrationConsent) => void }) {
  const allAgreed = hasRequiredConsent(value) && value.marketingEmailAgreed;
  const set = (key: keyof RegistrationConsent, checked: boolean) => onChange({ ...value, [key]: checked });
  return (
    <>
      <label className="flex cursor-pointer items-center gap-3 border-b border-[var(--snow-border)] pb-4 text-base font-bold text-[var(--snow-ink)]">
        <input type="checkbox" checked={allAgreed} onChange={(event) => { const checked = event.target.checked; onChange({ ageConfirmed: checked, termsAgreed: checked, privacyAgreed: checked, marketingEmailAgreed: checked }); }} className="h-4 w-4 shrink-0 accent-sky-700" />
        <span>전체 동의</span>
      </label>
      <p className="-mt-3 text-xs text-[var(--snow-muted)]">필수 및 선택 항목에 모두 동의합니다. 선택 항목은 동의하지 않아도 가입할 수 있습니다.</p>
      <div className="grid gap-2">
        <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
          <input type="checkbox" checked={value.ageConfirmed} onChange={(event) => set("ageConfirmed", event.target.checked)} className="h-4 w-4 shrink-0 accent-sky-700" />
          <span>[필수] 만 14세 이상입니다.</span>
        </label>
        <p className="pl-7 text-xs leading-5 text-[var(--snow-muted)]">SnowThing은 만 14세 이상만 회원으로 가입할 수 있습니다. 본인의 나이를 확인해 주세요.</p>
      </div>
      <div className="grid gap-2">
        <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
          <input type="checkbox" checked={value.termsAgreed} onChange={(event) => set("termsAgreed", event.target.checked)} className="h-4 w-4 shrink-0 accent-sky-700" />
          <span>[필수] 이용약관에 동의합니다.</span>
        </label>
        <p className="pl-7 text-xs text-[var(--snow-muted)]"><Link href={termsDocumentUrl} target="_blank" className="font-bold text-[var(--alpine-sky)] underline">이용약관 보기</Link> (문서 버전: {termsDocument.version})</p>
      </div>
      <div className="rounded-lg border border-[var(--snow-border)] bg-[var(--snow-surface-low)] p-4 text-sm leading-6 text-[var(--snow-ink-soft)]">
        <p><strong>수집 항목:</strong> {registrationPolicy.collection.items}</p>
        <p><strong>수집 목적:</strong> {registrationPolicy.collection.purpose}</p>
        <p><strong>보유 및 이용 기간:</strong> {registrationPolicy.collection.retention}</p>
        <p>{registrationPolicy.collection.refusal}</p>
        <p>개인정보 처리 및 문의 방법은 개인정보처리방침에서 확인할 수 있습니다.</p>
        <Link href={registrationPolicyUrl} target="_blank" className="font-bold text-[var(--alpine-sky)] underline">개인정보처리방침 보기</Link>
        <p className="text-xs text-[var(--snow-muted)]">문서 버전: {registrationPolicy.version}</p>
      </div>
      <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
        <input type="checkbox" checked={value.privacyAgreed} onChange={(event) => set("privacyAgreed", event.target.checked)} className="h-4 w-4 shrink-0 accent-sky-700" />
        <span>[필수] 개인정보 수집 및 이용에 동의합니다.</span>
      </label>
      <div className="grid gap-2">
        <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold text-[var(--snow-ink)]">
          <input type="checkbox" checked={value.marketingEmailAgreed} onChange={(event) => set("marketingEmailAgreed", event.target.checked)} className="h-4 w-4 shrink-0 accent-sky-700" />
          <span>[선택] 광고성 이메일 수신 및 이를 위한 개인정보 이용에 동의합니다.</span>
        </label>
        <p className="pl-7 text-xs leading-5 text-[var(--snow-muted)]">이용 항목: 이메일. 목적: 이벤트와 혜택 안내. 기간: 동의 철회 또는 탈퇴 시까지. 동의하지 않아도 가입할 수 있고, 내 정보 수정에서 언제든 철회할 수 있습니다.</p>
      </div>
    </>
  );
}
