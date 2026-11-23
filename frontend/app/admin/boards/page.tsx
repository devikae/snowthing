"use client";

import { FormEvent, useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import type { AnonymousMode, WritePermission } from "../../lib/boards";
import { csrfFetch } from "../../lib/csrfFetch";
import { readErrorMessage } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

/**
 * 게시판 관리 (IKA-114). 설계: docs/conception/037_board_admin/01_design.md
 * 순서는 상단 게시판 메뉴와 홈 탭이 같이 쓴다. 바꾼 내용은 사이트에 1분 안에 보인다(프론트 60초 캐시).
 * 닫기는 메뉴, 홈 탭, 글쓰기에서 빼고 새 글을 막는다. 삭제는 소프트 삭제라 아래 칸에서 복구한다. 기존 글은 둘 다 계속 보인다.
 */

interface AdminBoard {
  code: string;
  name: string;
  shortName: string;
  description: string | null;
  sortOrder: number;
  status: "OPEN" | "HIDDEN";
  showOnHome: boolean;
  writePermission: WritePermission;
  anonymousMode: AnonymousMode;
  deletedAt: string | null;
}

interface BoardForm {
  code: string;
  name: string;
  shortName: string;
  description: string;
  showOnHome: boolean;
  writePermission: WritePermission;
  anonymousMode: AnonymousMode;
}

const WRITE_LABEL: Record<WritePermission, string> = { MEMBER: "회원", ADMIN: "관리자", ALL: "누구나" };
const ANONYMOUS_LABEL: Record<AnonymousMode, string> = { OPTIONAL: "선택", FORBIDDEN: "금지", FORCED: "항상" };
const EMPTY_FORM: BoardForm = { code: "", name: "", shortName: "", description: "", showOnHome: true, writePermission: "MEMBER", anonymousMode: "OPTIONAL" };
const CHANGED_NOTICE = "반영했습니다. 사이트에는 1분 안에 보입니다.";

function toForm(board: AdminBoard): BoardForm {
  return {
    code: board.code,
    name: board.name,
    shortName: board.shortName,
    description: board.description ?? "",
    showOnHome: board.showOnHome,
    writePermission: board.writePermission,
    anonymousMode: board.anonymousMode,
  };
}

// 비회원 글은 익명 비밀번호로 관리하므로 "누구나"는 "항상 익명"과만 고를 수 있다. 서버도 같은 규칙으로 막는다.
function withRule(form: BoardForm, patch: Partial<BoardForm>): BoardForm {
  const next = { ...form, ...patch };
  if (next.writePermission === "ALL") next.anonymousMode = "FORCED";
  return next;
}

function RuleFields({ form, onChange, idPrefix }: { form: BoardForm; onChange: (next: BoardForm) => void; idPrefix: string }) {
  return (
    <>
      <label htmlFor={`${idPrefix}-write`}>
        누가 쓰나
        <select id={`${idPrefix}-write`} value={form.writePermission} onChange={(event) => onChange(withRule(form, { writePermission: event.target.value as WritePermission }))}>
          <option value="MEMBER">회원</option>
          <option value="ADMIN">관리자</option>
          <option value="ALL">누구나 (비회원 포함)</option>
        </select>
      </label>
      <label htmlFor={`${idPrefix}-anon`}>
        익명
        <select
          id={`${idPrefix}-anon`}
          value={form.anonymousMode}
          disabled={form.writePermission === "ALL"}
          onChange={(event) => onChange(withRule(form, { anonymousMode: event.target.value as AnonymousMode }))}
        >
          <option value="OPTIONAL">선택</option>
          <option value="FORBIDDEN">금지</option>
          <option value="FORCED">항상</option>
        </select>
      </label>
      <label className="admin-board-check">
        <input type="checkbox" checked={form.showOnHome} onChange={(event) => onChange({ ...form, showOnHome: event.target.checked })} />
        홈 탭에 보이기
      </label>
    </>
  );
}

export default function AdminBoardsPage() {
  const loaded = useAdminFetch<AdminBoard[]>(API_ENDPOINTS.admin.boards, "게시판을 불러오지 못했습니다.");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<BoardForm | null>(null);
  const [draft, setDraft] = useState<BoardForm>(EMPTY_FORM);

  const boards = loaded.data ?? [];
  const active = boards.filter((board) => !board.deletedAt);
  const deleted = boards.filter((board) => board.deletedAt);

  async function run(url: string, init: RequestInit, failure: string, after?: () => void) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await csrfFetch(url, { credentials: "include", ...init });
      if (!response.ok) throw new Error(await readErrorMessage(response, failure));
      after?.();
      setNotice(CHANGED_NOTICE);
      loaded.reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : failure);
    } finally {
      setBusy(false);
    }
  }

  const body = (form: BoardForm) => ({
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...form, description: form.description.trim() || null }),
  });

  function create(event: FormEvent) {
    event.preventDefault();
    void run(API_ENDPOINTS.admin.boards, body({ ...draft, code: draft.code.trim().toUpperCase() }), "게시판을 추가하지 못했습니다.", () => setDraft(EMPTY_FORM));
  }

  function saveEdit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    void run(API_ENDPOINTS.admin.board(editing.code), { ...body(editing), method: "PATCH" }, "게시판을 수정하지 못했습니다.", () => setEditing(null));
  }

  function toggleStatus(board: AdminBoard) {
    const next = board.status === "OPEN" ? "HIDDEN" : "OPEN";
    if (next === "HIDDEN" && !window.confirm(`${board.name}을(를) 닫을까요? 메뉴와 글쓰기에서 빠지고 새 글을 쓸 수 없습니다. 기존 글은 그대로 보입니다.`)) return;
    void run(API_ENDPOINTS.admin.boardStatus(board.code, next), { method: "PATCH" }, "상태를 바꾸지 못했습니다.");
  }

  function remove(board: AdminBoard) {
    if (!window.confirm(`${board.name}을(를) 삭제할까요? 아래 "삭제한 게시판"에서 복구할 수 있습니다. 기존 글은 그대로 보입니다.`)) return;
    void run(API_ENDPOINTS.admin.board(board.code), { method: "DELETE" }, "게시판을 삭제하지 못했습니다.");
  }

  return (
    <>
      <PageHeading
        title="게시판"
        description="순서는 상단 게시판 메뉴와 홈 탭에 같이 쓰입니다. 바꾼 내용은 1분 안에 사이트에 반영됩니다."
      />

      <section className="panel admin-section" aria-label="게시판 목록">
        <h2>게시판 목록</h2>
        {loaded.error && <p className="admin-error" role="alert">{loaded.error}</p>}
        {error && <p className="admin-error" role="alert">{error}</p>}
        {notice && <p className="admin-meta" role="status">{notice}</p>}
        {loaded.loading && !loaded.data && <p className="admin-meta">불러오는 중...</p>}
        {loaded.data && (
          <div className="admin-board-scroll">
            <table className="admin-board-table">
              <thead>
                <tr>
                  <th scope="col">순서</th>
                  <th scope="col">이름</th>
                  <th scope="col">홈 탭 이름</th>
                  <th scope="col">코드</th>
                  <th scope="col">누가 쓰나</th>
                  <th scope="col">익명</th>
                  <th scope="col">홈 탭</th>
                  <th scope="col">상태</th>
                  <th scope="col"><span className="sr-only">작업</span></th>
                </tr>
              </thead>
              <tbody>
                {active.map((board, index) =>
                  editing?.code === board.code ? (
                    <tr key={board.code} className="admin-board-editing">
                      <td colSpan={9}>
                        <form className="admin-board-form" onSubmit={saveEdit}>
                          <label>
                            이름
                            <input value={editing.name} maxLength={50} required onChange={(event) => setEditing({ ...editing, name: event.target.value })} />
                          </label>
                          <label>
                            홈 탭 이름
                            <input value={editing.shortName} maxLength={10} required onChange={(event) => setEditing({ ...editing, shortName: event.target.value })} />
                          </label>
                          <label>
                            코드
                            <input value={editing.code} disabled />
                          </label>
                          <RuleFields form={editing} onChange={setEditing} idPrefix={`edit-${board.code}`} />
                          <label className="admin-board-wide">
                            설명
                            <input value={editing.description} maxLength={200} onChange={(event) => setEditing({ ...editing, description: event.target.value })} />
                          </label>
                          <div className="admin-board-actions admin-board-wide">
                            <button className="admin-button" type="button" onClick={() => setEditing(null)}>취소</button>
                            <button className="admin-button primary" type="submit" disabled={busy}>저장</button>
                          </div>
                        </form>
                      </td>
                    </tr>
                  ) : (
                    <tr key={board.code} className={board.status === "HIDDEN" ? "admin-board-closed" : ""}>
                      <td>
                        <span className="admin-board-order">
                          <button className="admin-button" type="button" aria-label={`${board.name} 위로`} disabled={busy || index === 0} onClick={() => void run(API_ENDPOINTS.admin.boardMove(board.code, "UP"), { method: "POST" }, "순서를 바꾸지 못했습니다.")}>↑</button>
                          <span>{index + 1}</span>
                          <button className="admin-button" type="button" aria-label={`${board.name} 아래로`} disabled={busy || index === active.length - 1} onClick={() => void run(API_ENDPOINTS.admin.boardMove(board.code, "DOWN"), { method: "POST" }, "순서를 바꾸지 못했습니다.")}>↓</button>
                        </span>
                      </td>
                      <td><strong>{board.name}</strong></td>
                      <td>{board.shortName}</td>
                      <td><code>{board.code}</code></td>
                      <td>{WRITE_LABEL[board.writePermission]}</td>
                      <td>{ANONYMOUS_LABEL[board.anonymousMode]}</td>
                      <td>{board.showOnHome ? "보임" : "숨김"}</td>
                      <td><span className="admin-status" data-status={board.status === "OPEN" ? "APPROVED" : "HIDDEN"}>{board.status === "OPEN" ? "열림" : "닫힘"}</span></td>
                      <td>
                        <span className="admin-board-actions">
                          <button className="admin-button" type="button" disabled={busy} onClick={() => setEditing(toForm(board))}>수정</button>
                          <button className="admin-button" type="button" disabled={busy} onClick={() => toggleStatus(board)}>{board.status === "OPEN" ? "닫기" : "열기"}</button>
                          <button className="admin-button danger" type="button" disabled={busy} onClick={() => remove(board)}>삭제</button>
                        </span>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
        <p className="admin-notice">
          중고장터, 카풀, 1:1 문의는 전용 화면이 있어 여기서 관리하지 않습니다. 닫거나 삭제한 게시판의 글은 주소와 검색으로 계속 보입니다.
        </p>
      </section>

      <section className="panel admin-section" aria-label="게시판 추가">
        <h2>게시판 추가</h2>
        <form className="admin-board-form" onSubmit={create}>
          <label>
            이름
            <input value={draft.name} maxLength={50} required placeholder="예: 장비 리뷰" onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </label>
          <label>
            홈 탭 이름
            <input value={draft.shortName} maxLength={10} required placeholder="예: 리뷰" onChange={(event) => setDraft({ ...draft, shortName: event.target.value })} />
          </label>
          <label>
            코드 <small className="admin-meta">영문 대문자, 숫자, 밑줄. 만든 뒤 못 바꿈</small>
            <input value={draft.code} maxLength={20} required pattern="[A-Za-z][A-Za-z0-9_]{1,19}" placeholder="예: GEAR_REVIEW" onChange={(event) => setDraft({ ...draft, code: event.target.value.toUpperCase() })} />
          </label>
          <RuleFields form={draft} onChange={setDraft} idPrefix="create" />
          <label className="admin-board-wide">
            설명 <small className="admin-meta">게시판 위와 검색 결과에 보이는 문구</small>
            <input value={draft.description} maxLength={200} placeholder="예: 직접 써 본 장비 후기를 나누는 게시판입니다." onChange={(event) => setDraft({ ...draft, description: event.target.value })} />
          </label>
          <div className="admin-board-actions admin-board-wide">
            <button className="admin-button primary" type="submit" disabled={busy}>추가</button>
          </div>
        </form>
      </section>

      <section className="panel admin-section" aria-label="삭제한 게시판">
        <details>
          <summary>삭제한 게시판 ({deleted.length})</summary>
          {deleted.length === 0 ? (
            <p className="admin-meta">삭제한 게시판이 없습니다.</p>
          ) : (
            <div className="admin-list">
              {deleted.map((board) => (
                <div className="admin-row admin-board-deleted" key={board.code}>
                  <span>
                    <p>{board.name}</p>
                    <small className="admin-meta">{board.code}</small>
                  </span>
                  <small className="admin-meta">{board.deletedAt?.slice(0, 10).replaceAll("-", ".")} 삭제</small>
                  <button className="admin-button" type="button" disabled={busy} onClick={() => void run(API_ENDPOINTS.admin.boardRestore(board.code), { method: "POST" }, "복구하지 못했습니다.")}>
                    복구
                  </button>
                </div>
              ))}
            </div>
          )}
          <p className="admin-meta">복구한 게시판은 닫힌 상태로 돌아옵니다. 확인한 뒤 열어 주세요.</p>
        </details>
      </section>
    </>
  );
}
