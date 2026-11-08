"use client";

import React, { useCallback, useEffect, useState } from "react";
import Image from "next/image";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS, BannerItem } from "../../lib/api";

export default function AdminBannersPage() {
  const [banners, setBanners] = useState<BannerItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [editingBanner, setEditingBanner] = useState<BannerItem | null>(null);
  const [title, setTitle] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [linkUrl, setLinkUrl] = useState("");
  const [displayOrder, setDisplayOrder] = useState(0);
  const [isActive, setIsActive] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchBanners = useCallback((signal?: AbortSignal) => {
    return fetch(API_ENDPOINTS.admin.banners, {
        credentials: "include",
        signal,
      })
      .then((res) => {
        if (!res.ok) throw new Error("배너 목록을 불러오지 못했습니다.");
        return res.json();
      })
      .then((data) => {
        if (signal?.aborted) return;
      setBanners(data || []);
      })
      .catch((err: unknown) => {
        if (signal?.aborted) return;
      alert((err instanceof Error ? err.message : "오류가 발생했습니다."));
      })
      .finally(() => { if (!signal?.aborted) setLoading(false); });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void fetchBanners(controller.signal);
    return () => controller.abort();
  }, [fetchBanners]);

  function handleOpenCreate() {
    setEditingBanner(null);
    setTitle("");
    setImageUrl("");
    setLinkUrl("");
    setDisplayOrder(banners.length);
    setIsActive(true);
    setShowModal(true);
  }

  function handleOpenEdit(b: BannerItem) {
    setEditingBanner(b);
    setTitle(b.title);
    setImageUrl(b.imageUrl);
    setLinkUrl(b.linkUrl || "");
    setDisplayOrder(b.displayOrder);
    setIsActive(b.isActive);
    setShowModal(true);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !imageUrl.trim()) return;

    try {
      setActionLoading(true);
      const payload = {
        title: title.trim(),
        imageUrl: imageUrl.trim(),
        linkUrl: linkUrl.trim() || null,
        displayOrder,
        isActive,
        startAt: editingBanner?.startAt ?? null,
        endAt: editingBanner?.endAt ?? null,
      };

      const endpoint = editingBanner
        ? API_ENDPOINTS.admin.bannerDetail(editingBanner.id)
        : API_ENDPOINTS.admin.banners;
      const method = editingBanner ? "PATCH" : "POST";

      const res = await csrfFetch(endpoint, {
        method,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error("배너 저장에 실패했습니다.");
      setShowModal(false);
      fetchBanners();
      alert("배너가 성공적으로 저장되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "저장 실패"));
    } finally {
      setActionLoading(false);
    }
  }

  async function handleToggleActive(b: BannerItem) {
    try {
      setActionLoading(true);
      const res = await csrfFetch(API_ENDPOINTS.admin.bannerDetail(b.id), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ title: b.title, imageUrl: b.imageUrl, linkUrl: b.linkUrl,
          displayOrder: b.displayOrder, isActive: !b.isActive, startAt: b.startAt, endAt: b.endAt }),
      });
      if (!res.ok) throw new Error("상태 변경에 실패했습니다.");
      setBanners((prev) =>
        prev.map((item) =>
          item.id === b.id ? { ...item, isActive: !b.isActive } : item
        )
      );
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "상태 변경 실패"));
    } finally {
      setActionLoading(false);
    }
  }

  async function handleDelete(b: BannerItem) {
    if (!window.confirm(`[${b.title}] 배너를 삭제하시겠습니까?`)) return;

    try {
      setActionLoading(true);
      const res = await csrfFetch(API_ENDPOINTS.admin.bannerDetail(b.id), {
        method: "DELETE",
        credentials: "include",
      });
      if (!res.ok) throw new Error("배너 삭제에 실패했습니다.");
      setBanners((prev) => prev.filter((item) => item.id !== b.id));
      alert("배너가 삭제되었습니다.");
    } catch (err: unknown) {
      alert((err instanceof Error ? err.message : "삭제 실패"));
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
            메인 상단 배너 관리
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            서비스 메인 화면 상단 프로모션 및 공지 배너 등록, 노출 순서 및 On/Off 제어
          </p>
        </div>

        <button
          onClick={handleOpenCreate}
          className="px-4 py-2.5 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-sm font-semibold transition shadow-sm flex items-center gap-2 self-start sm:self-auto"
        >
          <span>+</span>
          <span>새 배너 등록</span>
        </button>
      </div>

      {/* Banner Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 border-b border-slate-200/80 text-xs uppercase font-semibold text-slate-500">
              <tr>
                <th className="py-3.5 px-4">순서</th>
                <th className="py-3.5 px-4">미리보기</th>
                <th className="py-3.5 px-4">배너 제목</th>
                <th className="py-3.5 px-4">연결 링크</th>
                <th className="py-3.5 px-4">노출 상태</th>
                <th className="py-3.5 px-4 text-right">관리 작업</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    배너 목록을 불러오는 중입니다...
                  </td>
                </tr>
              ) : banners.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    등록된 배너가 없습니다.
                  </td>
                </tr>
              ) : (
                banners.map((b) => (
                  <tr key={b.id} className="hover:bg-slate-50/70 transition">
                    <td className="py-3.5 px-4 font-mono font-bold text-slate-900">
                      {b.displayOrder}
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="w-24 h-12 bg-slate-100 rounded-lg overflow-hidden relative border border-slate-200">
                        {b.imageUrl ? (
                          <Image
                            src={b.imageUrl}
                            alt={b.title}
                            fill
                            unoptimized
                            className="object-cover"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-[10px] text-slate-400">
                            No Img
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 font-bold text-slate-900 max-w-xs truncate">
                      {b.title}
                    </td>
                    <td className="py-3.5 px-4 text-xs font-mono text-slate-400 max-w-xs truncate">
                      {b.linkUrl ? (
                        <a
                          href={b.linkUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-sky-600 hover:underline"
                        >
                          {b.linkUrl}
                        </a>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td className="py-3.5 px-4">
                      <button
                        onClick={() => handleToggleActive(b)}
                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold transition ${
                          b.isActive
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : "bg-slate-100 text-slate-500 border border-slate-200"
                        }`}
                      >
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            b.isActive ? "bg-emerald-500" : "bg-slate-400"
                          }`}
                        />
                        {b.isActive ? "노출 중 (ON)" : "숨김 (OFF)"}
                      </button>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => handleOpenEdit(b)}
                          className="px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
                        >
                          수정
                        </button>
                        <button
                          onClick={() => handleDelete(b)}
                          className="px-2.5 py-1 rounded-lg border border-rose-200 text-rose-600 hover:bg-rose-50 text-xs font-medium transition"
                        >
                          삭제
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create / Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white max-w-md w-full rounded-2xl shadow-2xl border border-slate-200 p-6">
            <h3 className="text-base font-bold text-slate-900 mb-1">
              {editingBanner ? "배너 수정" : "새 배너 등록"}
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              메인 상단에 표시될 배너 이미지와 연결 링크 정보를 입력해 주세요.
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  배너 제목 (관리용)
                </label>
                <input
                  type="text"
                  required
                  placeholder="예: 25/26 시즌권 오픈 특가 안내"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-sky-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  이미지 URL
                </label>
                <input
                  type="url"
                  required
                  placeholder="https://... 또는 /images/..."
                  value={imageUrl}
                  onChange={(e) => setImageUrl(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-sky-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">
                  클릭 이동 링크 URL (선택사항)
                </label>
                <input
                  type="url"
                  placeholder="https://... (없으면 클릭 불가)"
                  value={linkUrl}
                  onChange={(e) => setLinkUrl(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-sky-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-700 block mb-1">
                    정렬 순서 (낮을수록 앞)
                  </label>
                  <input
                    type="number"
                    value={displayOrder}
                    onChange={(e) => setDisplayOrder(Number(e.target.value))}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-sky-500"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-700 block mb-1">
                    노출 상태
                  </label>
                  <select
                    value={isActive ? "true" : "false"}
                    onChange={(e) => setIsActive(e.target.value === "true")}
                    className="w-full px-3.5 py-2 rounded-xl border border-slate-300 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-sky-500"
                  >
                    <option value="true">노출 (ON)</option>
                    <option value="false">숨김 (OFF)</option>
                  </select>
                </div>
              </div>

              <div className="flex gap-2 justify-end pt-2 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50 transition"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-2 rounded-xl bg-sky-600 hover:bg-sky-700 text-white text-xs font-semibold transition"
                >
                  {actionLoading ? "저장 중..." : "배너 저장"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
