"use client";

import { useEffect, useState } from "react";
import { shareLink } from "../character-test/shareFile";
import styles from "./kakaoShare.module.css";

// 카카오 JavaScript SDK(공유용). 지도 SDK(dapi.kakao.com, window.kakao 소문자)와는 다른 파일이며 window.Kakao(대문자)를 쓴다.
// 버전과 integrity 값은 https://developers.kakao.com/docs/ko/javascript/download 에 게시된 값이다. 버전을 올릴 때 둘 다 바꾼다.
const KAKAO_JS_SDK_URL = "https://t1.kakaocdn.net/kakao_js_sdk/2.8.4/kakao.min.js";
const KAKAO_JS_SDK_INTEGRITY = "sha384-BGX7Wb16GmRFagri4bLJV2DgipVZaU2AI812GZtHtHzzDQJnfHasDXN5houxv45w";
const KAKAO_JS_SDK_SCRIPT_ID = "kakao-js-sdk";
const SDK_TIMEOUT_MS = 5000;

type KakaoLink = { mobileWebUrl: string; webUrl: string };
type KakaoJsSdk = {
  init: (appKey: string) => void;
  isInitialized: () => boolean;
  Share?: {
    sendDefault: (settings: {
      objectType: "feed";
      content: { title: string; description: string; imageUrl: string; link: KakaoLink };
      buttons: { title: string; link: KakaoLink }[];
    }) => void;
  };
};

declare global {
  interface Window {
    Kakao?: KakaoJsSdk;
  }
}

let sdkPromise: Promise<KakaoJsSdk | null> | null = null;

// 같은 화면에 버튼이 여러 개여도 스크립트는 한 번만 넣는다. 키가 없거나 실패하면 null을 돌려 대체 공유로 넘어간다.
function loadKakaoSdk(): Promise<KakaoJsSdk | null> {
  if (sdkPromise) return sdkPromise;
  const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_APP_KEY;
  if (!appKey) return Promise.resolve(null);
  sdkPromise = new Promise<KakaoJsSdk | null>((resolve) => {
    const ready = () => {
      const sdk = window.Kakao;
      if (!sdk) return resolve(null);
      try {
        // Kakao.Share는 init() 뒤에 붙는다. init 전에 검사하면 항상 없다.
        if (!sdk.isInitialized()) sdk.init(appKey);
        resolve(sdk.Share ? sdk : null);
      } catch {
        resolve(null);
      }
    };
    if (window.Kakao) return ready();
    const timer = window.setTimeout(() => resolve(null), SDK_TIMEOUT_MS);
    const script = document.createElement("script");
    script.id = KAKAO_JS_SDK_SCRIPT_ID;
    script.src = KAKAO_JS_SDK_URL;
    script.integrity = KAKAO_JS_SDK_INTEGRITY;
    script.crossOrigin = "anonymous";
    script.async = true;
    script.onload = () => {
      window.clearTimeout(timer);
      ready();
    };
    script.onerror = () => {
      window.clearTimeout(timer);
      resolve(null);
    };
    document.head.appendChild(script);
  }).then((sdk) => {
    // 실패는 기억하지 않는다. 다음 클릭에서 다시 시도한다.
    if (!sdk) {
      sdkPromise = null;
      document.getElementById(KAKAO_JS_SDK_SCRIPT_ID)?.remove();
    }
    return sdk;
  });
  return sdkPromise;
}

type Props = {
  title: string;
  description: string;
  /** 같은 출처 경로 또는 절대 주소. 카카오 공유는 등록된 도메인 링크만 연다. */
  url: string;
  /** 공개 주소로 받을 수 있는 이미지. 경로를 주면 현재 출처로 바꾼다. 없으면 사이트 기본 OG 이미지. */
  imageUrl?: string | null;
  buttonTitle?: string;
  /** 한 화면에 공유 대상이 둘 이상이면 구분되는 글자를 준다. */
  label?: string;
  /** 아이콘만 보이고 글자는 화면 읽기 프로그램에만 남긴다. */
  iconOnly?: boolean;
  /** SDK를 못 쓸 때 부른다. 없으면 공유 창, 안 되면 링크 복사. */
  onFallback?: () => void | Promise<void>;
};

const FALLBACK_NOTICE = { shared: "", cancelled: "", copied: "링크를 복사했어요.", failed: "링크를 복사하지 못했어요." } as const;

export default function KakaoShareButton({ title, description, url, imageUrl, buttonTitle = "자세히 보기", label = "카카오톡 공유", iconOnly = false, onFallback }: Props) {
  const [notice, setNotice] = useState("");

  // 데스크톱은 공유 창을 팝업으로 연다. 클릭 뒤에 스크립트를 받으면 팝업이 막힐 수 있어 버튼이 보일 때 미리 받는다.
  useEffect(() => {
    void loadKakaoSdk();
  }, []);

  const fallback = async (absoluteUrl: string) => {
    if (onFallback) return onFallback();
    setNotice(FALLBACK_NOTICE[await shareLink(absoluteUrl, title, description)]);
  };

  const share = async () => {
    setNotice("");
    const origin = window.location.origin;
    const link = new URL(url, origin);
    // 다른 출처 주소는 카카오가 열지 않는다. 받은 쪽에서 막히지 않게 같은 출처만 쓴다.
    if (link.origin !== origin) return;
    const absoluteUrl = link.toString();
    const sdk = await loadKakaoSdk();
    if (!sdk?.Share) return fallback(absoluteUrl);
    const linkObject = { mobileWebUrl: absoluteUrl, webUrl: absoluteUrl };
    try {
      sdk.Share.sendDefault({
        objectType: "feed",
        content: { title, description: description || title, imageUrl: new URL(imageUrl || "/og-default.png", origin).toString(), link: linkObject },
        buttons: [{ title: buttonTitle, link: linkObject }],
      });
    } catch {
      await fallback(absoluteUrl);
    }
  };

  return (
    <>
      <button type="button" className={iconOnly ? `${styles.button} ${styles.iconOnly}` : styles.button} onClick={() => void share()} title={iconOnly ? label : undefined}>
        <svg className={styles.symbol} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <path d="M12 3C6.48 3 2 6.48 2 10.78c0 2.78 1.86 5.22 4.66 6.6l-.95 3.48c-.08.3.26.54.52.37l4.13-2.73c.54.07 1.09.11 1.64.11 5.52 0 10-3.48 10-7.83S17.52 3 12 3Z" />
        </svg>
        {iconOnly ? <span className="sr-only">{label}</span> : label}
      </button>
      {notice && <span className={styles.notice} role="status">{notice}</span>}
    </>
  );
}
