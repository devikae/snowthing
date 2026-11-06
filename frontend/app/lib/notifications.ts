import { API_V1_URL } from "./api";
import { csrfFetch } from "./csrfFetch";

export type NotificationType = "POST_COMMENT" | "COMMENT_REPLY" | "INQUIRY_ANSWER";

export interface NotificationItem {
  notificationId: string;
  type: NotificationType;
  postPublicId: string;
  postCategoryCode: string;
  commentId: string;
  actorName: string;
  preview: string;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

const NOTIFICATIONS_URL = `${API_V1_URL}/notifications`;

export const NOTIFICATION_ENDPOINTS = {
  list: (options: { unreadOnly?: boolean; cursor?: string | null; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (options.unreadOnly) params.set("unreadOnly", "true");
    if (options.cursor) params.set("cursor", options.cursor);
    params.set("size", String(options.size ?? 20));
    return `${NOTIFICATIONS_URL}?${params.toString()}`;
  },
  unreadCount: `${NOTIFICATIONS_URL}/unread-count`,
  read: (notificationId: string) => `${NOTIFICATIONS_URL}/${encodeURIComponent(notificationId)}/read`,
  readAll: `${NOTIFICATIONS_URL}/read-all`,
} as const;

/** 읽음 처리나 모두 읽음 뒤 헤더 배지를 다시 세게 알리는 이벤트 이름. */
export const NOTIFICATIONS_CHANGED_EVENT = "snowthing-notifications-changed";

export function notifyNotificationsChanged() {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
}

export function notificationMessage(item: NotificationItem) {
  switch (item.type) {
    case "POST_COMMENT":
      return `${item.actorName}님이 내 글에 댓글을 남겼어요`;
    case "COMMENT_REPLY":
      return `${item.actorName}님이 내 댓글에 답글을 남겼어요`;
    case "INQUIRY_ANSWER":
      return "문의에 답변이 달렸어요";
    default:
      return "새 알림이 있어요";
  }
}

/** 중고장터와 카풀 글은 전용 상세 화면으로 보낸다. */
export function notificationHref(item: NotificationItem) {
  const base = item.postCategoryCode === "MARKET" ? "/market" : item.postCategoryCode === "CARPOOL" ? "/carpool" : "/posts";
  return `${base}/${encodeURIComponent(item.postPublicId)}#comments`;
}

/** 서버 시각은 시간대 없는 KST라서 +09:00을 붙여 해석한다. */
export function formatNotificationTime(createdAt: string, now = Date.now()) {
  const normalized = createdAt.replace(/(\.\d{3})\d+/, "$1");
  const time = new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(normalized) ? normalized : `${normalized}+09:00`).getTime();
  if (Number.isNaN(time)) return "";
  const diffSec = Math.max(0, Math.floor((now - time) / 1000));
  if (diffSec < 60) return "방금";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}일 전`;
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric" }).format(time);
}

export async function markNotificationRead(notificationId: string) {
  const response = await csrfFetch(NOTIFICATION_ENDPOINTS.read(notificationId), { method: "PUT" });
  if (response.ok) notifyNotificationsChanged();
  return response.ok;
}

export async function markAllNotificationsRead() {
  const response = await csrfFetch(NOTIFICATION_ENDPOINTS.readAll, { method: "PUT" });
  if (response.ok) notifyNotificationsChanged();
  return response.ok;
}
