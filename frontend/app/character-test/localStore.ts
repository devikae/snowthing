import { ANSWERS_PATTERN, type Sport } from "./data";

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
  return saved && ANSWERS_PATTERN.test(saved.answers) && (saved.sport === "SKI" || saved.sport === "BOARD") ? saved : null;
}

export function saveAnswers(saved: SavedAnswers) {
  write(SAVED_ANSWERS_KEY, saved);
}

// 푸는 중인 테스트. 카톡 링크를 오가다 화면이 새로 열려도 이어서 풀게 한다. 결과가 나오면 지운다.
const QUIZ_PROGRESS_KEY = "snowthing-character-progress";

export type Choice = "A" | "B" | "C";

export interface QuizProgress {
  sport: Sport;
  order: number[];
  picked: Choice[];
}

export function getQuizProgress(questionCount: number): QuizProgress | null {
  const saved = read<QuizProgress>(QUIZ_PROGRESS_KEY);
  if (!saved || (saved.sport !== "SKI" && saved.sport !== "BOARD")) return null;
  const { order, picked } = saved;
  // 질문 수가 바뀌었거나 값이 깨졌으면 버린다. 순서는 0부터 질문 수까지 한 번씩 있어야 한다.
  const validOrder = Array.isArray(order) && order.length === questionCount && [...order].sort((a, b) => a - b).every((value, index) => value === index);
  const validPicked = Array.isArray(picked) && picked.length < questionCount && picked.every((choice) => choice === "A" || choice === "B" || choice === "C");
  return validOrder && validPicked ? saved : null;
}

export function saveQuizProgress(progress: QuizProgress) {
  write(QUIZ_PROGRESS_KEY, progress);
}

export function clearQuizProgress() {
  try {
    window.localStorage.removeItem(QUIZ_PROGRESS_KEY);
  } catch {
    // 저장이 막힌 브라우저면 지울 것도 없다.
  }
}
