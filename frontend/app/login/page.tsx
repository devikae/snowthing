"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false); // 로그인 상태 유지 state
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg("");

    if (!EMAIL_REGEX.test(email)) {
      setErrorMsg("올바른 이메일 형식(예: user@snowthing.com)이어야 합니다.");
      return;
    }

    setLoading(true);

    try {
      const res = await fetch("http://localhost:8080/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include", // JSESSIONID 세션 쿠키 수신 및 전달 필수!
        body: JSON.stringify({ email, password, rememberMe }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.error || errorData.message || "이메일 또는 비밀번호가 올바르지 않습니다.");
      }

      alert("로그인에 성공했습니다!");
      router.push("/");
    } catch (err: any) {
      setErrorMsg(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", padding: "2rem" }}>
      <div className="card-supabase" style={{ width: "100%", maxWidth: "420px" }}>
        <div style={{ textAlign: "center", marginBottom: "2rem" }}>
          <span className="pill-green" style={{ marginBottom: "0.75rem", display: "inline-block" }}>
            Snowthing Login
          </span>
          <h1 style={{ fontSize: "1.75rem", fontWeight: "700" }}>로그인</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.9rem", marginTop: "0.5rem" }}>
            스노보더 커뮤니티에 다시 오신 것을 환영합니다.
          </p>
        </div>

        {errorMsg && (
          <div style={{ backgroundColor: "rgba(255, 77, 79, 0.1)", border: "1px solid var(--error)", padding: "0.75rem", borderRadius: "6px", color: "var(--error)", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
            🚨 {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">이메일 계정</label>
            <input
              type="email"
              className="input-supabase"
              placeholder="user@snowthing.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label">비밀번호</label>
            <input
              type="password"
              className="input-supabase"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          {/* Remember-Me 로그인 상태 유지 체크박스 */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: "1rem", marginBottom: "1.25rem" }}>
            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer", fontSize: "0.85rem", color: "var(--text-sub)" }}>
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                style={{ accentColor: "var(--primary)", width: "16px", height: "16px" }}
              />
              <span>🔒 로그인 상태 유지 (30일간 세션 자동 연장)</span>
            </label>
          </div>

          <button type="submit" className="btn-primary-green" style={{ width: "100%" }} disabled={loading}>
            {loading ? "로그인 처리 중..." : "로그인"}
          </button>
        </form>

        <div style={{ textAlign: "center", marginTop: "1.5rem", fontSize: "0.85rem", color: "var(--text-muted)" }}>
          아직 회원이 아니신가요?{" "}
          <Link href="/signup" style={{ color: "var(--primary)", textDecoration: "none", fontWeight: "600" }}>
            지금 회원가입하기
          </Link>
        </div>
      </div>
    </div>
  );
}
