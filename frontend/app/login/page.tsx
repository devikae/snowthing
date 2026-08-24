"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Footer, TopNav } from "../components/SiteChrome";
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
      setErrorMsg("올바른 이메일 형식으로 입력해주세요. 예: user@snowthing.com");
      return;
    }

    setLoading(true);
    try {
      const res = await csrfFetch("http://localhost:8080/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, rememberMe }),
      });

      if (!res.ok) {
        const errorData = await res.json();
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
    <div className="min-h-screen bg-[var(--snow-background)]">
      <TopNav active="login" />
      <main className="snow-container flex min-h-[calc(100vh-129px)] items-center justify-center px-5 py-12">
        <section className="snow-card w-full max-w-md bg-white p-7 md:p-8">
          <div className="mb-7 text-center">
            <span className="snow-chip snow-chip-dark mb-4">Snowthing Login</span>
            <h1 className="text-3xl font-extrabold italic text-black">로그인</h1>
            <p className="mt-3 text-sm leading-6 text-[var(--snow-muted)]">스노보더 커뮤니티에 다시 접속합니다.</p>
          </div>

          {errorMsg && (
            <div className="mb-5 rounded border border-[#fecaca] bg-[#fef2f2] p-4 text-sm font-semibold text-[#dc2626]">
              {errorMsg}
            </div>
          )}

          <form onSubmit={handleSubmit} className="grid gap-5">
            <label className="grid gap-2">
              <span className="snow-label">Email</span>
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="user@snowthing.com" className="snow-input" required />
            </label>

            <label className="grid gap-2">
              <span className="snow-label">Password</span>
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호" className="snow-input" required />
            </label>

            <label className="flex items-center gap-2 text-sm text-[var(--snow-muted)]">
              <input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} />
              로그인 상태 유지
            </label>

            <button type="submit" className="snow-btn-primary w-full" disabled={loading}>
              {loading ? "로그인 중" : "로그인"}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-[var(--snow-muted)]">
            아직 계정이 없나요?{" "}
            <Link href="/signup" className="font-bold text-black underline">
              회원가입
            </Link>
          </p>
        </section>
      </main>
      <Footer />
    </div>
  );
}
