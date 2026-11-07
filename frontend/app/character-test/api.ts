import { API_BASE_URL } from "../lib/api";
import { csrfFetch as rawCsrfFetch } from "../lib/csrfFetch";
import { readErrorMessage } from "../lib/messageApi";
import type { CharacterLabel, CharacterStats, LevelCode, Sport } from "./data";

const GROUPS = `${API_BASE_URL}/api/v1/character-groups`;
const TOKEN_HEADER = "X-Character-Token";
// 한 와이파이에서 1초에 10명이 넘게 들어오면 제한에 걸릴 수 있어 한 번은 조용히 다시 보낸다.
const RATE_LIMIT_RETRY_MS = 1200;

export interface CharacterResult {
  character: CharacterLabel;
  stats: CharacterStats;
  highlightQuestions: number[];
  bestMatch: CharacterLabel | null;
}

export interface GroupPreview {
  publicId: string;
  name: string;
  participantCount: number;
  maxParticipants: number;
}

export interface JoinResponse {
  groupPublicId: string;
  groupName: string;
  participantPublicId: string;
  accessToken: string;
}

export interface MapPerson {
  participantPublicId: string;
  nickname: string;
  sport: Sport;
  character: CharacterLabel;
  score: number;
  habitGaps: number[];
}

export interface MapLevel {
  level: LevelCode;
  participants: MapPerson[];
}

export interface CharacterMap {
  groupPublicId: string;
  groupName: string;
  participantCount: number;
  maxParticipants: number;
  owner: boolean;
  me: { participantPublicId: string; nickname: string; sport: Sport; answers: string; result: CharacterResult };
  levels: MapLevel[];
}

export interface JoinInput {
  nickname: string;
  sport: Sport;
  answers: string;
}

// 연결 실패의 브라우저 문구(Failed to fetch)를 화면에 그대로 보여 주지 않는다.
function friendlyNetworkError<T>(request: Promise<T>) {
  return request.catch(() => {
    throw new Error("연결이 불안정해요. 잠시 후 다시 시도해 주세요.");
  });
}
const csrfFetch: typeof rawCsrfFetch = (...args) => friendlyNetworkError(rawCsrfFetch(...args));
const plainFetch: typeof fetch = (...args) => friendlyNetworkError(fetch(...args));

const jsonHeaders = { "Content-Type": "application/json" };
const GROUP_NOT_FOUND_CODE = "CHARACTER_001";

// 배포 중이나 프록시가 돌려준 404로 접속 정보를 지우면 안 된다. 서버가 없는 모임이라고 답한 경우만 없는 것으로 본다.
async function isGroupNotFound(response: Response) {
  if (response.status !== 404) return false;
  const data = await response.clone().json().catch(() => null);
  return data?.code === GROUP_NOT_FOUND_CODE;
}

export async function fetchResult(answers: string): Promise<CharacterResult> {
  const response = await csrfFetch(`${API_BASE_URL}/api/v1/character-test/result`, {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ answers }),
  });
  if (!response.ok) throw new Error(await readErrorMessage(response, "결과를 불러오지 못했습니다. 다시 시도해 주세요."));
  return response.json();
}

async function postJoin(url: string, input: JoinInput): Promise<JoinResponse> {
  const send = () => csrfFetch(url, { method: "POST", headers: jsonHeaders, body: JSON.stringify(input) });
  let response = await send();
  if (response.status === 429) {
    await new Promise((resolve) => setTimeout(resolve, RATE_LIMIT_RETRY_MS));
    response = await send();
  }
  if (!response.ok) throw new Error(await readErrorMessage(response, "들어가지 못했습니다. 다시 시도해 주세요."));
  return response.json();
}

export function createGroup(input: JoinInput) {
  return postJoin(GROUPS, input);
}

export function joinGroup(groupPublicId: string, input: JoinInput) {
  return postJoin(`${GROUPS}/${encodeURIComponent(groupPublicId)}/participants`, input);
}

// 서버가 없는 모임이라고 답하면 null로 돌려준다. 호출한 쪽이 없는 모임 화면을 보여 준다.
export async function fetchPreview(groupPublicId: string): Promise<GroupPreview | null> {
  const response = await plainFetch(`${GROUPS}/${encodeURIComponent(groupPublicId)}`, { cache: "no-store" });
  if (await isGroupNotFound(response)) return null;
  if (!response.ok) throw new Error("모임 정보를 불러오지 못했습니다.");
  return response.json();
}

// 토큰이 없거나 소속이 아니면 서버가 CHARACTER_001로 답한다. 이때 null이고, 다른 실패는 오류로 던져 접속 정보를 지키게 한다.
export async function fetchMap(groupPublicId: string, token: string): Promise<CharacterMap | null> {
  const response = await plainFetch(`${GROUPS}/${encodeURIComponent(groupPublicId)}/map`, {
    cache: "no-store",
    headers: { [TOKEN_HEADER]: token },
  });
  if (await isGroupNotFound(response)) return null;
  if (!response.ok) throw new Error("지도를 불러오지 못했습니다.");
  return response.json();
}

export async function leaveGroup(token: string) {
  const response = await csrfFetch(`${API_BASE_URL}/api/v1/character-participants/me`, {
    method: "DELETE",
    headers: { [TOKEN_HEADER]: token },
  });
  if (!response.ok && !(await isGroupNotFound(response))) throw new Error(await readErrorMessage(response, "나가지 못했습니다."));
}
