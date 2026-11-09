"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { errorText } from "../lib/errorText";
import { formatKoreanDate } from "../lib/koreanDate";
import { readErrorMessage } from "../lib/messageApi";
import styles from "./messages.module.css";

interface ScrappedPost {
  postPublicId: string;
  categoryCode: string;
  categoryName: string;
  title: string;
  writerNickname: string;
  commentCount: number;
  likeCount: number;
  postCreatedAt: string;
  scrappedAt: string;
}

interface ScrapPage {
  content: ScrappedPost[];
  pageInfo: { nextCursor: string | null; hasNext: boolean };
}

/** 설정 화면의 스크랩한 글. 최근 스크랩 순이고 더 보기로 이어서 불러온다. 삭제되거나 가려진 글은 서버가 뺀다. */
export function ScrappedPostsSection() {
  const [posts, setPosts] = useState<ScrappedPost[] | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [loadingMore, setLoadingMore] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async (cursor: string | null) => {
    const response = await fetch(API_ENDPOINTS.members.scraps(cursor), { credentials: "include", cache: "no-store" });
    if (!response.ok) throw new Error(await readErrorMessage(response, "스크랩한 글을 불러오지 못했습니다."));
    const page: ScrapPage = await response.json();
    setPosts((current) => (cursor && current ? [...current, ...page.content] : page.content));
    setNextCursor(page.pageInfo.hasNext ? page.pageInfo.nextCursor : null);
    setError("");
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      load(null).catch((loadError) => setError(errorText(loadError, "스크랩한 글을 불러오지 못했습니다.")));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      await load(nextCursor);
    } catch (loadError) {
      setError(errorText(loadError, "스크랩한 글을 불러오지 못했습니다."));
    } finally {
      setLoadingMore(false);
    }
  };

  const unscrap = async (post: ScrappedPost) => {
    if (busyId) return;
    setBusyId(post.postPublicId);
    setError("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.posts.scrap(post.postPublicId), { method: "DELETE" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "스크랩을 취소하지 못했습니다."));
      setPosts((current) => current?.filter((item) => item.postPublicId !== post.postPublicId) ?? null);
    } catch (unscrapError) {
      setError(errorText(unscrapError, "스크랩을 취소하지 못했습니다."));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="profile-section">
      <div>
        <h2>스크랩한 글</h2>
        <p>글 상세에서 스크랩한 글을 최근 순으로 보여 줍니다.</p>
      </div>
      <div>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {posts === null ? (
          !error && <p className={styles.meta}>스크랩한 글을 불러오는 중입니다.</p>
        ) : posts.length === 0 ? (
          <p className={styles.meta}>스크랩한 글이 없습니다.</p>
        ) : (
          <ul className={styles.blockList}>
            {posts.map((post) => (
              <li key={post.postPublicId}>
                <span className="profile-scrap-item">
                  <Link href={`/posts/${post.postPublicId}`}>{post.title}</Link>
                  <small>
                    {post.categoryName} | {post.writerNickname} | {formatKoreanDate(post.scrappedAt)} 스크랩
                  </small>
                </span>
                <button type="button" className={styles.button} disabled={busyId !== null} onClick={() => void unscrap(post)}>
                  {busyId === post.postPublicId ? "취소 중" : "스크랩 취소"}
                </button>
              </li>
            ))}
          </ul>
        )}
        {nextCursor && (
          <div className="profile-actions profile-scrap-more">
            <button type="button" disabled={loadingMore} onClick={() => void loadMore()}>
              {loadingMore ? "불러오는 중" : "더 보기"}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
