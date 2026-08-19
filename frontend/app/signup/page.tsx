"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

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

export default function SignUpPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState(""); // 비밀번호 확인 입력 필드
  const [nickname, setNickname] = useState("");
  const [bio, setBio] = useState("");
  const [departureRegion, setDepartureRegion] = useState("");

  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [ridingStyles, setRidingStyles] = useState<RidingStyleMaster[]>([]);

  const [selectedResortIds, setSelectedResortIds] = useState<number[]>([]);
  const [selectedStyleIds, setSelectedStyleIds] = useState<number[]>([]);

  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  // 이메일 및 비밀번호 정규식 패턴
  const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/;
  const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/;

  // 비밀번호 실시간 조건 판단
  const isLengthValid = password.length >= 8;
  const hasUppercase = /[A-Z]/.test(password);
  const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password);
  const isPasswordComplexityValid = PASSWORD_REGEX.test(password);
  const isPasswordMatch = password.length > 0 && password === passwordConfirm;

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [resortRes, styleRes] = await Promise.all([
          fetch("http://localhost:8080/api/resorts"),
          fetch("http://localhost:8080/api/riding-styles"),
        ]);
        if (resortRes.ok && styleRes.ok) {
          const resortData = await resortRes.json();
          const styleData = await styleRes.json();
          setResorts(resortData);
          setRidingStyles(styleData);
        }
      } catch (err) {
        console.error("마스터 데이터 로딩 실패", err);
      }
    };
    fetchMasterData();
  }, []);

  const handleResortToggle = (id: number) => {
    setSelectedResortIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleStyleToggle = (id: number) => {
    setSelectedStyleIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg("");

    if (!EMAIL_REGEX.test(email)) {
      setErrorMsg("올바른 이메일 형식(예: user@snowthing.com)이어야 합니다.");
      return;
    }

    if (!isPasswordComplexityValid) {
      setErrorMsg("비밀번호는 최소 8자 이상, 영문 대문자 1개, 특수문자 1개를 포함해야 합니다.");
      return;
    }

    if (!isPasswordMatch) {
      setErrorMsg("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    setLoading(true);

    try {
      const res = await fetch("http://localhost:8080/api/members", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
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
        throw new Error(errorData.error || errorData.message || "회원가입 실패");
      }

      alert("회원가입이 완료되었습니다! 로그인해 주세요.");
      router.push("/login");
    } catch (err: any) {
      setErrorMsg(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", padding: "2rem" }}>
      <div className="card-supabase" style={{ width: "100%", maxWidth: "560px" }}>
        <div style={{ textAlign: "center", marginBottom: "2rem" }}>
          <span className="pill-green" style={{ marginBottom: "0.75rem", display: "inline-block" }}>
            Snowthing Join
          </span>
          <h1 style={{ fontSize: "1.75rem", fontWeight: "700" }}>회원가입</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.9rem", marginTop: "0.5rem" }}>
            스노보더 커뮤니티 Snowthing의 회원이 되어보세요.
          </p>
        </div>

        {errorMsg && (
          <div style={{ backgroundColor: "rgba(255, 77, 79, 0.1)", border: "1px solid var(--error)", padding: "0.75rem", borderRadius: "6px", color: "var(--error)", fontSize: "0.85rem", marginBottom: "1.5rem" }}>
            🚨 {errorMsg}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* 이메일 계정 */}
          <div className="form-group">
            <label className="form-label">이메일 계정 *</label>
            <input
              type="email"
              className="input-supabase"
              placeholder="user@snowthing.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          {/* 비밀번호 1차 입력 */}
          <div className="form-group">
            <label className="form-label">비밀번호 *</label>
            <input
              type="password"
              className="input-supabase"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />

            {/* 실시간 비밀번호 복잡도 조건 배지 표시 */}
            {password.length > 0 && (
              <div style={{ display: "flex", gap: "0.5rem", fontSize: "0.78rem", marginTop: "0.4rem", flexWrap: "wrap" }}>
                <span style={{ color: isLengthValid ? "#3ecf8e" : "#ff4d4f", backgroundColor: isLengthValid ? "rgba(62, 207, 142, 0.1)" : "rgba(255, 77, 79, 0.1)", padding: "0.15rem 0.5rem", borderRadius: "4px" }}>
                  {isLengthValid ? "✓ 8자 이상" : "✗ 8자 이상 필요"}
                </span>
                <span style={{ color: hasUppercase ? "#3ecf8e" : "#ff4d4f", backgroundColor: hasUppercase ? "rgba(62, 207, 142, 0.1)" : "rgba(255, 77, 79, 0.1)", padding: "0.15rem 0.5rem", borderRadius: "4px" }}>
                  {hasUppercase ? "✓ 대문자 1개 이상" : "✗ 대문자 필요"}
                </span>
                <span style={{ color: hasSpecialChar ? "#3ecf8e" : "#ff4d4f", backgroundColor: hasSpecialChar ? "rgba(62, 207, 142, 0.1)" : "rgba(255, 77, 79, 0.1)", padding: "0.15rem 0.5rem", borderRadius: "4px" }}>
                  {hasSpecialChar ? "✓ 특수문자 1개 이상" : "✗ 특수문자 필요"}
                </span>
              </div>
            )}
          </div>

          {/* 비밀번호 2차 확인 입력 */}
          <div className="form-group">
            <label className="form-label">비밀번호 확인 *</label>
            <input
              type="password"
              className="input-supabase"
              placeholder="비밀번호 다시 입력"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              style={{
                borderColor: passwordConfirm.length > 0 ? (isPasswordMatch ? "var(--primary)" : "var(--error)") : undefined,
              }}
              required
            />

            {/* 실시간 2중 입력 일치 여부 피드백 메시지 */}
            {passwordConfirm.length > 0 && (
              <div style={{ fontSize: "0.82rem", marginTop: "0.35rem", fontWeight: "600" }}>
                {isPasswordMatch ? (
                  <span style={{ color: "var(--primary)" }}>🟢 비밀번호가 안전하게 일치합니다.</span>
                ) : (
                  <span style={{ color: "var(--error)" }}>🔴 비밀번호가 일치하지 않습니다.</span>
                )}
              </div>
            )}
          </div>

          {/* 활동 닉네임 */}
          <div className="form-group" style={{ marginTop: "1.25rem" }}>
            <label className="form-label">활동 닉네임 * (2자~10자)</label>
            <input
              type="text"
              className="input-supabase"
              placeholder="휘팍카빙왕"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
            />
          </div>

          {/* 선호 스키장 다중 선택 체크박스 */}
          <div className="form-group" style={{ marginTop: "1.5rem" }}>
            <label className="form-label">주로 방문하는 선호 스키장 (다중 선택)</label>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "0.5rem", marginTop: "0.25rem" }}>
              {resorts.map((r) => (
                <label
                  key={r.id}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    backgroundColor: selectedResortIds.includes(r.id) ? "rgba(62, 207, 142, 0.1)" : "#141414",
                    border: `1px solid ${selectedResortIds.includes(r.id) ? "var(--primary)" : "var(--border-dark)"}`,
                    padding: "0.6rem 0.8rem",
                    borderRadius: "6px",
                    cursor: "pointer",
                    fontSize: "0.85rem",
                    color: selectedResortIds.includes(r.id) ? "var(--primary)" : "var(--text-main)",
                    transition: "all 0.2s",
                  }}
                >
                  <input
                    type="checkbox"
                    checked={selectedResortIds.includes(r.id)}
                    onChange={() => handleResortToggle(r.id)}
                    style={{ accentColor: "var(--primary)" }}
                  />
                  <span>{r.name}</span>
                </label>
              ))}
            </div>
          </div>

          {/* 라이딩 성향 다중 선택 체크박스 */}
          <div className="form-group" style={{ marginTop: "1.25rem" }}>
            <label className="form-label">라이딩 성향 (다중 선택)</label>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "0.5rem", marginTop: "0.25rem" }}>
              {ridingStyles.map((s) => (
                <label
                  key={s.id}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    backgroundColor: selectedStyleIds.includes(s.id) ? "rgba(62, 207, 142, 0.1)" : "#141414",
                    border: `1px solid ${selectedStyleIds.includes(s.id) ? "var(--primary)" : "var(--border-dark)"}`,
                    padding: "0.6rem 0.8rem",
                    borderRadius: "6px",
                    cursor: "pointer",
                    fontSize: "0.85rem",
                    color: selectedStyleIds.includes(s.id) ? "var(--primary)" : "var(--text-main)",
                    transition: "all 0.2s",
                  }}
                >
                  <input
                    type="checkbox"
                    checked={selectedStyleIds.includes(s.id)}
                    onChange={() => handleStyleToggle(s.id)}
                    style={{ accentColor: "var(--primary)" }}
                  />
                  <span>{s.styleName}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="form-group" style={{ marginTop: "1.25rem" }}>
            <label className="form-label">자기소개 한마디</label>
            <input
              type="text"
              className="input-supabase"
              placeholder="입문 2년차 라이딩 보더입니다"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">주 출발/거주 지역</label>
            <input
              type="text"
              className="input-supabase"
              placeholder="서울 송파구"
              value={departureRegion}
              onChange={(e) => setDepartureRegion(e.target.value)}
            />
          </div>

          <button
            type="submit"
            className="btn-primary-green"
            style={{ width: "100%", marginTop: "1.5rem" }}
            disabled={loading || !isPasswordComplexityValid || !isPasswordMatch}
          >
            {loading ? "가입 처리 중..." : "회원가입 완료"}
          </button>
        </form>

        <div style={{ textAlign: "center", marginTop: "1.5rem", fontSize: "0.85rem", color: "var(--text-muted)" }}>
          이미 계정이 있으신가요?{" "}
          <Link href="/login" style={{ color: "var(--primary)", textDecoration: "none", fontWeight: "600" }}>
            로그인하기
          </Link>
        </div>
      </div>
    </div>
  );
}
