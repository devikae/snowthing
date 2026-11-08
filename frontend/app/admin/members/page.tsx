"use client";

import { useEffect, useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import {
  type AdminMember,
  type AdminMemberListResponse,
  MEMBER_STATUS_BADGE,
  MEMBER_STATUS_LABEL,
  formatDateTime,
  readErrorMessage,
} from "../adminTypes";

export default function AdminMembersPage() {
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminMemberListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.admin.members(query, page), { credentials: "include" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await readErrorMessage(response, "회원 목록을 불러오지 못했습니다."));
        return (await response.json()) as AdminMemberListResponse;
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
  }, [query, page]);

  const search = (event: React.FormEvent) => {
    event.preventDefault();
    const next = input.trim();
    if (next === query && page === 0) return;
    setLoading(true);
    setPage(0);
    setQuery(next);
  };

  const changeStatus = async (member: AdminMember, action: "suspend" | "reinstate") => {
    const message =
      action === "suspend"
        ? `${member.nickname} 회원을 정지할까요? 접속이 바로 끊기고 해제할 때까지 로그인할 수 없습니다.`
        : `${member.nickname} 회원의 정지를 해제할까요?`;
    if (!window.confirm(message)) return;
    setBusyId(member.publicId);
    setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.memberStatusAction(member.publicId, action), { method: "POST" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "회원 상태를 바꾸지 못했습니다."));
      const updated = (await response.json()) as AdminMember;
      setData((current) =>
        current && {
          ...current,
          items: current.items.map((item) => (item.publicId === updated.publicId ? updated : item)),
        },
      );
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeading title="회원 관리" description="닉네임이나 이메일로 회원을 찾아 정지하거나 해제합니다. 정지는 해제할 때까지 유지됩니다." />
      <section className="panel admin-section" aria-label="회원 목록">
        <form className="admin-actions" role="search" onSubmit={search}>
          <input
            type="search"
            value={input}
            maxLength={100}
            aria-label="닉네임 또는 이메일 검색"
            placeholder="닉네임 또는 이메일"
            onChange={(event) => setInput(event.target.value)}
          />
          <button className="admin-button" type="submit">
            검색
          </button>
        </form>
        {error && <p className="admin-error" role="alert">{error}</p>}
        <div className="admin-list">
          {loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!loading && data?.items.length === 0 && <p className="admin-meta">찾는 회원이 없습니다.</p>}
          {data?.items.map((member) => (
            <div className="admin-row" key={member.publicId}>
              <span className="admin-status" data-status={MEMBER_STATUS_BADGE[member.status]}>
                {MEMBER_STATUS_LABEL[member.status]}
              </span>
              <span style={{ minWidth: 0 }}>
                <p>
                  {member.nickname}
                  {member.role === "ROLE_ADMIN" ? " (관리자)" : ""}
                </p>
                <small style={{ overflowWrap: "anywhere" }}>
                  {member.email}, 가입 {formatDateTime(member.createdAt)}
                </small>
              </span>
              {member.status === "SUSPENDED" ? (
                <button
                  className="admin-button"
                  type="button"
                  disabled={busyId === member.publicId}
                  onClick={() => void changeStatus(member, "reinstate")}
                >
                  해제
                </button>
              ) : (
                <button
                  className="admin-button danger"
                  type="button"
                  disabled={busyId === member.publicId || member.status === "WITHDRAWN" || member.role === "ROLE_ADMIN"}
                  onClick={() => void changeStatus(member, "suspend")}
                >
                  정지
                </button>
              )}
            </div>
          ))}
        </div>
        <div className="admin-actions" style={{ justifyContent: "center" }}>
          <button
            className="admin-button"
            type="button"
            disabled={page === 0 || loading}
            onClick={() => {
              setLoading(true);
              setPage((current) => current - 1);
            }}
          >
            이전
          </button>
          <span className="admin-meta">{page + 1}쪽</span>
          <button
            className="admin-button"
            type="button"
            disabled={!data?.hasNext || loading}
            onClick={() => {
              setLoading(true);
              setPage((current) => current + 1);
            }}
          >
            다음
          </button>
        </div>
      </section>
    </>
  );
}
