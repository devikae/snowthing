"use client";

import React, { useCallback, useEffect, useState } from "react";
import { API_ENDPOINTS, ChatBannedIpItem } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { useAdminFetch } from "../useAdminFetch";

interface RecentChatMessage {
  messageId: string;
  sender: { publicId: string; nickname: string };
  content: string;
  sentAt: string;
}

const RECENT_BAN_DAYS = [1, 7, 30] as const;

/**
 * 최근 라이브톡 대화 (IKA-115). 서버가 들고 있는 최근 30개를 새것부터 보여 주고 한 줄씩 가리거나 보낸 사람 IP를 막는다.
 * 실시간 연결 없이 새로고침 버튼으로 다시 읽는다. 관리 화면에서 계속 열어 두는 용도가 아니라 조치할 때 잠깐 보는 화면이라서다.
 */
function RecentChatPanel({ onBanned }: { onBanned: () => void }) {
  const recent = useAdminFetch<RecentChatMessage[]>(API_ENDPOINTS.chat.recent, "최근 대화를 불러오지 못했습니다.");
  // 가린 줄은 다시 읽기 전까지 화면에서 바로 뺀다.
  const [removedIds, setRemovedIds] = useState<Set<string>>(() => new Set());
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [banDays, setBanDays] = useState<(typeof RECENT_BAN_DAYS)[number]>(7);
  const messages = (Array.isArray(recent.data) ? recent.data : [])
    .filter((message) => !removedIds.has(message.messageId))
    .reverse();
  const loading = recent.loading;
  const error = actionError ?? recent.error;
  const load = () => {
    setActionError(null);
    recent.reload();
  };

  const hide = async (message: RecentChatMessage) => {
    if (busyId) return;
    setBusyId(message.messageId);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.hideChatMessage(message.messageId), { method: "DELETE" });
      if (!response.ok) throw new Error();
      setRemovedIds((previous) => new Set(previous).add(message.messageId));
    } catch {
      setActionError("메시지를 가리지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusyId(null);
    }
  };

  const ban = async (message: RecentChatMessage) => {
    if (busyId) return;
    if (!window.confirm(`${message.sender.nickname}의 IP를 ${banDays}일 막고 최근 메시지를 모두 지울까요?`)) return;
    setBusyId(message.messageId);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.banMessageIp(message.messageId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ days: banDays }),
      });
      if (!response.ok) throw new Error();
      load();
      onBanned();
    } catch {
      setActionError("IP를 차단하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-200/80 shadow-sm p-4 space-y-3" aria-label="최근 라이브톡 대화">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-slate-900">최근 대화</h3>
          <p className="text-xs text-slate-500">
            최근 30개를 새것부터 보여 줍니다. 가리기는 그 줄만 모든 화면에서 지우고, 차단은 보낸 사람 IP를 막고 최근 메시지를 모두 지웁니다.
            가린 메시지는 되돌릴 수 없습니다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs text-slate-600">
            차단 기간{" "}
            <select
              value={banDays}
              onChange={(event) => setBanDays(Number(event.target.value) as (typeof RECENT_BAN_DAYS)[number])}
              className="ml-1 rounded-lg border border-slate-300 bg-white px-2 py-1 text-xs"
            >
              {RECENT_BAN_DAYS.map((days) => (
                <option key={days} value={days}>
                  {days}일
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={load}
            disabled={loading}
            className="rounded-lg border border-slate-200 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
          >
            {loading ? "불러오는 중" : "새로고침"}
          </button>
        </div>
      </div>
      {error && <p role="alert" className="text-xs font-semibold text-rose-600">{error}</p>}
      {!loading && messages.length === 0 && <p className="py-6 text-center text-sm text-slate-500">최근 대화가 없습니다.</p>}
      <ul className="divide-y divide-slate-100">
        {messages.map((message) => (
          <li key={message.messageId} className="flex flex-wrap items-start gap-2 py-2">
            <div className="min-w-0 flex-1">
              <p className="text-xs text-slate-500">
                <strong className="text-slate-800">{message.sender.nickname}</strong>
                <span className="ml-2 font-mono">{message.sentAt.slice(5, 16).replace("T", " ")}</span>
              </p>
              <p className="break-words text-sm text-slate-800">{message.content}</p>
            </div>
            <div className="flex shrink-0 gap-1">
              <button
                type="button"
                onClick={() => void hide(message)}
                disabled={busyId !== null}
                className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-60"
              >
                가리기
              </button>
              {message.sender.publicId !== "snowthing-ai" && (
                <button
                  type="button"
                  onClick={() => void ban(message)}
                  disabled={busyId !== null}
                  className="rounded-lg bg-rose-50 px-2.5 py-1 text-xs font-bold text-rose-600 hover:bg-rose-100 disabled:opacity-60"
                >
                  차단
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function AdminChatPage() {
  const [bannedIps, setBannedIps] = useState<ChatBannedIpItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Manual Ban Modal
  const [showBanModal, setShowBanModal] = useState(false);
  const [ipInput, setIpInput] = useState("");
  const [reasonInput, setReasonInput] = useState("");
  const [daysInput, setDaysInput] = useState(7);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchBannedIps = useCallback((signal?: AbortSignal) => {
    return csrfFetch(API_ENDPOINTS.admin.bannedIps, {
        credentials: "include",
        signal,
      })
      .then((res) => {
        if (!res.ok) throw new Error("차단 목록을 불러오지 못했습니다.");
        return res.json();
      })
      .then((data) => {
        if (signal?.aborted) return;
      setBannedIps(data || []);
      })
      .catch((err: unknown) => {
        if (signal?.aborted) return;
      alert((err instanceof Error ? err.message : "오류가 발생했습니다."));
      })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void fetchBannedIps(controller.signal);
    return () => controller.abort();
  }, [fetchBannedIps]);

  async function handleUnban(item: ChatBannedIpItem) {
    if (
      !window.confirm(
        `[${item.bannedIp}] IP 차단을 해제할까요?\n이 IP(대역)에서 다시 글, 댓글, 라이브톡을 쓸 수 있게 됩니다.`
      )
    )
      return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(API_ENDPOINTS.admin.unbanIp(item.id), {
        method: "DELETE",
        credentials: "include",
      });
      if (!res.ok) throw new Error("차단을 해제하지 못했습니다.");
      setBannedIps((prev) => prev.filter((b) => b.id !== item.id));
      alert("차단이 해제되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "차단을 해제하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    } finally {
      setActionLoading(false);
    }
  }

  async function handleCreateBan(e: React.FormEvent) {
    e.preventDefault();
    if (!ipInput.trim() || !reasonInput.trim()) return;

    try {
      setActionLoading(true);
      const bannedUntil = new Date(
        Date.now() + daysInput * 24 * 60 * 60 * 1000
      ).toISOString();

      const res = await csrfFetch(API_ENDPOINTS.admin.bannedIps, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          bannedIp: ipInput.trim(),
          reason: reasonInput.trim(),
          bannedUntil,
        }),
      });

      if (!res.ok) {
        const errJson = await res.json().catch(() => null);
        throw new Error(errJson?.message || "IP 차단을 등록하지 못했습니다.");
      }

      setShowBanModal(false);
      setIpInput("");
      setReasonInput("");
      fetchBannedIps();
      alert("IP가 성공적으로 차단 목록에 등록되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "IP 차단을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Title & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-slate-900 tracking-tight">
            라이브톡, IP 차단 관리
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            최근 라이브톡 대화를 보고 조치합니다. 차단한 IP와 대역은 게시글, 댓글, 라이브톡 작성이 모두 막힙니다. 차단된 사람에게는 차단 내용을 알려 주지 않습니다.
          </p>
        </div>

        <button
          onClick={() => setShowBanModal(true)}
          className="px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-sm font-semibold transition shadow-sm flex items-center gap-2 self-start sm:self-auto"
        >
          <span>+</span>
          <span>수동 IP 차단 등록</span>
        </button>
      </div>

      <RecentChatPanel onBanned={() => void fetchBannedIps()} />

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-xs uppercase font-semibold text-slate-500">
              <tr>
                <th className="py-3.5 px-4">차단된 IP, 대역</th>
                <th className="py-3.5 px-4">차단 사유</th>
                <th className="py-3.5 px-4">차단 만료일</th>
                <th className="py-3.5 px-4">등록자</th>
                <th className="py-3.5 px-4">차단 등록일</th>
                <th className="py-3.5 px-4 text-right">관리 작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500">
                    차단 목록을 불러오는 중입니다...
                  </td>
                </tr>
              ) : bannedIps.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500">
                    현재 차단된 IP가 없습니다.
                  </td>
                </tr>
              ) : (
                bannedIps.map((b) => (
                  <tr key={b.id} className="hover:bg-slate-50/70 transition">
                    <td className="py-3.5 px-4 font-mono font-bold text-slate-900">
                      {b.bannedIp}
                    </td>
                    <td className="py-3.5 px-4 text-slate-700 max-w-xs truncate">
                      {b.reason}
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono text-slate-500">
                      {b.bannedUntil ? b.bannedUntil.slice(0, 16).replace("T", " ") : "영구"}
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-500">
                      {b.createdBy || "시스템"}
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono text-slate-500">
                      {b.createdAt ? b.createdAt.slice(0, 10) : "-"}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <button
                        onClick={() => handleUnban(b)}
                        className="px-3 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
                      >
                        차단 해제
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Manual Ban Modal */}
      {showBanModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white max-w-md w-full rounded-2xl shadow-2xl border border-slate-200 p-6">
            <h3 className="text-base font-bold text-slate-900 mb-1">
              IP 수동 차단 추가
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              차단된 IP나 대역에서는 게시글, 댓글, 라이브톡을 쓸 수 없습니다. IPv6은 주소가 자주 바뀌므로 /64 대역으로 막는 것을 권합니다.
            </p>
            <form onSubmit={handleCreateBan} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  IP 주소 또는 대역 (IPv4, IPv6, CIDR)
                </label>
                <input
                  type="text"
                  required
                  placeholder="예: 123.45.67.89, 2001:db8:1:2::/64"
                  value={ipInput}
                  onChange={(e) => setIpInput(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  차단 사유
                </label>
                <input
                  type="text"
                  required
                  placeholder="예: 불법 도배 광고, 음란 링크 전송"
                  value={reasonInput}
                  onChange={(e) => setReasonInput(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  차단 기간
                </label>
                <select
                  value={daysInput}
                  onChange={(e) => setDaysInput(Number(e.target.value))}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-rose-500"
                >
                  <option value={1}>1일</option>
                  <option value={3}>3일</option>
                  <option value={7}>7일 (기본)</option>
                  <option value={30}>30일</option>
                  <option value={365}>1년 (장기 차단)</option>
                </select>
              </div>

              <div className="flex gap-2 justify-end pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowBanModal(false)}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold transition"
                >
                  {actionLoading ? "등록 중..." : "차단 등록"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
