"use client";

import { useCallback, useEffect, useState } from "react";
import { readErrorMessage } from "./adminTypes";

/** 관리자 화면 공통 조회. url이 바뀌거나 reload를 부르면 다시 읽고, 늦게 온 이전 응답은 버린다. */
export function useAdminFetch<T>(url: string | null, failure: string) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!url) return;
    let cancelled = false;
    fetch(url, { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await readErrorMessage(response, failure));
        return (await response.json()) as T;
      })
      .then((body) => {
        if (cancelled) return;
        setData(body);
        setError(null);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [url, failure, reloadKey]);

  const reload = useCallback(() => {
    setLoading(true);
    setReloadKey((current) => current + 1);
  }, []);

  return { data, error, loading, setLoading, setError, reload };
}
