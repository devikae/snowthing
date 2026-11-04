import { API_ENDPOINTS, API_V1_URL } from "./api";
import { csrfFetch } from "./csrfFetch";

// 쪽지와 차단 API. 다른 작업과 api.ts 충돌을 줄이려고 따로 둔다.
export const MESSAGE_ENDPOINTS = {
  send: `${API_V1_URL}/messages`,
  received: (cursor?: string | null) =>
    `${API_V1_URL}/messages/received${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ""}`,
  sent: (cursor?: string | null) =>
    `${API_V1_URL}/messages/sent${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ""}`,
  detail: (id: string) => `${API_V1_URL}/messages/${encodeURIComponent(id)}`,
  unreadCount: `${API_V1_URL}/messages/unread-count`,
  blocks: `${API_V1_URL}/members/blocks`,
  block: (memberPublicId: string) => `${API_V1_URL}/members/blocks/${encodeURIComponent(memberPublicId)}`,
} as const;

// 다른 화면에서 쪽지를 읽거나 지웠을 때 헤더 배지를 바로 다시 읽게 한다.
export const MESSAGES_UPDATED_EVENT = "snowthing-messages-updated";

export function notifyMessagesUpdated() {
  window.dispatchEvent(new Event(MESSAGES_UPDATED_EVENT));
}

export type MessageBox = "RECEIVED" | "SENT";

export interface MessageMember {
  publicId: string | null;
  nickname: string;
  profileImageUrl: string | null;
  withdrawn: boolean;
}

export interface MessageListing {
  publicId: string | null;
  title: string | null;
  deleted: boolean;
}

export interface MessageSummary {
  id: string;
  box: MessageBox;
  counterpart: MessageMember;
  preview: string;
  read: boolean;
  marketListing: MessageListing | null;
  createdAt: string;
}

export interface MessageDetail {
  id: string;
  box: MessageBox;
  counterpart: MessageMember;
  content: string;
  read: boolean;
  readAt: string | null;
  marketListing: MessageListing | null;
  canReply: boolean;
  createdAt: string;
}

export interface MessagePage {
  content: MessageSummary[];
  pageInfo: { nextCursor: string | null; hasNext: boolean; pageSize: number };
}

export interface BlockedMember {
  publicId: string;
  nickname: string;
  profileImageUrl: string | null;
  withdrawn: boolean;
  blockedAt: string;
}

export const MESSAGE_MAX_LENGTH = 1000;

export async function readErrorMessage(response: Response, fallback: string) {
  const data = await response.json().catch(() => null);
  return typeof data?.message === "string" ? data.message : fallback;
}

/** 쪽지 작성 화면 주소. 판매글에서 보낼 때는 판매글 publicId와 제목을 함께 넘긴다. */
export function composeMessageHref(
  receiver: { publicId: string; nickname: string },
  listing?: { publicId: string; title: string },
) {
  const params = new URLSearchParams({ to: receiver.publicId, name: receiver.nickname });
  if (listing) {
    params.set("listing", listing.publicId);
    params.set("listingTitle", listing.title);
  }
  return `/messages/new?${params.toString()}`;
}

export async function blockMember(memberPublicId: string) {
  const response = await csrfFetch(MESSAGE_ENDPOINTS.block(memberPublicId), { method: "POST" });
  if (!response.ok) throw new Error(await readErrorMessage(response, "차단하지 못했습니다."));
}

export async function unblockMember(memberPublicId: string) {
  const response = await csrfFetch(MESSAGE_ENDPOINTS.block(memberPublicId), { method: "DELETE" });
  if (!response.ok) throw new Error(await readErrorMessage(response, "차단을 해제하지 못했습니다."));
}

// 한 화면에 작성자 메뉴가 여러 개 있어도 내 정보 요청은 한 번만 보낸다. 로그인 상태가 바뀔 수 있어 잠깐만 재사용한다.
const VIEWER_CACHE_MS = 10_000;
let viewerCache: { at: number; promise: Promise<string | null> } | null = null;

export function fetchViewerPublicId(): Promise<string | null> {
  if (viewerCache && Date.now() - viewerCache.at < VIEWER_CACHE_MS) return viewerCache.promise;
  const promise = fetch(API_ENDPOINTS.members.me, { credentials: "include" })
    .then((response) => (response.ok ? response.json() : null))
    .then((member: { publicId?: string } | null) => member?.publicId ?? null)
    .catch(() => null);
  viewerCache = { at: Date.now(), promise };
  return promise;
}

// 서버 시각은 오프셋 없는 KST 문자열이라 브라우저 시간대와 상관없이 KST로 읽고 KST로 보여 준다.
export function formatMessageDate(value: string) {
  const hasOffset = /([zZ]|[+-]\d{2}:?\d{2})$/.test(value);
  return new Date(hasOffset ? value : `${value}+09:00`).toLocaleString("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
