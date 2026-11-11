"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { AdminPager } from "../AdminPager";
import { ACTION_LABEL, type AdminActionLogItem, type AdminPage, TARGET_LABEL, formatDateTime } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

function targetCell(log: AdminActionLogItem) {
  const label = TARGET_LABEL[log.targetType] ?? log.targetType;
  if (log.targetType === "MEMBER") {
    return (
      <Link href={`/admin/members/${log.targetId}`}>
        {label} {log.targetId.slice(0, 8)}
      </Link>
    );
  }
  if (log.targetType === "POST") {
    return (
      <Link href={log.action === "MARKET_MODERATION" ? `/market/${log.targetId}` : `/posts/${log.targetId}`}>
        {label} {log.targetId.slice(0, 8)}
      </Link>
    );
  }
  return `${label} ${log.targetId}`;
}

/** 관리자 조치 기록 (IKA-78). 이 기능을 배포한 뒤의 조치부터 남는다. 글 본문은 기록하지 않는다. */
export default function AdminActionLogsPage() {
  const [action, setAction] = useState("");
  const [targetType, setTargetType] = useState("");
  const [targetIdInput, setTargetIdInput] = useState("");
  const [targetId, setTargetId] = useState("");
  const [page, setPage] = useState(0);
  const list = useAdminFetch<AdminPage<AdminActionLogItem>>(
    API_ENDPOINTS.admin.actionLogs({ action, targetType, targetId, page }),
    "조치 기록을 불러오지 못했습니다.",
  );
  const data = list.data;

  const restart = () => {
    list.setLoading(true);
    setPage(0);
  };

  return (
    <>
      <PageHeading title="조치 기록" description="관리자가 한 정지, 권한 변경, 블라인드, 삭제, 상태 변경, 배너와 차단 변경을 시간 역순으로 봅니다." />
      <section className="panel admin-section" aria-label="조치 기록 목록">
        <form
          className="admin-actions"
          role="search"
          onSubmit={(event) => {
            event.preventDefault();
            const next = targetIdInput.trim();
            if (next === targetId) return;
            restart();
            setTargetId(next);
          }}
        >
          <select
            aria-label="조치 종류"
            value={action}
            onChange={(event) => {
              restart();
              setAction(event.target.value);
            }}
          >
            <option value="">모든 조치</option>
            {Object.entries(ACTION_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <select
            aria-label="대상 종류"
            value={targetType}
            onChange={(event) => {
              restart();
              setTargetType(event.target.value);
            }}
          >
            <option value="">모든 대상</option>
            {Object.entries(TARGET_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <input
            type="search"
            value={targetIdInput}
            maxLength={64}
            aria-label="대상 식별자"
            placeholder="대상 공개 ID나 번호"
            onChange={(event) => setTargetIdInput(event.target.value)}
          />
          <button className="admin-button" type="submit">
            찾기
          </button>
        </form>
        {list.error && (
          <p className="admin-error" role="alert">
            {list.error}
          </p>
        )}
        <div className="admin-list">
          {list.loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!list.loading && data?.items.length === 0 && <p className="admin-meta">기록이 없습니다.</p>}
          {data?.items.map((log) => (
            <div className="admin-row" key={log.id}>
              <span className="admin-meta">{formatDateTime(log.createdAt)}</span>
              <span style={{ minWidth: 0 }}>
                <p>{ACTION_LABEL[log.action] ?? log.action}</p>
                <small style={{ overflowWrap: "anywhere" }}>
                  {targetCell(log)}
                  {log.reason ? `, ${log.reason}` : ""}
                  {", 처리 "}
                  {log.actorPublicId ? <Link href={`/admin/members/${log.actorPublicId}`}>{log.actorNickname}</Link> : "기록 없음"}
                </small>
              </span>
            </div>
          ))}
        </div>
        <AdminPager
          page={page}
          hasNext={!!data?.hasNext}
          loading={list.loading}
          onChange={(next) => {
            list.setLoading(true);
            setPage(next);
          }}
        />
      </section>
    </>
  );
}
