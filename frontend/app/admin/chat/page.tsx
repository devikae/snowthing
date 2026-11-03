"use client";

import React, { useCallback, useEffect, useState } from "react";
import { API_ENDPOINTS, ChatBannedIpItem } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";

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
        `[${item.bannedIp}] IP 차단을 해제하시겠습니까?\n해당 IP에서 다시 채팅 전송이 가능해집니다.`
      )
    )
      return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(API_ENDPOINTS.admin.unbanIp(item.id), {
        method: "DELETE",
        credentials: "include",
      });
      if (!res.ok) throw new Error("차단 해제에 실패했습니다.");
      setBannedIps((prev) => prev.filter((b) => b.id !== item.id));
      alert("차단이 해제되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "차단 해제 실패"));
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
        throw new Error(errJson?.message || "IP 차단 등록에 실패했습니다.");
      }

      setShowBanModal(false);
      setIpInput("");
      setReasonInput("");
      fetchBannedIps();
      alert("IP가 성공적으로 차단 목록에 등록되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "차단 등록 실패"));
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
            채팅 IP 차단 관리
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            악성 유저, 광고/도배 계정의 IP 실시간 차단 목록 및 해제 관리
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

      {/* Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-xs uppercase font-semibold text-slate-500">
              <tr>
                <th className="py-3.5 px-4">차단된 IP</th>
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
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    차단 목록을 불러오는 중입니다...
                  </td>
                </tr>
              ) : bannedIps.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
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
                    <td className="py-3.5 px-4 text-xs font-mono text-slate-400">
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
              차단된 IP는 실시간 라이브톡에 메시지를 전송할 수 없습니다.
            </p>
            <form onSubmit={handleCreateBan} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  IP 주소 (IPv4 / IPv6)
                </label>
                <input
                  type="text"
                  required
                  placeholder="예: 123.45.67.89"
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
