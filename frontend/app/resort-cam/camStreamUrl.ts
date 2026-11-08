// 운영 사이트에서는 캠 영상을 CloudFront(cam.snowthing.org)로 받는다.
// 같은 캠을 보는 시청자끼리 조각을 공유해 서버 송신량이 시청자 수에 비례하지 않게 한다.
const CAM_CDN_ORIGIN = "https://cam.snowthing.org";
const CDN_PAGE_HOSTS = new Set(["snowthing.org", "www.snowthing.org"]);

export function toCamStreamUrl(sourceUrl: string): string {
  if (!sourceUrl.startsWith("/stream-proxy/") || typeof window === "undefined") return sourceUrl;
  return CDN_PAGE_HOSTS.has(window.location.hostname) ? `${CAM_CDN_ORIGIN}${sourceUrl}` : sourceUrl;
}
