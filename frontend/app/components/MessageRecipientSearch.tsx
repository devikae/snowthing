"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { composeMessageHref, MESSAGE_ENDPOINTS, readErrorMessage } from "../lib/messageApi";
import { CommentProfileAvatar } from "./CommentProfileAvatar";
import styles from "./messages.module.css";
import { errorText } from "../lib/errorText";

const MIN_LENGTH = 2;
const DEBOUNCE_MS = 300;

interface MemberSearchResult {
  publicId: string;
  nickname: string;
  profileImageUrl: string | null;
}

interface SearchState {
  keyword: string;
  results: MemberSearchResult[];
  error: string;
}

/** 쪽지 받는 회원을 닉네임 앞글자로 찾는다. 고르면 주소를 바꿔 기존 작성 화면으로 넘어간다. */
export function MessageRecipientSearch({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [input, setInput] = useState("");
  const [search, setSearch] = useState<SearchState>({ keyword: "", results: [], error: "" });
  const keyword = input.trim();
  const searchable = keyword.length >= MIN_LENGTH;
  const settled = search.keyword === keyword;

  useEffect(() => {
    if (!searchable) return;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(MESSAGE_ENDPOINTS.memberSearch(keyword), {
          credentials: "include",
          cache: "no-store",
          signal: controller.signal,
        });
        if (response.status === 401) {
          onUnauthorized();
          return;
        }
        if (!response.ok) throw new Error(await readErrorMessage(response, "회원을 찾지 못했습니다."));
        const results: MemberSearchResult[] = await response.json();
        setSearch({ keyword, results, error: "" });
      } catch (searchError) {
        if (searchError instanceof DOMException && searchError.name === "AbortError") return;
        setSearch({
          keyword,
          results: [],
          error: errorText(searchError, "회원을 찾지 못했습니다."),
        });
      }
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, searchable, onUnauthorized]);

  return (
    <div className={styles.card}>
      <div className={styles.compose}>
        <label>
          받는 회원
          <input
            className={styles.recipientInput}
            type="search"
            value={input}
            maxLength={50}
            onChange={(event) => setInput(event.target.value)}
            placeholder="닉네임 앞글자를 2글자 이상 입력하세요."
            autoFocus
          />
        </label>
        {searchable && settled && search.error && <p className={styles.error} role="alert">{search.error}</p>}
        {searchable && settled && !search.error && search.results.length === 0 && (
          <p className={styles.meta} role="status">닉네임이 이렇게 시작하는 회원이 없습니다.</p>
        )}
        {searchable && settled && search.results.length > 0 && (
          <ul className={styles.recipientList}>
            {search.results.map((member) => (
              <li key={member.publicId}>
                <Link href={composeMessageHref(member)}>
                  <CommentProfileAvatar
                    nickname={member.nickname}
                    profileImageUrl={member.profileImageUrl}
                    size={28}
                    className={styles.recipientAvatar}
                  />
                  <span>{member.nickname}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
