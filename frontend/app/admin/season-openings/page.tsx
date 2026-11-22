"use client";

import { useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { csrfFetch } from "../../lib/csrfFetch";
import { BANNER_TITLE_MAX, DEFAULT_BANNER_TITLE, type SeasonOpening, type SeasonOpenings } from "../../lib/seasonOpening";
import { clearSeasonOpeningsCache } from "../../lib/useSeasonOpenings";
import { readErrorMessage } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

/**
 * 리조트 개장일 관리 (IKA-87). 날짜, 확정 여부, 메인 배너 표시와 순서, 배너 전체 스위치를 한 번에 저장한다.
 * 저장하면 사용자 화면은 새로고침할 때 새 값을 받는다. 배포는 필요 없다.
 */
export default function AdminSeasonOpeningsPage() {
  const loaded = useAdminFetch<SeasonOpenings>(API_ENDPOINTS.admin.seasonOpenings, "개장일을 불러오지 못했습니다.");
  const [draft, setDraft] = useState<SeasonOpenings | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const current = draft ?? loaded.data;

  const change = (next: SeasonOpenings) => {
    setDraft(next);
    setDirty(true);
    setNotice(null);
  };

  const updateResort = (code: string, patch: Partial<SeasonOpening>) => {
    if (!current) return;
    change({
      ...current,
      resorts: current.resorts.map((resort) => (resort.resortCode === code ? { ...resort, ...patch } : resort)),
    });
  };

  const move = (index: number, offset: -1 | 1) => {
    if (!current) return;
    const target = index + offset;
    if (target < 0 || target >= current.resorts.length) return;
    const resorts = [...current.resorts];
    [resorts[index], resorts[target]] = [resorts[target], resorts[index]];
    change({ ...current, resorts });
  };

  async function save() {
    if (!current) return;
    const missingDate = current.resorts.find((resort) => resort.showOnBanner && !resort.openingDate);
    if (missingDate) {
      setSaveError(`${missingDate.name}: 배너에 넣으려면 개장일이 있어야 합니다.`);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.seasonOpenings, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          bannerEnabled: current.bannerEnabled,
          bannerTitle: current.bannerTitle?.trim() || DEFAULT_BANNER_TITLE,
          resorts: current.resorts.map((resort, index) => ({
            resortCode: resort.resortCode,
            openingDate: resort.openingDate || null,
            confirmed: resort.confirmed,
            showOnBanner: resort.showOnBanner,
            bannerOrder: index + 1,
          })),
        }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "저장하지 못했습니다."));
      setDraft((await response.json()) as SeasonOpenings);
      setDirty(false);
      clearSeasonOpeningsCache();
      setNotice("저장했습니다. 사용자 화면은 새로고침하면 바뀝니다.");
    } catch (cause) {
      setSaveError(cause instanceof Error ? cause.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeading
        title="개장일"
        description="리조트별 개장일과 메인 개장 배너를 관리합니다. 확정하지 않은 날짜는 사용자 화면에 예상으로 보입니다."
      />
      <section className="panel admin-section" aria-label="개장일 목록">
        {loaded.error && (
          <p className="admin-error" role="alert">
            {loaded.error}
          </p>
        )}
        {loaded.loading && !current && <p className="admin-meta">불러오는 중...</p>}
        {current && (
          <>
            <div className="admin-toolbar">
              <label className="admin-season-switch">
                <input
                  type="checkbox"
                  checked={current.bannerEnabled}
                  onChange={(event) => change({ ...current, bannerEnabled: event.target.checked })}
                />
                메인 개장 배너 켜기
              </label>
              <label className="admin-season-title">
                배너 문구
                <input
                  type="text"
                  value={current.bannerTitle ?? DEFAULT_BANNER_TITLE}
                  maxLength={BANNER_TITLE_MAX}
                  placeholder={DEFAULT_BANNER_TITLE}
                  onChange={(event) => change({ ...current, bannerTitle: event.target.value })}
                />
              </label>
              <button className="admin-button primary" type="button" disabled={saving || !dirty} onClick={save}>
                {saving ? "저장 중..." : "저장"}
              </button>
            </div>
            <p className="admin-notice">
              배너에는 &quot;배너에 표시&quot;를 고른 리조트 중 아직 개장하지 않은 곳만 위에서부터 보입니다. 모두 개장하면 배너는 저절로
              사라집니다.
            </p>
            {saveError && (
              <p className="admin-error" role="alert">
                {saveError}
              </p>
            )}
            {notice && (
              <p className="admin-meta" role="status">
                {notice}
              </p>
            )}
            {current.resorts.length === 0 && <p className="admin-meta">등록된 리조트가 없습니다. 마이그레이션 적용을 확인해 주세요.</p>}
            <div className="admin-list">
              {current.resorts.map((resort, index) => (
                <div className="admin-row admin-season-row" key={resort.resortCode}>
                  <span className="admin-season-order">
                    <button
                      className="admin-button"
                      type="button"
                      aria-label={`${resort.name} 위로`}
                      disabled={index === 0}
                      onClick={() => move(index, -1)}
                    >
                      ↑
                    </button>
                    <button
                      className="admin-button"
                      type="button"
                      aria-label={`${resort.name} 아래로`}
                      disabled={index === current.resorts.length - 1}
                      onClick={() => move(index, 1)}
                    >
                      ↓
                    </button>
                  </span>
                  <span>
                    <p>
                      {index + 1}. {resort.name}
                    </p>
                    <small className="admin-meta">{resort.resortCode}</small>
                  </span>
                  <span className="admin-season-fields">
                    <input
                      type="date"
                      aria-label={`${resort.name} 개장일`}
                      value={resort.openingDate ?? ""}
                      min="2020-01-01"
                      max="2100-12-31"
                      onChange={(event) => updateResort(resort.resortCode, { openingDate: event.target.value || null })}
                    />
                    <label>
                      <input
                        type="checkbox"
                        checked={resort.confirmed}
                        onChange={(event) => updateResort(resort.resortCode, { confirmed: event.target.checked })}
                      />
                      확정
                    </label>
                    <label>
                      <input
                        type="checkbox"
                        checked={resort.showOnBanner}
                        onChange={(event) => updateResort(resort.resortCode, { showOnBanner: event.target.checked })}
                      />
                      배너에 표시
                    </label>
                  </span>
                </div>
              ))}
            </div>
          </>
        )}
      </section>
    </>
  );
}
