"use client";

import { useEffect } from "react";
import { API_ENDPOINTS } from "./api";
import { csrfFetch } from "./csrfFetch";

export function useImageUploadLease(references: string[]) {
  const serialized = JSON.stringify(references);
  useEffect(() => {
    const keys: string[] = JSON.parse(serialized);
    if (!keys.some(key => key.includes("/m-"))) return;
    const renew = () => {
      if (document.visibilityState !== "visible") return;
      void csrfFetch(`${API_ENDPOINTS.images.upload}/keep-alive`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ imageKeys: keys }),
      }).catch(() => undefined);
    };
    const timer = window.setInterval(renew, 10 * 60 * 1000);
    return () => window.clearInterval(timer);
  }, [serialized]);
}
