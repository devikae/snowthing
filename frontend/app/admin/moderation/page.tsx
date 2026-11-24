"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { confirm, type ConfirmOptions } from "../../lib/feedback";
import { ask } from "../adminConfirm";
import { type AdminPage, type ModerationItem, formatDateTime, postHref, readErrorMessage } from "../adminTypes";
import { AdminPager as Pager } from "../AdminPager";
import { useAdminFetch } from "../useAdminFetch";

const TARGET_LABEL: Record<ModerationItem["targetType"], string> = {
  POST: "글",
  COMMENT: "댓글",
  MARKET: "장터",
  CARPOOL: "카풀",
  RESORT_REPORT: "설질 제보",
};

const STATE_LABEL: Record<ModerationItem["targetState"], string> = {
  VISIBLE: "보이는 중",
  HIDDEN: "가려짐",
  DELETED: "삭제됨",
};

/**
 * 자동 검열 검토 대기 (IKA-80).
 * 서버가 1분마다 새 글과 댓글을 검사해 확실한 스팸은 숨기고, 애매한 글은 여기에 올린다. 숨김까지만 하고 지우거나 정지하지 않는다.
 * 문제없음을 누르면 숨김을 풀고 같은 내용은 다시 숨기지 않는다.
 */
export default function AdminModerationPage() {
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const list = useAdminFetch<AdminPage<ModerationItem>>(API_ENDPOINTS.admin.moderations(page), "검토 목록을 불러오지 못했습니다.");

  const run = async (item: ModerationItem, question: ConfirmOptions, request: () => Promise<Response>, done: string) => {
    if (!(await confirm(question))) return;
    setBusyId(item.id);
    setNotice(null);
    list.setError(null);
    try {
      const response = await request();
      if (!response.ok) throw new Error(await readErrorMessage(response, "처리하지 못했습니다."));
      setNotice(done);
      list.reload();
    } catch (cause) {
      list.setError((cause as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const pass = (item: ModerationItem) =>
    run(
      item,
      item.verdict === "HIDE" ? ask("숨김을 풀까요? 같은 내용은 다시 숨기지 않습니다.", "숨김 풀기") : ask("문제없음으로 처리할까요?", "문제없음"),
      () => csrfFetch(API_ENDPOINTS.admin.moderationAction(item.id, "pass"), { method: "POST", credentials: "include" }),
      "문제없음으로 처리했습니다.",
    );
  const keep = (item: ModerationItem) =>
    run(
      item,
      item.verdict === "HIDE" ? ask("숨김을 그대로 둘까요?", "숨김 유지") : ask("이 항목을 숨길까요?", "숨기기", true),
      () => csrfFetch(API_ENDPOINTS.admin.moderationAction(item.id, "keep"), { method: "POST", credentials: "include" }),
      item.verdict === "HIDE" ? "숨김을 유지합니다." : "숨겼습니다.",
    );
  const remove = (item: ModerationItem) =>
    run(
      item,
      ask("이 항목을 삭제할까요?", "삭제", true),
      () => csrfFetch(API_ENDPOINTS.admin.moderation(item.id), { method: "DELETE", credentials: "include" }),
      "삭제했습니다.",
    );

  return (
    <>
      <PageHeading
        title="자동 검열"
        description="자동 검사로 숨긴 글과 검토가 필요한 글입니다. 숨긴 항목은 관리자만 내용을 볼 수 있습니다."
      />
      <section className="panel admin-section" aria-label="검토 대기">
        <h2>검토 대기</h2>
        {list.error && (
          <p className="admin-error" role="alert">
            {list.error}
          </p>
        )}
        {notice && (
          <p className="admin-meta" role="status">
            {notice}
          </p>
        )}
        {list.loading && !list.data && <p className="admin-meta">불러오는 중...</p>}
        {list.data && list.data.items.length === 0 && <p className="admin-meta">검토할 항목이 없습니다.</p>}
        <div className="admin-list">
          {list.data?.items.map((item) => (
            <div className="admin-row admin-hold-row" key={item.id}>
              <span className="admin-status" data-status={item.verdict === "HIDE" ? "REJECTED" : "DRAFT"}>
                {item.verdict === "HIDE" ? "숨김" : "검토"}
              </span>
              <span className="admin-hold-body">
                <p>
                  <small className="admin-meta">{TARGET_LABEL[item.targetType]} </small>
                  <TargetLink item={item} />
                  {item.targetType === "COMMENT" && <small className="admin-meta"> 에 단 댓글</small>}
                </p>
                <span className="admin-body">{item.excerpt || "(내용 없음)"}</span>
                <small>
                  이유 <b>{item.reasons.length > 0 ? item.reasons.join(", ") : "-"}</b> | {STATE_LABEL[item.targetState]} |{" "}
                  {item.writerNickname ?? "비회원"} | {formatDateTime(item.processedAt ?? item.createdAt)}
                </small>
              </span>
              <span className="admin-actions">
                <button className="admin-button primary" type="button" disabled={busyId === item.id} onClick={() => pass(item)}>
                  문제없음
                </button>
                <button className="admin-button" type="button" disabled={busyId === item.id} onClick={() => keep(item)}>
                  {item.verdict === "HIDE" ? "숨김 유지" : "숨기기"}
                </button>
                <button className="admin-button danger" type="button" disabled={busyId === item.id} onClick={() => remove(item)}>
                  삭제
                </button>
              </span>
            </div>
          ))}
        </div>
        {list.data && (
          <Pager
            page={page}
            hasNext={list.data.hasNext}
            loading={list.loading}
            onChange={(next) => {
              list.setLoading(true);
              setPage(next);
            }}
          />
        )}
      </section>
    </>
  );
}

function TargetLink({ item }: { item: ModerationItem }) {
  if (item.targetType === "RESORT_REPORT") {
    return (
      <Link className="admin-link" href="/admin/resort-reports">
        설질 제보 관리에서 보기
      </Link>
    );
  }
  if (!item.postPublicId || !item.categoryCode) {
    return <span>(원문 없음)</span>;
  }
  return (
    <Link className="admin-link" href={postHref(item.categoryCode, item.postPublicId)} target="_blank">
      {item.title || "(제목 없음)"}
    </Link>
  );
}
