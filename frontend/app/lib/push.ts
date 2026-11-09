import { API_V1_URL } from "./api";
import { csrfFetch } from "./csrfFetch";

const PUSH_URL = `${API_V1_URL}/push`;
const SERVICE_WORKER_URL = "/sw.js";

export type PushState =
  | "loading"
  | "unsupported" // 브라우저가 서비스 워커나 푸시를 지원하지 않는다
  | "ios-install" // iOS Safari 탭: 홈 화면에 추가한 앱에서만 된다
  | "server-off" // 서버에 VAPID 키가 없다
  | "denied" // 사용자가 알림 권한을 막았다
  | "off"
  | "on";

interface PushConfig {
  enabled: boolean;
  publicKey: string | null;
}

function isIos() {
  const ua = navigator.userAgent;
  // iPadOS 13+ Safari는 데스크톱 맥으로 보이므로 터치 지원으로 가린다.
  return /iPhone|iPad|iPod/.test(ua) || (ua.includes("Macintosh") && navigator.maxTouchPoints > 1);
}

function isStandalone() {
  return (
    window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

function pushSupported() {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

async function fetchConfig(): Promise<PushConfig | null> {
  const response = await fetch(`${PUSH_URL}/config`, { credentials: "include" });
  if (!response.ok) return null;
  return response.json();
}

async function currentSubscription() {
  const registration = await navigator.serviceWorker.getRegistration(SERVICE_WORKER_URL);
  return registration ? registration.pushManager.getSubscription() : null;
}

async function saveSubscription(subscription: PushSubscription) {
  const response = await csrfFetch(`${PUSH_URL}/subscriptions`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(subscription.toJSON()),
  });
  return response.ok;
}

function base64UrlToBytes(value: string) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (value.length % 4)) % 4);
  const raw = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

/**
 * 지금 기기의 휴대폰 알림 상태. 이미 켜져 있으면 구독을 서버에 한 번 더 보내 최근 사용 시각을 고치고,
 * 이 기기에서 다른 계정으로 로그인했다면 구독을 지금 계정으로 옮긴다.
 */
export async function readPushState(): Promise<PushState> {
  const config = await fetchConfig().catch(() => null);
  if (!config?.enabled || !config.publicKey) return "server-off";
  if (!pushSupported()) {
    return isIos() && !isStandalone() ? "ios-install" : "unsupported";
  }
  if (Notification.permission === "denied") return "denied";
  const subscription = await currentSubscription().catch(() => null);
  if (!subscription || Notification.permission !== "granted") return "off";
  const saved = await saveSubscription(subscription).catch(() => false);
  return saved ? "on" : "off";
}

/** 권한을 묻고 구독을 만든 뒤 서버에 저장한다. 저장에 실패하면 브라우저 쪽 구독도 되돌린다. */
export async function enablePush(): Promise<PushState> {
  const config = await fetchConfig();
  if (!config?.enabled || !config.publicKey) return "server-off";
  const permission = await Notification.requestPermission();
  if (permission === "denied") return "denied";
  if (permission !== "granted") return "off";

  const registration = await navigator.serviceWorker.register(SERVICE_WORKER_URL, { scope: "/" });
  await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlToBytes(config.publicKey),
    });
  }
  if (await saveSubscription(subscription).catch(() => false)) return "on";
  await subscription.unsubscribe().catch(() => false);
  throw new Error("구독 저장 실패");
}

/** 서버에서 지운 뒤 브라우저 구독도 해지한다. 서버 요청이 실패해도 기기 쪽은 끈다. */
export async function disablePush(): Promise<PushState> {
  const subscription = await currentSubscription();
  if (subscription) {
    await csrfFetch(`${PUSH_URL}/subscriptions`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ endpoint: subscription.endpoint }),
    }).catch(() => undefined);
    await subscription.unsubscribe().catch(() => false);
  }
  return "off";
}

/**
 * 로그아웃 직전에 부른다. 공유 기기에서 로그아웃한 계정의 알림이 계속 뜨지 않게 이 기기 구독을 지운다.
 * 로그아웃을 오래 붙잡지 않도록 1.5초가 지나면 기다리지 않는다.
 */
export async function disablePushBeforeLogout() {
  if (typeof window === "undefined" || !pushSupported()) return;
  await Promise.race([
    disablePush().catch(() => undefined),
    new Promise((resolve) => window.setTimeout(resolve, 1500)),
  ]);
}
