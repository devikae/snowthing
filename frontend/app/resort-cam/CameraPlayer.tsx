"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { ResortCameraItem } from "./types";
import { toCamStreamUrl } from "./camStreamUrl";
import styles from "./resortCam.module.css";

interface CameraPlayerProps {
  camera: ResortCameraItem;
  autoPlay: boolean;
  focused?: boolean;
}

// 아무 조작 없이 영상을 계속 받으면 서버 송신량이 시청 시간만큼 늘어난다.
const IDLE_STOP_MS = 20 * 60 * 1000;
const ACTIVITY_EVENTS = ["pointerdown", "keydown", "wheel", "touchstart"] as const;

function subscribeVisibility(onChange: () => void) {
  document.addEventListener("visibilitychange", onChange);
  return () => document.removeEventListener("visibilitychange", onChange);
}

function usePageVisible() {
  return useSyncExternalStore(subscribeVisibility, () => document.visibilityState !== "hidden", () => true);
}

export function CameraPlayer({ camera, autoPlay, focused = false }: CameraPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [manualPlay, setManualPlay] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [retryAttempt, setRetryAttempt] = useState(0);
  const [failureReason, setFailureReason] = useState("");

  const [idleStopped, setIdleStopped] = useState(false);
  const pageVisible = usePageVisible();

  // 탭이 가려지면 재생을 멈추고, 돌아오면 다시 연결한다.
  const shouldLoad = (focused || manualPlay || autoPlay) && pageVisible && !idleStopped;
  const officialUrl = camera.externalPageUrl || camera.sourceUrl || "";

  useEffect(() => {
    const video = videoRef.current;
    if (camera.sourceType !== "HLS" || !video || !shouldLoad) return;
    const streamUrl = toCamStreamUrl(camera.sourceUrl);

    let disposed = false;
    let usingHls = false;
    let destroy: (() => void) | undefined;
    const fail = (reason = "connection-error") => {
      if (disposed) return;
      window.clearTimeout(startupTimer);
      destroy?.();
      destroy = undefined;
      setFailed(true);
      setFailureReason(reason);
      setLoading(false);
    };
    let startupTimer = window.setTimeout(() => fail("startup-timeout"), 18000);
    const onNativeError = () => { if (!usingHls) fail(`native-media-${video.error?.code ?? 0}`); };
    const onReady = () => {
      if (disposed) return;
      window.clearTimeout(startupTimer);
      setLoading(false);
    };
    video.addEventListener("loadeddata", onReady);
    video.addEventListener("error", onNativeError);
    setFailed(false);
    setLoading(true);

    void (async () => {
      try {
        const { default: Hls } = await import("hls.js");
        if (disposed) return;
        // Chromium can report native HLS support but stall on valid camera streams.
        if (Hls.isSupported()) {
          usingHls = true;
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
          destroy = () => hls.destroy();
          let mediaRecoveries = 0;
          let nativeFallback = false;
          hls.loadSource(streamUrl);
          hls.attachMedia(video);
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            if (disposed) return;
            void video.play().catch(() => undefined);
          });
          hls.on(Hls.Events.ERROR, (_, data) => {
            if (data.fatal) {
              if (data.type === Hls.ErrorTypes.MEDIA_ERROR && mediaRecoveries++ === 0) {
                hls.recoverMediaError();
              } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !nativeFallback && video.canPlayType("application/vnd.apple.mpegurl")) {
                nativeFallback = true;
                usingHls = false;
                destroy?.();
                destroy = undefined;
                window.clearTimeout(startupTimer);
                startupTimer = window.setTimeout(() => fail("startup-timeout"), 18000);
                video.src = streamUrl;
                video.load();
                void video.play().catch(() => undefined);
              } else fail(data.details);
            }
          });
        } else if (video.canPlayType("application/vnd.apple.mpegurl")) video.src = streamUrl;
        else { fail("unsupported-browser"); return; }
        void video.play().catch(() => undefined);
      } catch {
        fail("initialization-failed");
      }
    })();

    return () => {
      disposed = true;
      window.clearTimeout(startupTimer);
      video.removeEventListener("loadeddata", onReady);
      video.removeEventListener("error", onNativeError);
      destroy?.();
      video.pause();
      video.removeAttribute("src");
      video.load();
    };
  }, [camera.sourceType, camera.sourceUrl, manualPlay, shouldLoad, retryAttempt]);

  useEffect(() => {
    if (!shouldLoad) return;
    let timer = window.setTimeout(() => setIdleStopped(true), IDLE_STOP_MS);
    const resetTimer = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setIdleStopped(true), IDLE_STOP_MS);
    };
    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, resetTimer, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, resetTimer));
    };
  }, [shouldLoad]);

  // 순수 영상 iframe 전용 (vivaldi.html, rtsp.me 등)
  const isDedicatedVideoIframe =
    camera.sourceType === "IFRAME" ||
    (camera.sourceType === "EXTERNAL_LINK" && camera.sourceUrl.includes("pop_webcam.do"));
  const showStatus = (camera.sourceType === "EXTERNAL_LINK" && !isDedicatedVideoIframe) || failed;

  return (
    <div className={`${styles.playerContainer} ${showStatus ? styles.statusContainer : ""}`} data-playback-error={failed ? failureReason : undefined}>
      {/* 1. HLS 비디오 스트림 (정상 작동 중) */}
      {camera.sourceType === "HLS" && shouldLoad && !failed && (
        <>
          <video
            ref={videoRef}
            muted
            controls
            playsInline
            className={styles.videoElement}
            aria-label={`${camera.name} 라이브 영상`}
          />
          {loading && (
            <div className={styles.loadingOverlay}>
              <span className="material-symbols-outlined spin" aria-hidden="true">progress_activity</span>
              <span>실시간 스트림 연결 중...</span>
            </div>
          )}
        </>
      )}

      {/* 2. YouTube 스트림 */}
      {camera.sourceType === "YOUTUBE" && shouldLoad && !failed && (
        <iframe
          src={`${camera.sourceUrl}${camera.sourceUrl.includes("?") ? "&" : "?"}autoplay=${
            autoPlay || manualPlay ? "1" : "0"
          }&mute=1&playsinline=1`}
          title={`${camera.name} 라이브 영상`}
          className={styles.iframeElement}
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
          allowFullScreen
        />
      )}

      {/* 3. 순수 영상 전용 IFRAME 스트림 (Vivaldi WESP 등) */}
      {isDedicatedVideoIframe && shouldLoad && !failed && (
        <iframe
          src={camera.sourceUrl}
          title={`${camera.name} 라이브 영상`}
          className={styles.iframeElement}
          allow="autoplay; fullscreen"
          allowFullScreen
        />
      )}

      {/* 4. 현지 스트림 송출 대기 중 / 비시즌 점검 (깔끔한 프리뷰 카드) */}
      {showStatus && (
        <div className={styles.offlineCard} role="status">
          <div className={styles.offlineIconBox}>
            <span className="material-symbols-outlined" aria-hidden="true">videocam_off</span>
          </div>
          <div className={styles.offlineText}>
            <h4>{camera.sourceIssue === "upstream-unavailable" ? "원본 영상에 연결할 수 없습니다" : camera.sourceIssue === "provider-restricted" ? "제공처에서 재생을 제한하고 있습니다" : camera.sourceIssue === "not-published" ? "현재 공개된 영상이 없습니다" : failed ? "영상에 연결하지 못했습니다" : "공식 페이지에서 확인해 주세요"}</h4>
            <p>{camera.availabilityNote || (failed ? "영상 주소가 변경되었거나 제공처 연결에 문제가 있을 수 있습니다." : "이 카메라는 리조트 공식 페이지에서 확인할 수 있습니다.")}</p>
            {camera.availabilityCheckedAt && <small>원본 확인: {camera.availabilityCheckedAt}</small>}
          </div>
          <div className={styles.offlineActions}>{failed && <button type="button" className={styles.officialSiteBtn} onClick={() => {
            setFailed(false);
            setRetryAttempt(attempt => attempt + 1);
          }}>다시 연결</button>}
          {officialUrl && (
            <a
              href={officialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={styles.officialSiteBtn}
            >
              <span className="material-symbols-outlined" aria-hidden="true">open_in_new</span>
              리조트 공식 현황 보기
            </a>
          )}
          </div>
        </div>
      )}

      {/* 5. 로드 전 클릭 커버 */}
      {!shouldLoad && !failed && !((camera.sourceType === "EXTERNAL_LINK" && !isDedicatedVideoIframe)) && (
        <button
          type="button"
          className={styles.playCover}
          onClick={() => {
            setIdleStopped(false);
            setManualPlay(true);
          }}
          aria-label={`${camera.name} 라이브 영상 ${idleStopped ? "계속 보기" : "재생"}`}
        >
          <span className="material-symbols-outlined playIcon" aria-hidden="true">play_circle</span>
          <span className={styles.playText}>{idleStopped ? "오래 켜 둔 영상을 멈췄어요. 계속 보기" : "실시간 영상 재생"}</span>
        </button>
      )}
    </div>
  );
}
