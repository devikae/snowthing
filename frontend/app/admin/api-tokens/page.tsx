"use client";

import { useCallback, useEffect, useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { type ApiTokenItem, formatDateTime, readErrorMessage } from "../adminTypes";

export default function ApiTokenPage() {
  const [tokens, setTokens] = useState<ApiTokenItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState({ READ: true, DRAFT: false });
  const [days, setDays] = useState(30);
  const [issued, setIssued] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fetchTokens = useCallback(async () => {
    const response = await fetch(API_ENDPOINTS.admin.apiTokens, { credentials: "include" });
    if (!response.ok) throw new Error(await readErrorMessage(response, "토큰 목록을 불러오지 못했습니다."));
    return (await response.json()) as ApiTokenItem[];
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchTokens()
      .then((body) => {
        if (!cancelled) setTokens(body);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [fetchTokens]);

  const reload = async () => {
    try {
      setTokens(await fetchTokens());
    } catch (cause) {
      setError((cause as Error).message);
    }
  };

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setIssued(null);
    try {
      const selected = (Object.keys(scopes) as Array<keyof typeof scopes>).filter((key) => scopes[key]);
      const response = await csrfFetch(API_ENDPOINTS.admin.apiTokens, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, scopes: selected, expiresInDays: days }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "토큰을 만들지 못했습니다."));
      const body = (await response.json()) as { token: string };
      setIssued(body.token);
      setName("");
      await reload();
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (tokenId: string) => {
    if (!window.confirm("이 토큰을 폐기할까요? 되돌릴 수 없습니다.")) return;
    setError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.revokeApiToken(tokenId), { method: "POST" });
      if (!response.ok) throw new Error(await readErrorMessage(response, "토큰을 폐기하지 못했습니다."));
      await reload();
    } catch (cause) {
      setError((cause as Error).message);
    }
  };

  return (
    <>
      <PageHeading
        title="API 토큰"
        description="로컬 MCP가 쓰는 제한 토큰입니다. READ는 근거 검색과 조회, DRAFT는 초안 저장과 상태 조회만 할 수 있습니다. 어떤 토큰도 승인이나 게시는 할 수 없습니다."
      />
      {error && <p className="admin-error" role="alert">{error}</p>}
      {issued && (
        <div className="admin-token-once" role="status">
          <strong>토큰은 지금 한 번만 보입니다. 안전한 곳(Windows 자격 증명 관리자 등)에 저장하세요.</strong>
          <code className="admin-token-value">{issued}</code>
          <div className="admin-actions">
            <button className="admin-button" type="button" onClick={() => void navigator.clipboard?.writeText(issued)}>
              복사
            </button>
            <button className="admin-button" type="button" onClick={() => setIssued(null)}>
              닫기
            </button>
          </div>
        </div>
      )}
      <section className="panel admin-section" aria-label="토큰 만들기">
        <h2>새 토큰</h2>
        <form className="admin-form" onSubmit={(event) => void create(event)}>
          <label>
            이름
            <input value={name} maxLength={50} required onChange={(event) => setName(event.target.value)} placeholder="예: 내 PC 로컬 MCP" />
          </label>
          <fieldset>
            <legend className="admin-meta">범위</legend>
            <label>
              <input type="checkbox" checked={scopes.READ} onChange={(event) => setScopes({ ...scopes, READ: event.target.checked })} /> READ
            </label>
            <label>
              <input type="checkbox" checked={scopes.DRAFT} onChange={(event) => setScopes({ ...scopes, DRAFT: event.target.checked })} /> DRAFT
            </label>
          </fieldset>
          <label>
            만료 (일, 1~30)
            <input type="number" min={1} max={30} value={days} onChange={(event) => setDays(Number(event.target.value))} />
          </label>
          <div className="admin-actions">
            <button className="admin-button primary" type="submit" disabled={busy || !name.trim() || (!scopes.READ && !scopes.DRAFT)}>
              만들기
            </button>
          </div>
        </form>
      </section>
      <section className="panel admin-section" aria-label="내 토큰 목록">
        <h2>내 토큰</h2>
        <div className="admin-list">
          {tokens.length === 0 && <p className="admin-meta">만든 토큰이 없습니다.</p>}
          {tokens.map((token) => (
            <div className="admin-row" key={token.tokenId}>
              <span className="admin-status" data-status={token.active ? "APPROVED" : "REJECTED"}>
                {token.active ? "사용 중" : token.revokedAt ? "폐기됨" : "만료됨"}
              </span>
              <span style={{ minWidth: 0 }}>
                <p>
                  {token.name} <code className="admin-token-value">{token.tokenPrefix}...</code>
                </p>
                <small>
                  {token.scopes.join(", ")}, 만료 {formatDateTime(token.expiresAt)}, 마지막 사용 {formatDateTime(token.lastUsedAt)}
                </small>
              </span>
              <button className="admin-button danger" type="button" disabled={!token.active} onClick={() => void revoke(token.tokenId)}>
                폐기
              </button>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
