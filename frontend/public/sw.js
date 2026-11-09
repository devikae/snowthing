// 눈팅 서비스 워커. 웹 푸시 표시와 알림 클릭만 처리한다.
// fetch 이벤트를 듣지 않으므로 페이지 요청과 캐시에는 관여하지 않는다(오프라인 캐시 없음).

const DEFAULT_URL = "/notifications";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = {};
  }
  const title = typeof data.title === "string" && data.title ? data.title : "눈팅";
  const body = typeof data.body === "string" && data.body ? data.body : "새 알림이 있어요";
  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: "/icons/icon-192.png",
      badge: "/icons/badge-96.png",
      lang: "ko",
      data: { url: safeUrl(data.url) },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = safeUrl(event.notification.data && event.notification.data.url);
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      for (const client of windows) {
        if (client.url === target && "focus" in client) return client.focus();
      }
      return self.clients.openWindow(target);
    }),
  );
});

// 같은 출처 경로만 연다. 서버가 보내는 값이지만 외부 주소로 새는 일이 없게 한 번 더 막는다.
function safeUrl(value) {
  try {
    const url = new URL(typeof value === "string" ? value : DEFAULT_URL, self.location.origin);
    if (url.origin === self.location.origin) return url.href;
  } catch {
    // 아래 기본값으로 간다.
  }
  return new URL(DEFAULT_URL, self.location.origin).href;
}
