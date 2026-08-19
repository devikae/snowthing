"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

interface MemberProfile {
  publicId: string;
  email: string;
  nickname: string;
  role: string;
  resortNames: string[];
  ridingStyleNames: string[];
}

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

export default function HomePage() {
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [loading, setLoading] = useState(true);

  // 프로필 수정 모달/폼 state
  const [isEditing, setIsEditing] = useState(false);
  const [editNickname, setEditNickname] = useState("");
  const [editBio, setEditBio] = useState("");
  const [editDepartureRegion, setEditDepartureRegion] = useState("");
  const [selectedResortIds, setSelectedResortIds] = useState<number[]>([]);
  const [selectedStyleIds, setSelectedStyleIds] = useState<number[]>([]);

  // 마스터 데이터 state
  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [ridingStyles, setRidingStyles] = useState<RidingStyleMaster[]>([]);
  const [updateError, setUpdateError] = useState("");
  const [updateLoading, setUpdateLoading] = useState(false);

  const fetchMyProfile = async () => {
    try {
      const res = await fetch("http://localhost:8080/api/members/me", {
        method: "GET",
        credentials: "include",
      });

      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        setEditNickname(data.nickname);
      } else {
        setProfile(null);
      }
    } catch (err) {
      setProfile(null);
    } finally {
      setLoading(false);
    }
  };

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

  useEffect(() => {
    fetchMyProfile();
    fetchMasterData();
  }, []);

  const handleLogout = async () => {
    try {
      await fetch("http://localhost:8080/api/auth/logout", {
        method: "POST",
        credentials: "include",
      });
      alert("로그아웃 되었습니다.");
      setProfile(null);
      setIsEditing(false);
    } catch (err) {
      alert("로그아웃 처리 실패");
    }
  };

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

  // 프로필 수정 제출 (PUT /api/members/me)
  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setUpdateError("");
    setUpdateLoading(true);

    try {
      const res = await fetch("http://localhost:8080/api/members/me", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          nickname: editNickname,
          bio: editBio,
          departureRegion: editDepartureRegion,
          resortIds: selectedResortIds,
          ridingStyleIds: selectedStyleIds,
        }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.error || errorData.message || "프로필 수정 실패");
      }

      const updatedData = await res.json();
      setProfile(updatedData);
      alert("프로필 정보가 성공적으로 수정되었습니다!");
      setIsEditing(false);
    } catch (err: any) {
      setUpdateError(err.message);
    } finally {
      setUpdateLoading(false);
    }
  };

  return (
    <div className="container" style={{ paddingTop: "2rem", paddingBottom: "4rem" }}>
      {/* 1. 상단 네비게이션 헤더 */}
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "3rem", paddingBottom: "1.25rem", borderBottom: "1px solid var(--border-dark)" }}>
        <Link href="/" style={{ display: "flex", alignItems: "center", gap: "0.75rem", textDecoration: "none", color: "inherit" }}>
          <div style={{ width: "36px", height: "36px", borderRadius: "8px", backgroundColor: "var(--primary)", display: "flex", alignItems: "center", justifyContent: "center", color: "#171717", fontWeight: "900", fontSize: "1.2rem" }}>
            S
          </div>
          <span style={{ fontSize: "1.35rem", fontWeight: "800", letterSpacing: "-0.5px" }}>Snowthing</span>
          <span className="pill-green">v1.0 MVP</span>
        </Link>

        <div>
          {profile ? (
            <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
              <span style={{ fontSize: "0.9rem", color: "var(--text-sub)" }}>
                <strong style={{ color: "#ffffff" }}>{profile.nickname}</strong> 님
              </span>
              <button onClick={handleLogout} className="btn-secondary" style={{ fontSize: "0.85rem", padding: "0.5rem 1rem" }}>
                로그아웃
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", gap: "0.75rem" }}>
              <Link href="/login" className="btn-secondary" style={{ textDecoration: "none", fontSize: "0.9rem", padding: "0.6rem 1.2rem" }}>
                로그인
              </Link>
              <Link href="/signup" className="btn-primary-green" style={{ textDecoration: "none", fontSize: "0.9rem", padding: "0.6rem 1.2rem" }}>
                회원가입
              </Link>
            </div>
          )}
        </div>
      </header>

      {/* 2. 메인 히어로 섹션 */}
      <div style={{ textAlign: "center", maxWidth: "800px", margin: "0 auto 3.5rem auto" }}>
        <span className="pill-green" style={{ marginBottom: "1rem", display: "inline-block", fontSize: "0.85rem", padding: "0.35rem 1rem" }}>
          🏂 스노보더 전용 커뮤니티 플랫폼
        </span>
        <h1 style={{ fontSize: "3.25rem", fontWeight: "800", lineHeight: "1.15", letterSpacing: "-1.5px", marginBottom: "1.5rem" }}>
          새로운 보딩의 시작 <br />
          <span style={{ color: "var(--primary)" }}>Snowthing에 오신 것을 환영합니다</span>
        </h1>
        <p style={{ color: "var(--text-muted)", fontSize: "1.15rem", lineHeight: "1.6", marginBottom: "2.5rem" }}>
          스프링 세션 고정 방어 보안 기반의 백엔드와 Next.js 15가 연결된 플랫폼입니다. <br />
          지금 바로 회원가입 하시고 스노보더 커뮤니티를 체험해 보세요!
        </p>

        {!profile && !loading && (
          <div style={{ display: "flex", justifyContent: "center", gap: "1.25rem", flexWrap: "wrap" }}>
            <Link href="/signup" className="btn-primary-green" style={{ textDecoration: "none", fontSize: "1.1rem", padding: "0.9rem 2.25rem", borderRadius: "8px" }}>
              ✨ 지금 무료 회원가입하기
            </Link>
            <Link href="/login" className="btn-secondary" style={{ textDecoration: "none", fontSize: "1.1rem", padding: "0.9rem 2.25rem", borderRadius: "8px" }}>
              🔑 기존 계정 로그인
            </Link>
          </div>
        )}
      </div>

      {/* 3. 로그인 유저 프로필 카드 및 프로필 수정 모달 */}
      <div style={{ maxWidth: "680px", margin: "0 auto" }}>
        {loading ? (
          <div className="card-supabase" style={{ textAlign: "center", padding: "3rem" }}>
            <p style={{ color: "var(--text-muted)" }}>세션 인증 상태 확인 중...</p>
          </div>
        ) : profile ? (
          <div className="card-supabase" style={{ borderLeft: "4px solid var(--primary)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "1.5rem" }}>
              <div>
                <span className="pill-green" style={{ marginBottom: "0.5rem", display: "inline-block" }}>
                  보더 명함 프로필 (Session Active)
                </span>
                <h2 style={{ fontSize: "1.6rem", fontWeight: "700" }}>{profile.nickname} 님</h2>
              </div>
              <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                <button
                  onClick={() => setIsEditing(!isEditing)}
                  className="btn-primary-green"
                  style={{ fontSize: "0.8rem", padding: "0.35rem 0.8rem" }}
                >
                  {isEditing ? "닫기" : "✏️ 프로필 수정"}
                </button>
                <span style={{ fontSize: "0.8rem", backgroundColor: "#262626", color: "#ffffff", padding: "0.3rem 0.7rem", borderRadius: "4px", fontWeight: "600" }}>
                  {profile.role}
                </span>
              </div>
            </div>

            {/* 프로필 수정 폼 (isEditing === true) */}
            {isEditing ? (
              <form onSubmit={handleUpdateProfile} style={{ backgroundColor: "#141414", padding: "1.5rem", borderRadius: "8px", border: "1px solid var(--primary)", marginBottom: "1.5rem" }}>
                <h3 style={{ fontSize: "1.1rem", fontWeight: "700", marginBottom: "1rem", color: "var(--primary)" }}>
                  ✏️ 내 프로필 정보 수정
                </h3>

                {updateError && (
                  <div style={{ backgroundColor: "rgba(255, 77, 79, 0.1)", border: "1px solid var(--error)", padding: "0.6rem", borderRadius: "6px", color: "var(--error)", fontSize: "0.85rem", marginBottom: "1rem" }}>
                    🚨 {updateError}
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">활동 닉네임 *</label>
                  <input
                    type="text"
                    className="input-supabase"
                    value={editNickname}
                    onChange={(e) => setEditNickname(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">자기소개</label>
                  <input
                    type="text"
                    className="input-supabase"
                    value={editBio}
                    onChange={(e) => setEditBio(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">주 출발/거주 지역</label>
                  <input
                    type="text"
                    className="input-supabase"
                    value={editDepartureRegion}
                    onChange={(e) => setEditDepartureRegion(e.target.value)}
                  />
                </div>

                {/* 선호 스키장 선택 */}
                <div className="form-group">
                  <label className="form-label">선호 스키장 수정 (다중 선택)</label>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "0.5rem" }}>
                    {resorts.map((r) => (
                      <label
                        key={r.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "0.4rem",
                          backgroundColor: selectedResortIds.includes(r.id) ? "rgba(62, 207, 142, 0.1)" : "#1c1c1c",
                          border: `1px solid ${selectedResortIds.includes(r.id) ? "var(--primary)" : "#333333"}`,
                          padding: "0.5rem",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "0.8rem",
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={selectedResortIds.includes(r.id)}
                          onChange={() => handleResortToggle(r.id)}
                        />
                        <span>{r.name}</span>
                      </label>
                    ))}
                  </div>
                </div>

                {/* 라이딩 성향 선택 */}
                <div className="form-group">
                  <label className="form-label">라이딩 성향 수정 (다중 선택)</label>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: "0.5rem" }}>
                    {ridingStyles.map((s) => (
                      <label
                        key={s.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "0.4rem",
                          backgroundColor: selectedStyleIds.includes(s.id) ? "rgba(62, 207, 142, 0.1)" : "#1c1c1c",
                          border: `1px solid ${selectedStyleIds.includes(s.id) ? "var(--primary)" : "#333333"}`,
                          padding: "0.5rem",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "0.8rem",
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={selectedStyleIds.includes(s.id)}
                          onChange={() => handleStyleToggle(s.id)}
                        />
                        <span>{s.styleName}</span>
                      </label>
                    ))}
                  </div>
                </div>

                <div style={{ display: "flex", gap: "0.5rem", marginTop: "1.25rem" }}>
                  <button type="submit" className="btn-primary-green" style={{ flex: 1 }} disabled={updateLoading}>
                    {updateLoading ? "저장 중..." : "수정 완료 (PUT)"}
                  </button>
                  <button type="button" onClick={() => setIsEditing(false)} className="btn-secondary">
                    취소
                  </button>
                </div>
              </form>
            ) : (
              /* 프로필 조회 카드 */
              <div style={{ display: "flex", flexDirection: "column", gap: "0.85rem", backgroundColor: "#141414", padding: "1.25rem", borderRadius: "8px", border: "1px solid #262626", fontSize: "0.95rem" }}>
                <div>
                  <span style={{ color: "var(--text-muted)", width: "120px", display: "inline-block" }}>이메일 계정:</span>
                  <strong style={{ color: "#ffffff" }}>{profile.email}</strong>
                </div>
                <div>
                  <span style={{ color: "var(--text-muted)", width: "120px", display: "inline-block" }}>외부 UUID ID:</span>
                  <code style={{ color: "var(--primary)", fontFamily: "monospace", fontSize: "0.9rem" }}>{profile.publicId}</code>
                </div>

                <div>
                  <span style={{ color: "var(--text-muted)", width: "120px", display: "inline-block", verticalAlign: "top", marginTop: "0.25rem" }}>선호 스키장:</span>
                  <div style={{ display: "inline-flex", gap: "0.4rem", flexWrap: "wrap" }}>
                    {profile.resortNames && profile.resortNames.length > 0 ? (
                      profile.resortNames.map((r, i) => (
                        <span key={i} style={{ backgroundColor: "rgba(62, 207, 142, 0.15)", color: "var(--primary)", border: "1px solid rgba(62, 207, 142, 0.3)", padding: "0.2rem 0.6rem", borderRadius: "4px", fontSize: "0.8rem", fontWeight: "600" }}>
                          🏔️ {r}
                        </span>
                      ))
                    ) : (
                      <span style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>미선택</span>
                    )}
                  </div>
                </div>

                <div>
                  <span style={{ color: "var(--text-muted)", width: "120px", display: "inline-block", verticalAlign: "top", marginTop: "0.25rem" }}>라이딩 성향:</span>
                  <div style={{ display: "inline-flex", gap: "0.4rem", flexWrap: "wrap" }}>
                    {profile.ridingStyleNames && profile.ridingStyleNames.length > 0 ? (
                      profile.ridingStyleNames.map((s, i) => (
                        <span key={i} style={{ backgroundColor: "#262626", color: "#ffffff", border: "1px solid #333333", padding: "0.2rem 0.6rem", borderRadius: "4px", fontSize: "0.8rem", fontWeight: "600" }}>
                          🏂 {s}
                        </span>
                      ))
                    ) : (
                      <span style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>미선택</span>
                    )}
                  </div>
                </div>
              </div>
            )}

            <div style={{ marginTop: "1.5rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <p style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
                💡 PUT /api/members/me 프로필 수정 연동 완료
              </p>
              <button onClick={handleLogout} className="btn-secondary" style={{ fontSize: "0.85rem" }}>
                로그아웃
              </button>
            </div>
          </div>
        ) : (
          <div className="card-supabase" style={{ textAlign: "center", padding: "2.5rem 2rem" }}>
            <h3 style={{ fontSize: "1.25rem", fontWeight: "700", marginBottom: "0.75rem" }}>회원가입이 필요합니다</h3>
            <p style={{ color: "var(--text-muted)", fontSize: "0.95rem", marginBottom: "1.75rem" }}>
              아래 버튼을 누르시면 즉시 회원가입 페이지로 이동합니다.
            </p>
            <Link href="/signup" className="btn-primary-green" style={{ textDecoration: "none", display: "inline-block", width: "100%", maxWidth: "320px" }}>
              회원가입 페이지로 이동
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
