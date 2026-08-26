"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS } from "../lib/api";

interface ResortMaster {
  id: number;
  name: string;
  regionName: string;
}

interface RidingStyleMaster {
  id: number;
  styleName: string;
  description: string;
}

const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;
const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).{8,}$/;

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}

export default function SignUpPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [nickname, setNickname] = useState("");
  const [bio, setBio] = useState("");
  const [departureRegion, setDepartureRegion] = useState("");
  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [ridingStyles, setRidingStyles] = useState<RidingStyleMaster[]>([]);
  const [selectedResortIds, setSelectedResortIds] = useState<number[]>([]);
  const [selectedStyleIds, setSelectedStyleIds] = useState<number[]>([]);
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const isLengthValid = password.length >= 8;
  const hasUppercase = /[A-Z]/.test(password);
  const hasSpecialChar = /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(password);
  const isPasswordComplexityValid = PASSWORD_REGEX.test(password);
  const isPasswordMatch = password.length > 0 && password === passwordConfirm;

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const [resortRes, styleRes] = await Promise.all([
            fetch(API_ENDPOINTS.master.resorts),
            fetch(API_ENDPOINTS.master.ridingStyles),
          ]);
          if (resortRes.ok) {
            const resortData: ResortMaster[] = await resortRes.json();
            setResorts(resortData);
          }
          if (styleRes.ok) {
            const styleData: RidingStyleMaster[] = await styleRes.json();
            setRidingStyles(styleData);
          }
        } catch (error) {
          console.error("마스터 데이터 로드 실패:", error);
        }
      })();
    }, 0);

    return () => window.clearTimeout(timer);
  }, []);

  const handleResortToggle = (id: number) => {
    setSelectedResortIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  };

  const handleStyleToggle = (id: number) => {
    setSelectedStyleIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMsg("");

    if (!EMAIL_REGEX.test(email)) {
      setErrorMsg("올바른 이메일 형식으로 입력해주세요. 예: user@snowthing.com");
      return;
    }

    if (password.length < 4) {
      setErrorMsg("비밀번호는 최소 4자 이상이어야 합니다.");
      return;
    }

    if (!isPasswordMatch) {
      setErrorMsg("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    setLoading(true);
    try {
      const res = await csrfFetch(API_ENDPOINTS.members.signup, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email,
          password,
          nickname,
          bio,
          departureRegion,
          resortIds: selectedResortIds,
          ridingStyleIds: selectedStyleIds,
        }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || errorData.error || "회원가입에 실패했습니다.");
      }

      // 회원가입 성공 즉시 자동 로그인 수행
      const loginRes = await csrfFetch(API_ENDPOINTS.auth.login, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, rememberMe: true }),
      });

      if (loginRes.ok) {
        router.push("/");
      } else {
        router.push("/login");
      }
    } catch (error) {
      setErrorMsg(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="signup" />
      <main className="snow-container px-5 py-10 lg:px-8">
        <section className="mx-auto max-w-3xl">
          <div className="mb-7 border-b-2 border-black pb-5 text-center">
            <span className="snow-chip snow-chip-dark mb-4">Snowthing Join</span>
            <h1 className="text-4xl font-extrabold italic text-black">회원가입</h1>
            <p className="mt-3 text-[var(--snow-muted)]">라이딩 성향과 선호 리조트를 함께 등록합니다.</p>
          </div>

          <form onSubmit={handleSubmit} className="snow-card grid gap-6 bg-white p-6 md:p-8">
            {errorMsg && <div className="rounded border border-[#fecaca] bg-[#fef2f2] p-4 text-sm font-semibold text-[#dc2626]">{errorMsg}</div>}

            <div className="grid gap-5 md:grid-cols-2">
              <label className="grid gap-2 md:col-span-2">
                <span className="snow-label">Email</span>
                <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="user@snowthing.com" className="snow-input" required />
              </label>

              <label className="grid gap-2">
                <span className="snow-label">Password</span>
                <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호" className="snow-input" required />
              </label>

              <label className="grid gap-2">
                <span className="snow-label">Confirm</span>
                <input type="password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} placeholder="비밀번호 확인" className="snow-input" required />
              </label>
            </div>

            {password.length > 0 && (
              <div className="flex flex-wrap gap-2">
                <span className={`snow-chip ${isLengthValid ? "snow-chip-green" : ""}`}>8자 이상</span>
                <span className={`snow-chip ${hasUppercase ? "snow-chip-green" : ""}`}>대문자 포함</span>
                <span className={`snow-chip ${hasSpecialChar ? "snow-chip-green" : ""}`}>특수문자 포함</span>
                <span className={`snow-chip ${isPasswordMatch ? "snow-chip-green" : ""}`}>비밀번호 일치</span>
              </div>
            )}

            <div className="grid gap-5 md:grid-cols-2">
              <label className="grid gap-2">
                <span className="snow-label">Nickname</span>
                <input value={nickname} onChange={(event) => setNickname(event.target.value)} placeholder="닉네임" className="snow-input" required />
              </label>
              <label className="grid gap-2">
                <span className="snow-label">Departure Region</span>
                <input value={departureRegion} onChange={(event) => setDepartureRegion(event.target.value)} placeholder="서울 송파구" className="snow-input" />
              </label>
            </div>

            <label className="grid gap-2">
              <span className="snow-label">Bio</span>
              <input value={bio} onChange={(event) => setBio(event.target.value)} placeholder="자기소개 한 줄" className="snow-input" />
            </label>

            <SelectionGrid title="Base Resorts" items={resorts.map((item) => ({ id: item.id, label: item.name }))} selectedIds={selectedResortIds} onToggle={handleResortToggle} />
            <SelectionGrid title="Riding Styles" items={ridingStyles.map((item) => ({ id: item.id, label: item.styleName }))} selectedIds={selectedStyleIds} onToggle={handleStyleToggle} />

            <button type="submit" className="snow-btn-primary w-full" disabled={loading || !isPasswordMatch}>
              {loading ? "가입 처리 중" : "회원가입 완료"}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-[var(--snow-muted)]">
            이미 계정이 있나요?{" "}
            <Link href="/login" className="font-bold text-black underline">
              로그인
            </Link>
          </p>
        </section>
      </main>
      <Footer />
    </div>
  );
}

function SelectionGrid({
  title,
  items,
  selectedIds,
  onToggle,
}: {
  title: string;
  items: { id: number; label: string }[];
  selectedIds: number[];
  onToggle: (id: number) => void;
}) {
  return (
    <fieldset className="grid gap-3">
      <legend className="snow-label">{title}</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {items.length === 0 ? (
          <p className="text-sm text-[var(--snow-muted)]">선택 항목을 불러오는 중입니다.</p>
        ) : (
          items.map((item) => {
            const checked = selectedIds.includes(item.id);
            return (
              <label
                key={item.id}
                className={`flex items-center gap-2 rounded border px-3 py-3 text-sm font-semibold ${
                  checked ? "border-black bg-[var(--snow-surface-low)] text-black" : "border-[var(--snow-border)] bg-white text-[var(--snow-ink-soft)]"
                }`}
              >
                <input type="checkbox" checked={checked} onChange={() => onToggle(item.id)} />
                {item.label}
              </label>
            );
          })
        )}
      </div>
    </fieldset>
  );
}
