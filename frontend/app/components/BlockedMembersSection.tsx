"use client";

import { useCallback, useEffect, useState } from "react";
import { BlockedMember, MESSAGE_ENDPOINTS, formatMessageDate, readErrorMessage, unblockMember } from "../lib/messageApi";
import styles from "./messages.module.css";

/** 설정 화면의 차단 목록. 차단한 회원과는 서로 쪽지를 보낼 수 없다. */
export function BlockedMembersSection() {
  const [blocks, setBlocks] = useState<BlockedMember[] | null>(null);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const response = await fetch(MESSAGE_ENDPOINTS.blocks, { credentials: "include", cache: "no-store" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "차단 목록을 불러오지 못했습니다."));
      setBlocks(await response.json());
      setError("");
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "차단 목록을 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const unblock = async (member: BlockedMember) => {
    if (busyId) return;
    if (!window.confirm(`${member.nickname}님의 차단을 해제할까요?`)) return;
    setBusyId(member.publicId);
    setError("");
    try {
      await unblockMember(member.publicId);
      setBlocks((current) => current?.filter((item) => item.publicId !== member.publicId) ?? null);
    } catch (unblockError) {
      setError(unblockError instanceof Error ? unblockError.message : "차단을 해제하지 못했습니다.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="profile-section">
      <div>
        <h2>차단한 회원</h2>
        <p>차단한 회원과는 서로 쪽지를 보낼 수 없습니다.</p>
      </div>
      <div>
        {error && <p className={styles.error} role="alert">{error}</p>}
        {blocks === null ? (
          !error && <p className={styles.meta}>차단 목록을 불러오는 중입니다.</p>
        ) : blocks.length === 0 ? (
          <p className={styles.meta}>차단한 회원이 없습니다.</p>
        ) : (
          <ul className={styles.blockList}>
            {blocks.map((member) => (
              <li key={member.publicId}>
                <span>
                  {member.nickname}
                  <small>{formatMessageDate(member.blockedAt)} 차단</small>
                </span>
                <button type="button" className={styles.button} disabled={busyId !== null} onClick={() => void unblock(member)}>
                  {busyId === member.publicId ? "해제 중" : "차단 해제"}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
