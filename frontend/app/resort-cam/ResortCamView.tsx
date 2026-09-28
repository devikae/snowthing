"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { CameraPlayer } from "./CameraPlayer";
import styles from "./resortCam.module.css";
import type { ResortCameraGroup } from "./types";
import { cameraSlug, resortSlug } from "./types";

interface ResortCamViewProps {
  resorts: ResortCameraGroup[];
  selectedResortCode: string;
  selectedCameraCode?: string;
  invalidMessage?: string;
}

export function ResortCamView({
  resorts,
  selectedResortCode,
  selectedCameraCode,
  invalidMessage,
}: ResortCamViewProps) {
  const selectedResort = resorts.find((resort) => resort.code === selectedResortCode);
  const selectedCamera = selectedResort?.cameras.find((camera) => camera.code === selectedCameraCode);
  const cameras = selectedCamera ? [selectedCamera] : (selectedResort?.cameras ?? []);
  const gridRef = useRef<HTMLDivElement>(null);
  const [visibleCodes, setVisibleCodes] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (selectedCamera || !gridRef.current) return;
    const observer = new IntersectionObserver(
      (entries) => {
        setVisibleCodes((current) => {
          const next = new Set(current);
          entries.forEach((entry) => {
            const code = (entry.target as HTMLElement).dataset.cameraCode;
            if (!code) return;
            if (entry.isIntersecting) next.add(code);
            else next.delete(code);
          });
          return next;
        });
      },
      { threshold: 0.25 },
    );
    gridRef.current.querySelectorAll<HTMLElement>("[data-camera-code]").forEach((card) => observer.observe(card));
    return () => observer.disconnect();
  }, [selectedCamera, selectedResortCode]);

  const autoPlayCodes = new Set(
    cameras
      .filter((camera) => visibleCodes.has(`${selectedResortCode}:${camera.code}`))
      .map((camera) => camera.code),
  );

  if (invalidMessage) return <EmptyState message={invalidMessage} />;
  if (!selectedResort) return <EmptyState message="현재 표시할 슬로프캠이 없습니다." />;

  const playableCount = selectedResort.cameras.filter((camera) => camera.sourceType !== "EXTERNAL_LINK").length;

  return (
    <main className={styles.page}>
      {/* 상단 탭 네비게이션: 리조트 현황 vs 슬로프캠 */}
      <div className={styles.topTabs}>
        <div className={styles.topTabsLeft}>
          <Link href="/resort" className={styles.tabInactive}>
            <span className="material-symbols-outlined">info</span>
            실시간 현황
          </Link>
          <Link href="/resort-cam" className={styles.tabActive}>
            <span className="material-symbols-outlined">videocam</span>
            슬로프캠 LIVE
          </Link>
        </div>
        <span className={styles.autoRefreshNote}>외부 스트리밍 직접 연결</span>
      </div>

      <section className={styles.intro}>
        <div className={styles.introIcon} aria-hidden="true">
          <span className="material-symbols-outlined">videocam</span>
        </div>
        <div>
          <p className={styles.eyebrow}>RESORT LIVE CAM</p>
          <h1>전국 스키장 실시간 슬로프캠</h1>
          <p className={styles.introCopy}>주요 리조트의 슬로프 실시간 상황과 설질을 한 화면에서 확인하세요.</p>
        </div>
      </section>

      {/* 리조트 선택 탭 */}
      <nav className={styles.resortNav} aria-label="리조트 선택">
        <div className={styles.resortNavTrack}>
          {resorts.map((resort) => {
            const active = resort.code === selectedResort.code;
            return (
              <Link
                key={resort.code}
                href={`/resort-cam/${resortSlug(resort.code)}`}
                className={`${styles.resortTab} ${active ? styles.resortActive : ""}`}
                aria-current={active ? "page" : undefined}
              >
                <span className={styles.tabName}>{resort.name}</span>
                <small className={styles.countBadge}>{resort.cameras.length}</small>
              </Link>
            );
          })}
        </div>
      </nav>

      {/* 리조트 상세 섹션 */}
      <section className={styles.resortSection}>
        <header className={styles.resortHeader}>
          <div>
            <div className={styles.resortMeta}>
              <span className={styles.metaRegion}>{selectedResort.regionName}</span>
              <span className={styles.metaCount}>카메라 {selectedResort.cameras.length}개</span>
              {playableCount > 0 ? (
                <span className={styles.liveMeta}>실시간 스트림 {playableCount}개</span>
              ) : (
                <span className={styles.officialMeta}>공식 스트리밍 센터</span>
              )}
            </div>
            <h2>{selectedCamera ? selectedCamera.name : selectedResort.name}</h2>
            <p className={styles.headerDescription}>
              {selectedCamera
                ? `${selectedResort.name} · ${selectedCamera.name} 화면입니다.`
                : `${selectedResort.name}에서 송출하는 실시간 슬로프 영상입니다.`}
            </p>
          </div>
          {selectedCamera && (
            <Link className={styles.backLink} href={`/resort-cam/${resortSlug(selectedResort.code)}`}>
              <span className="material-symbols-outlined" aria-hidden="true">grid_view</span>
              전체 보기
            </Link>
          )}
        </header>

        {cameras.length === 0 ? (
          <EmptyState message="등록된 슬로프캠이 없습니다." />
        ) : (
          <div ref={gridRef} className={selectedCamera ? styles.singleGrid : styles.cameraGrid}>
            {cameras.map((camera) => (
              <article
                key={camera.code}
                data-camera-code={`${selectedResort.code}:${camera.code}`}
                className={styles.cameraCard}
              >
                <div className={styles.cardHeader}>
                  <div className={styles.cardHeaderLeft}>
                    <h3>{camera.name}</h3>
                  </div>
                  {!selectedCamera && (
                    <Link
                      href={`/resort-cam/${resortSlug(selectedResort.code)}/${cameraSlug(camera.code)}`}
                      className={styles.expandButton}
                      aria-label={`${camera.name} 크게 보기`}
                      title="크게 보기"
                    >
                      <span className="material-symbols-outlined" aria-hidden="true">open_in_full</span>
                    </Link>
                  )}
                </div>
                <CameraPlayer
                  camera={camera}
                  autoPlay={selectedCamera ? true : autoPlayCodes.has(camera.code)}
                  focused={Boolean(selectedCamera)}
                />
              </article>
            ))}
          </div>
        )}
      </section>

      <p className={styles.sourceNotice}>
        ※ 슬로프캠 영상은 각 리조트의 공식 공개 스트림에 직접 연결됩니다. 현지 기상 상황 및 리조트 사정에 따라 스트리밍이 일시 중단될 수 있습니다.
      </p>
    </main>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className={styles.empty}>
      <span className="material-symbols-outlined" aria-hidden="true">videocam_off</span>
      <p>{message}</p>
    </div>
  );
}
