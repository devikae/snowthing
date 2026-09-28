"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useResortCameras } from "./ResortCamProvider";
import { resortSlug } from "./types";
import styles from "./resortCam.module.css";

export function ResortCamIndexRoute() {
  const router = useRouter();
  const resorts = useResortCameras();

  useEffect(() => {
    if (resorts.length > 0) router.replace(`/resort-cam/${resortSlug(resorts[0].code)}`);
  }, [resorts, router]);

  return <div className={styles.empty}>{resorts.length > 0 ? "리조트캠으로 이동 중입니다." : "현재 표시할 리조트캠이 없습니다."}</div>;
}
