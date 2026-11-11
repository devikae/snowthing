"use client";

import Image from "next/image";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "../components/SiteChrome";
import { BlockedMembersSection } from "../components/BlockedMembersSection";
import { CarpoolReviewSection } from "../components/CarpoolReviewSection";
import { CommentProfileAvatar } from "../components/CommentProfileAvatar";
import { MarketingConsentSection } from "../components/MarketingConsentSection";
import { ScrappedPostsSection } from "../components/ScrappedPostsSection";
import { SocialAccountsSection } from "../components/SocialAccountsSection";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { errorText } from "../lib/errorText";
import "./profile.css";

interface Preference { id: number; name: string; }
interface Settings {
  publicId: string; email: string; nickname: string; profileImageUrl: string | null;
  bio: string | null; departureRegion: string | null; passwordChangeAvailable: boolean;
  resorts: Preference[]; ridingStyles: Preference[];
}
interface Form { nickname: string; bio: string; departureRegion: string; resortIds: number[]; ridingStyleIds: number[]; }
const toForm = (p: Settings): Form => ({ nickname: p.nickname, bio: p.bio ?? "", departureRegion: p.departureRegion ?? "", resortIds: p.resorts.map(x => x.id), ridingStyleIds: p.ridingStyles.map(x => x.id) });
async function message(response: Response, fallback: string) {
  const data = await response.json().catch(() => null);
  return data?.message ?? fallback;
}

