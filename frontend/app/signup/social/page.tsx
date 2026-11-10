"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { safeReturnUrl } from "../../lib/safeReturnUrl";
import { consentPayload, EMPTY_CONSENT, hasRequiredConsent, RegistrationConsentForm } from "../RegistrationConsentForm";

const PROVIDER_NAMES: Record<string, string> = { GOOGLE: "구글", NAVER: "네이버", KAKAO: "카카오" };

export default function SocialSignUpPage() {
  const router = useRouter();
  const [provider, setProvider] = useState<string | null>(null);
  const [consent, setConsent] = useState(EMPTY_CONSENT);
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void fetch(API_ENDPOINTS.auth.oauth2SignUpPending, { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) { router.replace("/login?error=OAUTH_SIGNUP_EXPIRED"); return; }
        setProvider((await response.json()).provider);
      })
      .catch(() => setErrorMsg("가입 정보를 불러오지 못했습니다. 다시 로그인해 주세요."));
  }, [router]);

  const submit = async () => {
    setErrorMsg(""); setLoading(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.auth.oauth2SignUp, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(consentPayload(consent)) });
      if (response.status === 410) { router.replace("/login?error=OAUTH_SIGNUP_EXPIRED"); return; }
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(body?.message || "가입을 처리하지 못했습니다.");
      window.location.assign(safeReturnUrl(body?.returnUrl ?? null));
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "가입을 처리하지 못했습니다.");
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="signup" />
      <main className="snow-container px-5 py-10 lg:px-8">
        <section className="mx-auto max-w-2xl">
          <h1 className="mb-6 text-center text-3xl font-extrabold text-[var(--snow-ink)]">회원가입</h1>
          <div className="snow-card grid gap-5 p-6 md:p-8">
            <h2 className="text-lg font-bold text-[var(--snow-ink)]">{provider ? `${PROVIDER_NAMES[provider] ?? "소셜"} 계정으로 가입합니다` : "가입 전 확인해 주세요"}</h2>
            {errorMsg && <div role="alert" className="rounded border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-700">{errorMsg}</div>}
            <RegistrationConsentForm value={consent} onChange={setConsent} />
            <button type="button" className="snow-btn-primary w-full" disabled={!provider || !hasRequiredConsent(consent) || loading} onClick={() => void submit()}>{loading ? "처리 중" : "동의하고 가입"}</button>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
