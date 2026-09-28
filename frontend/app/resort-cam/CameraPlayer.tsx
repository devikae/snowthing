"use client";

import { useEffect, useRef, useState } from "react";
import type { ResortCameraItem } from "./types";
import styles from "./resortCam.module.css";

interface CameraPlayerProps {
  camera: ResortCameraItem;
  autoPlay: boolean;
  focused?: boolean;
}

export function CameraPlayer({ camera, autoPlay, focused = false }: CameraPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [desktop, setDesktop] = useState(false);
  const [manualPlay, setManualPlay] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const media = window.matchMedia("(min-width: 768px)");
    const sync = () => setDesktop(media.matches);
    sync();
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, []);

  const shouldLoad = focused || manualPlay || autoPlay;
  const officialUrl = camera.externalPageUrl || camera.sourceUrl || "";

  useEffect(() => {
    const video = videoRef.current;
    if (camera.sourceType !== "HLS" || !video || !shouldLoad) return;

    let disposed = false;
    let destroy: (() => void) | undefined;
    setFailed(false);
    setLoading(true);

    void (async () => {
      try {
        if (video.canPlayType("application/vnd.apple.mpegurl")) {
          video.src = camera.sourceUrl;
          video.addEventListener("loadedmetadata", () => setLoading(false), { once: true });
          video.addEventListener("error", () => {
            setFailed(true);
            setLoading(false);
          }, { once: true });
        } else {
          const { default: Hls } = await import("hls.js");
          if (disposed) return;
          if (!Hls.isSupported()) {
            setFailed(true);
            setLoading(false);
            return;
          }
          const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
          hls.loadSource(camera.sourceUrl);
          hls.attachMedia(video);
          hls.on(Hls.Events.MANIFEST_PARSED, () => {
            setLoading(false);
            void video.play().catch(() => undefined);
          });
          hls.on(Hls.Events.ERROR, (_, data) => {
            if (data.fatal) {
              setFailed(true);
              setLoading(false);
            }
          });
          destroy = () => hls.destroy();
        }
        void video.play().catch(() => undefined);
      } catch {
        setFailed(true);
        setLoading(false);
      }
    })();

    return () => {
      disposed = true;
      video.pause();
      video.removeAttribute("src");
      video.load();
      destroy?.();
    };
  }, [camera.sourceType, camera.sourceUrl, manualPlay, shouldLoad]);

  // 순수 영상 iframe 전용 (vivaldi.html, rtsp.me 등)
  const isDedicatedVideoIframe =
    camera.sourceType === "IFRAME" ||
    (camera.sourceType === "EXTERNAL_LINK" && camera.sourceUrl.includes("pop_webcam.do"));

  return (
    <div className={styles.playerContainer}>
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
              <span className="material-symbols-outlined spin">progress_activity</span>
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
      {((camera.sourceType === "EXTERNAL_LINK" && !isDedicatedVideoIframe) || failed) && (
        <div className={styles.offlineCard}>
          <div className={styles.offlineIconBox}>
            <span className="material-symbols-outlined">ac_unit</span>
          </div>
          <div className={styles.offlineText}>
            <h4>현지 스트리밍 준비 중</h4>
            <p>리조트 현지 기상 및 비시즌 점검으로 인해 시즌 개장 시 실시간 자동 송출됩니다.</p>
          </div>
          {officialUrl && (
            <a
              href={officialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={styles.officialSiteBtn}
            >
              <span className="material-symbols-outlined">open_in_new</span>
              리조트 공식 현황 보기
            </a>
          )}
        </div>
      )}

      {/* 5. 로드 전 클릭 커버 */}
      {!shouldLoad && !failed && !((camera.sourceType === "EXTERNAL_LINK" && !isDedicatedVideoIframe)) && (
        <button
          type="button"
          className={styles.playCover}
          onClick={() => setManualPlay(true)}
          aria-label={`${camera.name} 라이브 영상 재생`}
        >
          <span className="material-symbols-outlined playIcon">play_circle</span>
          <span className={styles.playText}>실시간 영상 재생</span>
        </button>
      )}
    </div>
  );
}