export default function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<Settings | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [resorts, setResorts] = useState<Preference[]>([]);
  const [styles, setStyles] = useState<Preference[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState<"save" | "photo" | "password" | "withdraw" | null>(null);
  const [checkedNickname, setCheckedNickname] = useState<string | null>(null);
  const [checkingNickname, setCheckingNickname] = useState(false);
  const [nicknameMessage, setNicknameMessage] = useState("");
  const nicknameVersion = useRef(0);
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [passwordEditorOpen, setPasswordEditorOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [withdrawInput, setWithdrawInput] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const allowNavigation = useRef(false);
  const profileDirty = Boolean(profile && form && JSON.stringify(toForm(profile)) !== JSON.stringify(form));
  const dirty = profileDirty || photo !== null;

  const load = useCallback(async () => {
    try {
      const responses = await Promise.all([
        fetch(API_ENDPOINTS.members.settings, { credentials: "include" }),
        fetch(API_ENDPOINTS.master.resorts, { credentials: "include" }),
        fetch(API_ENDPOINTS.master.ridingStyles, { credentials: "include" }),
      ]);
      if (responses[0].status === 401) { router.replace("/login?returnUrl=/profile"); return; }
      for (const response of responses) if (!response.ok) throw new Error(await message(response, "내 정보와 선택 항목을 불러오지 못했습니다. 다시 시도해 주세요."));
      const p: Settings = await responses[0].json();
      const resortData: Preference[] = await responses[1].json();
      const styleData: { id: number; styleName: string }[] = await responses[2].json();
      setProfile(p); setForm(toForm(p));
      setResorts([...resortData, ...p.resorts.filter(x => !resortData.some(y => y.id === x.id))]);
      setStyles([...styleData.map(x => ({ id: x.id, name: x.styleName })), ...p.ridingStyles.filter(x => !styleData.some(y => y.id === x.id))]);
    } catch (e) { setError(errorText(e, "내 정보를 불러오지 못했습니다.")); }
    finally { setLoading(false); }
  }, [router]);
  useEffect(() => {
    if (!form || (!error && !notice)) return;
    const timer = window.setTimeout(() => { setError(""); setNotice(""); }, 5000);
    return () => window.clearTimeout(timer);
  }, [form, error, notice]);
  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  useEffect(() => {
    if (preview) return () => URL.revokeObjectURL(preview);
  }, [preview]);
  useEffect(() => {
    if (!dirty && !currentPassword && !newPassword && !confirmPassword) return;
    const beforeUnload = (event: BeforeUnloadEvent) => { if (!allowNavigation.current) event.preventDefault(); };
    const beforeLink = (event: MouseEvent) => {
      const link = (event.target as HTMLElement).closest("a[href]");
      if (link && !allowNavigation.current && !window.confirm("저장하지 않은 변경 사항이 있습니다. 페이지를 이동할까요?")) event.preventDefault();
    };
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", beforeLink, true);
    return () => { window.removeEventListener("beforeunload", beforeUnload); document.removeEventListener("click", beforeLink, true); };
  }, [dirty, currentPassword, newPassword, confirmPassword]);

  const checkNickname = async () => {
    if (!form || checkingNickname) return;
    const nickname = form.nickname;
    if (!/^[a-zA-Z0-9가-힣]{2,10}$/.test(nickname)) { setNicknameMessage("한글, 영문, 숫자 2~10자로 입력해 주세요."); return; }
    const version = ++nicknameVersion.current;
    setCheckingNickname(true); setNicknameMessage("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.nicknameAvailability, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ nickname }) });
      if (!response.ok) throw new Error(await message(response, "중복 확인을 하지 못했습니다. 잠시 후 다시 시도해 주세요."));
      const result: { nickname: string; available: boolean } = await response.json();
      if (version !== nicknameVersion.current) return;
      setCheckedNickname(result.available && result.nickname === nickname ? nickname : null);
      setNicknameMessage(result.available ? "사용할 수 있는 닉네임입니다." : "이미 사용 중인 닉네임입니다.");
    } catch (e) { if (version === nicknameVersion.current) setNicknameMessage(errorText(e, "중복 확인을 하지 못했습니다. 잠시 후 다시 시도해 주세요.")); }
    finally { setCheckingNickname(false); }
  };
  const save = async (event: React.FormEvent) => {
    event.preventDefault(); if (!form || !profile || busy) return;
    if (form.nickname !== profile.nickname && checkedNickname !== form.nickname) { setError("변경할 닉네임의 중복 확인을 완료해 주세요."); return; }
    setBusy("save"); setError(""); setNotice("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.settings, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(form) });
      if (!response.ok) throw new Error(await message(response, "내 정보를 저장하지 못했습니다."));
      const p: Settings = await response.json(); setProfile(p); setForm(toForm(p));
      setCheckedNickname(null); setNicknameMessage(""); setNotice("내 정보를 저장했습니다.");
      window.dispatchEvent(new Event("snowthing-profile-updated"));
    } catch (e) { setError(errorText(e, "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.")); }
    finally { setBusy(null); }
  };
  const savePhoto = async (reset = false) => {
    if (!profile || busy || (!reset && !photo)) return;
    setBusy("photo"); setError(""); setNotice("");
    try {
      const data = new FormData(); if (photo) data.append("file", photo);
      const response = await csrfFetch(reset ? API_ENDPOINTS.members.resetProfileImage : API_ENDPOINTS.images.profileUpload, { method: reset ? "DELETE" : "POST", ...(!reset ? { body: data } : {}) });
      if (response.status === 429) {
        window.alert("사진 변경 횟수를 넘었습니다. 잠시 후 다시 시도해 주세요.");
        setPhoto(null); setPreview(null);
        if (fileRef.current) fileRef.current.value = "";
        return;
      }
      if (!response.ok) throw new Error(await message(response, "프로필 사진을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요."));
      const url = reset ? null : (await response.json()).imageUrl;
      setProfile({ ...profile, profileImageUrl: url }); setPhoto(null); setPreview(null);
      if (fileRef.current) fileRef.current.value = "";
      setNotice(reset ? "기본 이미지로 변경했습니다." : "프로필 사진을 변경했습니다.");
      window.dispatchEvent(new Event("snowthing-profile-updated"));
    } catch (e) { setError(errorText(e, "사진을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.")); }
    finally { setBusy(null); }
  };
  const changePassword = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy) return;
    if (newPassword.length < 8 || newPassword.length > 20 || !newPassword.trim()) { setError("새 비밀번호는 8~20자로 입력해 주세요."); return; }
    if (newPassword !== confirmPassword) { setError("새 비밀번호 확인이 일치하지 않습니다."); return; }
    if (dirty && !window.confirm("저장하지 않은 프로필 변경이 있습니다. 비밀번호를 변경하면 로그아웃됩니다. 계속할까요?")) return;
    setBusy("password"); setError(""); setNotice("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.members.changePassword, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ currentPassword, newPassword }) });
      if (!response.ok) throw new Error(await message(response, "비밀번호를 변경하지 못했습니다."));
      setCurrentPassword(""); setNewPassword(""); setConfirmPassword("");
      allowNavigation.current = true;
      window.location.href = "/login?passwordChanged=1";
    } catch (e) { setError(errorText(e, "비밀번호를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.")); setBusy(null); }
  };
  const withdraw = async (event: React.FormEvent) => {
    event.preventDefault(); if (!profile || busy) return;
    if (!window.confirm("정말 탈퇴할까요? 되돌릴 수 없습니다.")) return;
    setBusy("withdraw"); setError(""); setNotice("");
    try {
      const body = profile.passwordChangeAvailable ? { password: withdrawInput } : { confirmation: withdrawInput };
      const response = await csrfFetch(API_ENDPOINTS.members.withdrawal, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      if (!response.ok) throw new Error(await message(response, "탈퇴를 처리하지 못했습니다."));
      allowNavigation.current = true;
      window.location.href = "/login?withdrawn=1";
    } catch (e) { setError(e instanceof Error ? e.message : "탈퇴를 처리하지 못했습니다."); setBusy(null); }
  };
  const toggle = (key: "resortIds" | "ridingStyleIds", id: number) => {
    if (!form) return;
    const values = form[key];
    if (key === "resortIds" && !values.includes(id) && values.length >= 2) { setError("선호 리조트는 최대 2개까지 선택할 수 있습니다."); return; }
    setForm({ ...form, [key]: values.includes(id) ? values.filter(x => x !== id) : [...values, id] });
  };
  const cancelPasswordChange = () => {
    setPasswordEditorOpen(false);
    setCurrentPassword(""); setNewPassword(""); setConfirmPassword(""); setShowPassword(false);
  };

  return <div className="community-page"><TopNav active="profile" /><main className="community-container profile-settings">
    <header className="profile-settings-heading"><div><h1>내 정보 수정</h1><p>프로필과 계정 정보를 관리합니다.</p></div><Link href="/">홈으로</Link></header>
    {error && <p className="profile-feedback error" role="alert">{error}</p>}{notice && <p className="profile-feedback" role="status">{notice}</p>}
    {loading ? <div className="profile-loading" aria-busy="true">내 정보를 불러오는 중입니다.</div> : !profile || !form ? <button type="button" className="profile-button-primary" onClick={() => { setLoading(true); setError(""); void load(); }}>다시 불러오기</button> : <>
      <section className="profile-section"><div><h2>프로필 사진</h2><p>사진은 별도로 저장됩니다.</p></div><div className="profile-photo-editor">
        {preview ? <Image src={preview} alt="내 프로필 사진" width={96} height={96} unoptimized /> : <CommentProfileAvatar className="profile-photo-avatar" nickname={profile.nickname} profileImageUrl={profile.profileImageUrl} size={96} />}
        <div><label className="profile-file-label">사진 선택<input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" disabled={!!busy} onChange={event => {
          const file = event.target.files?.[0]; if (!file) return;
          if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) { setError("JPG, PNG, WebP 형식의 5MB 이하 사진을 선택해 주세요."); event.target.value = ""; return; }
          setPhoto(file); setPreview(URL.createObjectURL(file)); setError("");
        }} /></label><p>JPG, PNG, WebP, 최대 5MB</p><div className="profile-actions">{photo && <><button className="profile-button-primary" disabled={!!busy} onClick={() => void savePhoto()}>{busy === "photo" ? "저장 중" : "사진 저장"}</button><button className="profile-button-cancel" disabled={!!busy} onClick={() => { setPhoto(null); setPreview(null); if (fileRef.current) fileRef.current.value = ""; }}>선택 취소</button></>}{profile.profileImageUrl && <button disabled={!!busy} onClick={() => void savePhoto(true)}>기본 이미지로 변경</button>}</div></div>
      </div></section>
      <section className="profile-section"><div><h2>비밀번호</h2><p>{passwordEditorOpen ? "변경하면 모든 기기에서 로그아웃됩니다." : "계정 비밀번호를 변경할 수 있습니다."}</p></div>{profile.passwordChangeAvailable ? passwordEditorOpen ? <form id="profile-password-form" onSubmit={changePassword}><fieldset className="profile-fields" disabled={!!busy}>
        <label>현재 비밀번호<input autoFocus type={showPassword ? "text" : "password"} autoComplete="current-password" value={currentPassword} maxLength={200} required onChange={event => setCurrentPassword(event.target.value)} /></label>
        <label>새 비밀번호<input type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={8} maxLength={20} value={newPassword} required onChange={event => setNewPassword(event.target.value)} /><small>8~20자</small></label>
        <label>새 비밀번호 확인<input type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={8} maxLength={20} value={confirmPassword} required onChange={event => setConfirmPassword(event.target.value)} /></label>
        <label className="profile-password-toggle"><input type="checkbox" checked={showPassword} onChange={event => setShowPassword(event.target.checked)} />비밀번호 표시</label><div className="profile-actions"><button className="profile-button-cancel" type="button" onClick={cancelPasswordChange}>변경 취소</button><button type="submit" className="profile-button-primary" disabled={!currentPassword || !newPassword || !confirmPassword}>{busy === "password" ? "변경 중" : "비밀번호 변경"}</button></div>
      </fieldset></form> : <div className="profile-password-entry"><button type="button" aria-expanded={false} aria-controls="profile-password-form" disabled={!!busy} onClick={() => setPasswordEditorOpen(true)}>비밀번호 변경</button></div> : <p className="profile-social-note">소셜 로그인 계정입니다. 비밀번호는 가입한 소셜 계정(카카오, 네이버, 구글) 설정에서 변경해 주세요.</p>}</section>
      <SocialAccountsSection />
      <form onSubmit={save}><fieldset className="profile-section" disabled={!!busy}><div><h2>기본 정보</h2><p>{profile.email}</p></div><div className="profile-fields">
        <label>닉네임<div className="profile-nickname"><input value={form.nickname} minLength={2} maxLength={10} pattern="[a-zA-Z0-9가-힣]{2,10}" required onChange={event => { ++nicknameVersion.current; setCheckedNickname(null); setNicknameMessage(""); setForm({ ...form, nickname: event.target.value }); }} /><button type="button" disabled={checkingNickname || form.nickname === profile.nickname} onClick={() => void checkNickname()}>{checkingNickname ? "확인 중" : form.nickname === profile.nickname ? "현재 닉네임" : "중복 확인"}</button></div><small aria-live="polite">{nicknameMessage || "한글, 영문, 숫자 2~10자"}</small></label>
        <label>자기소개<textarea value={form.bio} rows={3} maxLength={255} onChange={event => setForm({ ...form, bio: event.target.value })} /><small>{form.bio.length}/255</small></label>
        <label>출발 지역<input value={form.departureRegion} maxLength={100} placeholder="예: 서울 송파구" onChange={event => setForm({ ...form, departureRegion: event.target.value })} /></label>
        <fieldset className="profile-preferences"><legend>선호 리조트 <small>최대 2개</small></legend><p className="profile-saved-preferences">저장된 리조트: {profile.resorts.map(x => x.name).join(", ") || "선택한 리조트가 없습니다."}</p><div>{resorts.map(x => <label key={x.id}><input type="checkbox" checked={form.resortIds.includes(x.id)} onChange={() => toggle("resortIds", x.id)} />{x.name}</label>)}</div></fieldset>
        <fieldset className="profile-preferences"><legend>라이딩 스타일</legend><p className="profile-saved-preferences">저장된 스타일: {profile.ridingStyles.map(x => x.name).join(", ") || "선택한 스타일이 없습니다."}</p><div>{styles.map(x => <label key={x.id}><input type="checkbox" checked={form.ridingStyleIds.includes(x.id)} onChange={() => toggle("ridingStyleIds", x.id)} />{x.name}</label>)}</div></fieldset>
        <div className="profile-actions"><button className="profile-button-cancel" type="button" onClick={() => { if (!profileDirty || window.confirm("수정한 정보를 취소할까요?")) { setForm(toForm(profile)); setCheckedNickname(null); setNicknameMessage(""); } }}>수정 취소</button><button type="submit" className="profile-button-primary" disabled={!profileDirty || (form.nickname !== profile.nickname && checkedNickname !== form.nickname)}>{busy === "save" ? "저장 중" : "정보 저장"}</button></div>
      </div></fieldset></form>
      <ScrappedPostsSection />
      <MarketingConsentSection />
      <CarpoolReviewSection />
      <BlockedMembersSection />
      <section className="profile-section profile-withdrawal"><div><h2>회원 탈퇴</h2><p>탈퇴하면 되돌릴 수 없습니다.</p></div>{withdrawOpen ? <form onSubmit={withdraw}><fieldset className="profile-fields" disabled={!!busy}>
        <ul className="profile-withdrawal-notice">
          <li>이메일, 닉네임, 비밀번호, 프로필 사진, 소개, 소셜 연결은 바로 삭제됩니다.</li>
          <li>쓴 글과 댓글은 남고 작성자는 &quot;탈퇴한 회원&quot;으로 표시됩니다. 지우고 싶은 글은 탈퇴 전에 지워 주세요.</li>
          <li>판매글과 카풀 글의 연락처는 지워지고, 판매글은 판매 완료로 바뀝니다.</li>
          <li>같은 이메일로는 30일 동안 다시 가입할 수 없습니다.</li>
        </ul>
        {profile.passwordChangeAvailable
          ? <label>비밀번호 확인<input type="password" autoComplete="current-password" value={withdrawInput} maxLength={200} required onChange={event => setWithdrawInput(event.target.value)} /></label>
          : <label>확인 문구<input value={withdrawInput} maxLength={20} placeholder="탈퇴" required onChange={event => setWithdrawInput(event.target.value)} /><small>확인을 위해 &quot;탈퇴&quot;를 입력해 주세요.</small></label>}
        <div className="profile-actions"><button className="profile-button-cancel" type="button" onClick={() => { setWithdrawOpen(false); setWithdrawInput(""); }}>취소</button><button type="submit" className="profile-button-danger" disabled={!withdrawInput || (!profile.passwordChangeAvailable && withdrawInput !== "탈퇴")}>{busy === "withdraw" ? "처리 중" : "탈퇴하기"}</button></div>
      </fieldset></form> : <div className="profile-password-entry"><button type="button" disabled={!!busy} onClick={() => setWithdrawOpen(true)}>회원 탈퇴</button></div>}</section>

    </>}
  </main><Footer /></div>;
}
