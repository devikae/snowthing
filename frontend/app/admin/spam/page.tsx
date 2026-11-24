"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { confirm, type ConfirmOptions } from "../../lib/feedback";
import { ask } from "../adminConfirm";
import {
  type AdminPage,
  type BannedWordItem,
  type FilterHoldItem,
  formatDateTime,
  postHref,
  readErrorMessage,
} from "../adminTypes";
import { AdminPager as Pager } from "../AdminPager";
import { useAdminFetch } from "../useAdminFetch";

/**
 * 금칙어 보류 검토와 금칙어 관리 (IKA-96).
 * 금칙어가 든 글과 댓글은 저장은 되지만 작성자 본인과 관리자 말고는 보이지 않는다. 여기서 공개로 돌리거나 삭제한다.
 * 금칙어를 바꾸면 서버가 바로 새 목록을 쓴다. 배포나 재시작은 필요 없다.
 */
export default function AdminSpamPage() {
  return (
    <>
      <PageHeading
        title="스팸 보류"
        description="금칙어가 걸려 조용히 보류된 글과 댓글을 검토합니다. 작성자에게는 평소처럼 보이고 다른 사람에게는 보이지 않습니다."
      />
      <HoldReview />
      <BannedWords />
    </>
  );
}

function HoldReview() {
  const [page, setPage] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const list = useAdminFetch<AdminPage<FilterHoldItem>>(API_ENDPOINTS.admin.filterHolds(page), "보류 목록을 불러오지 못했습니다.");

  const run = async (item: FilterHoldItem, question: ConfirmOptions, request: () => Promise<Response>, done: string) => {
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

  const release = (item: FilterHoldItem) =>
    run(
      item,
      ask("이 항목을 모두에게 공개할까요?", "공개"),
      () => csrfFetch(API_ENDPOINTS.admin.filterHoldAction(item.id, "release"), { method: "POST", credentials: "include" }),
      "공개로 바꿨습니다.",
    );
  const remove = (item: FilterHoldItem) =>
    run(
      item,
      ask("이 항목을 삭제할까요? 작성자에게도 보이지 않게 됩니다.", "삭제", true),
      () => csrfFetch(API_ENDPOINTS.admin.filterHold(item.id), { method: "DELETE", credentials: "include" }),
      "삭제했습니다.",
    );
  const ban = (item: FilterHoldItem) =>
    run(
      item,
      ask(`${item.writerIp} 대역을 30일 동안 막을까요?\nIPv6은 /64 대역, IPv4는 주소 하나를 막습니다. 글, 댓글, 라이브톡 작성이 막힙니다.`, "차단", true),
      () => csrfFetch(API_ENDPOINTS.admin.filterHoldAction(item.id, "ban-ip"), { method: "POST", credentials: "include" }),
      "IP 대역을 막았습니다. 보류 항목은 따로 삭제해 주세요.",
    );

  return (
    <section className="panel admin-section" aria-label="보류 검토">
      <h2>보류 검토</h2>
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
      {list.data && list.data.items.length === 0 && <p className="admin-meta">보류된 글과 댓글이 없습니다.</p>}
      <div className="admin-list">
        {list.data?.items.map((item) => (
          <div className="admin-row admin-hold-row" key={item.id}>
            <span className="admin-status" data-status="REJECTED">
              {item.targetType === "POST" ? "글" : "댓글"}
            </span>
            <span className="admin-hold-body">
              <p>
                <Link className="admin-link" href={postHref(item.categoryCode, item.postPublicId)} target="_blank">
                  {item.postTitle}
                </Link>
                {item.targetType === "COMMENT" && <small className="admin-meta"> 에 단 댓글</small>}
              </p>
              <span className="admin-body">{item.excerpt || "(내용 없음)"}</span>
              <small>
                걸린 말 <b>{item.matchedWord}</b> | {item.writerNickname ?? "비회원"}
                {item.anonymous ? " (익명)" : ""} | {item.writerIp} | {formatDateTime(item.heldAt)}
              </small>
            </span>
            <span className="admin-actions">
              <button className="admin-button primary" type="button" disabled={busyId === item.id} onClick={() => release(item)}>
                공개 전환
              </button>
              <button className="admin-button danger" type="button" disabled={busyId === item.id} onClick={() => remove(item)}>
                삭제
              </button>
              <button className="admin-button danger" type="button" disabled={busyId === item.id} onClick={() => ban(item)}>
                이 IP 대역 차단
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
  );
}

function BannedWords() {
  const words = useAdminFetch<BannedWordItem[]>(API_ENDPOINTS.admin.bannedWords, "금칙어를 불러오지 못했습니다.");
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);

  const add = async (event: React.FormEvent) => {
    event.preventDefault();
    const word = input.trim();
    if (!word) return;
    setBusy(true);
    words.setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.bannedWords, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ word }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "금칙어를 추가하지 못했습니다."));
      setInput("");
      words.reload();
    } catch (cause) {
      words.setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (item: BannedWordItem) => {
    if (!(await confirm(ask(`"${item.word}"를 금칙어에서 뺄까요?`, "빼기")))) return;
    setBusy(true);
    words.setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.bannedWord(item.id), { method: "DELETE", credentials: "include" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "금칙어를 빼지 못했습니다."));
      words.reload();
    } catch (cause) {
      words.setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel admin-section" aria-label="금칙어">
      <h2>금칙어</h2>
      <p className="admin-notice">
        공백, 점, 하이픈 같은 기호와 대소문자를 무시하고 비교합니다. &quot;카 지 노&quot;, &quot;카.지.노&quot;도 &quot;카지노&quot;로 봅니다.
        그래서 짧은 말은 다른 단어에 걸칠 수 있습니다. 일상 대화에도 쓰는 말(예: 카지노, 먹튀)보다 광고에만 쓰는 말을 넣어 주세요. 관리자 글은
        검사하지 않습니다.
      </p>
      <form className="admin-actions" onSubmit={add}>
        <input
          aria-label="새 금칙어"
          placeholder="예: 카지노사이트"
          maxLength={50}
          value={input}
          onChange={(event) => setInput(event.target.value)}
        />
        <button className="admin-button primary" type="submit" disabled={busy || !input.trim()}>
          추가
        </button>
      </form>
      {words.error && (
        <p className="admin-error" role="alert">
          {words.error}
        </p>
      )}
      {words.loading && !words.data && <p className="admin-meta">불러오는 중...</p>}
      {words.data && words.data.length === 0 && <p className="admin-meta">등록된 금칙어가 없습니다.</p>}
      <div className="admin-word-list">
        {words.data?.map((item) => (
          <span className="admin-word" key={item.id}>
            {item.word}
            <button type="button" aria-label={`${item.word} 빼기`} disabled={busy} onClick={() => remove(item)}>
              ×
            </button>
          </span>
        ))}
      </div>
    </section>
  );
}
