// 구글은 앱 안 브라우저(웹뷰)의 로그인을 403 disallowed_useragent로 막는다.
// 카카오톡과 라인은 외부 브라우저로 여는 공식 방법이 있고, 안드로이드는 크롬 intent로 열 수 있다.
export type InAppBrowser = "kakaotalk" | "line" | "other";

const OTHER_IN_APP = [/Instagram/i, /FBAN|FBAV|FB_IAB/i, /NAVER\(inapp/i, /DaumApps/i, /everytimeApp/i, /BAND\//, /Twitter/i, /; wv\)/];

const INTENT_CAPABLE = /Instagram|FBAN|FBAV|FB_IAB/i;

export function detectInAppBrowser(userAgent: string): InAppBrowser | null {
  if (/KAKAOTALK/i.test(userAgent)) return "kakaotalk";
  if (/ Line\//i.test(userAgent)) return "line";
  return OTHER_IN_APP.some((pattern) => pattern.test(userAgent)) ? "other" : null;
}

// 외부 브라우저로 바로 열 수 있으면 그 주소를, 사용자가 직접 열어야 하면 null을 돌려준다.
export function externalBrowserUrl(browser: InAppBrowser, pageUrl: string, userAgent: string): string | null {
  if (browser === "kakaotalk") return `kakaotalk://web/openExternal?url=${encodeURIComponent(pageUrl)}`;
  if (browser === "line") {
    const url = new URL(pageUrl);
    url.searchParams.set("openExternalBrowser", "1");
    return url.toString();
  }
  // 웹뷰는 앱이 직접 처리하지 않으면 intent://를 열지 못하고 오류 화면을 띄운다. 처리하는 것으로 알려진 앱에만 쓴다.
  if (/Android/i.test(userAgent) && INTENT_CAPABLE.test(userAgent)) {
    const url = new URL(pageUrl);
    return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=${url.protocol.replace(":", "")};package=com.android.chrome;end`;
  }
  return null;
}
