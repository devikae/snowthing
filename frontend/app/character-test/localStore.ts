import type { Sport } from "./data";

// 브라우저에만 두는 값. 접근 토큰은 XSS가 나면 읽힐 수 있지만, 비회원이라 세션이 없고 토큰의 권한이 이 지도 하나로 좁아 localStorage를 쓴다.
const MEMBERSHIPS_KEY = "snowthing-character-memberships";
const SAVED_ANSWERS_KEY = "snowthing-character-answers";

export interface Membership {
  token: string;
  nickname: string;
  groupName?: string;
}

export interface SavedMembership extends Membership {
  groupPublicId: string;
}

export interface SavedAnswers {
  answers: string;
  sport: Sport;
}

function read<T>(key: string): T | null {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 시크릿 창 등에서 저장이 막혀도 이번 화면은 계속 쓸 수 있다.
  }
}

export function getMembership(groupPublicId: string): Membership | null {
  return read<Record<string, Membership>>(MEMBERSHIPS_KEY)?.[groupPublicId] ?? null;
}

export function getMemberships(): SavedMembership[] {
  const memberships = read<Record<string, Membership>>(MEMBERSHIPS_KEY) ?? {};
  return Object.entries(memberships).map(([groupPublicId, membership]) => ({ ...membership, groupPublicId }));
}

export function saveMembership(groupPublicId: string, membership: Membership) {
  write(MEMBERSHIPS_KEY, { ...read<Record<string, Membership>>(MEMBERSHIPS_KEY), [groupPublicId]: membership });
}

export function removeMembership(groupPublicId: string) {
  const memberships = { ...read<Record<string, Membership>>(MEMBERSHIPS_KEY) };
  delete memberships[groupPublicId];
  write(MEMBERSHIPS_KEY, memberships);
}

export function getSavedAnswers(): SavedAnswers | null {
  const saved = read<SavedAnswers>(SAVED_ANSWERS_KEY);
  return saved && /^[AB]{12}[ABC]{3}$/.test(saved.answers) && (saved.sport === "SKI" || saved.sport === "BOARD") ? saved : null;
}

export function saveAnswers(saved: SavedAnswers) {
  write(SAVED_ANSWERS_KEY, saved);
}
