"use client";

import { useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS, type AdminDmMailOutboxSummary, type AdminDmMailSwitches } from "../../lib/api";
import { formatDateTime, readErrorMessage } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

type SwitchField = "enqueue" | "send";

const SWITCHES: { field: SwitchField; label: string; description: (hours: number) => string }[] = [
  {
    field: "enqueue",
    label: "기록",
    description: () => "쪽지가 오면 메일 발송 요청을 쌓습니다. 받는 사람이 메일 알림을 켠 경우에만 쌓습니다.",
  },
  {
    field: "send",
    label: "발송",
    description: (hours) => `쌓인 요청을 메일로 보냅니다. 쪽지가 온 지 ${hours}시간이 지난 요청은 보내지 않습니다.`,
  },
];

const COUNTS: { status: keyof AdminDmMailOutboxSummary["counts"]; label: string; attention?: boolean }[] = [
  { status: "PENDING", label: "대기" },
  { status: "ACCEPTED", label: "성공" },
  { status: "RETRY_WAIT", label: "재시도 대기" },
  { status: "FAILED", label: "실패", attention: true },
  { status: "UNKNOWN", label: "확인 필요", attention: true },
  { status: "SKIPPED", label: "건너뜀" },
];

/**
 * 쪽지 도착 메일 스위치와 발송 상태 (IKA-109). 기록과 발송을 따로 켜고 끈다. 서버 설정(환경변수)이 꺼져 있으면 여기서 켜도 동작하지 않는다.
 */
export default function AdminDmMailPage() {
  const [updating, setUpdating] = useState<SwitchField | null>(null);
  const switches = useAdminFetch<AdminDmMailSwitches>(API_ENDPOINTS.admin.dmMailSwitches, "쪽지 메일 설정을 불러오지 못했습니다.");
  const summary = useAdminFetch<AdminDmMailOutboxSummary>(
    API_ENDPOINTS.admin.dmMailOutboxSummary,
    "발송 상태를 불러오지 못했습니다.",
  );
  const data = switches.data;
  const counts = summary.data?.counts;
  const error = switches.error ?? summary.error;
  const serverBlocked = data ? !data.enqueue.serverAllowed || !data.send.serverAllowed : false;
  const providerBlocked = data ? data.send.serverAllowed && !data.mailProviderReady : false;

  const confirmMessage = (field: SwitchField, next: boolean) => {
    if (!data) return "";
    if (field === "enqueue") {
      return next
        ? "쪽지 메일 기록을 켤까요? 다음 쪽지부터 발송 요청을 쌓습니다."
        : "쪽지 메일 기록을 끌까요? 다음 쪽지부터 발송 요청을 쌓지 않습니다. 이미 쌓인 요청은 그대로 둡니다.";
    }
    if (!next) return "쪽지 메일 발송을 끌까요? 지금 보내는 한 통까지만 나가고 그 뒤로는 보내지 않습니다.";
    if (!data.send.serverAllowed) {
      return "쪽지 메일 발송을 켜 둘까요? 지금은 서버 설정에서 막혀 있어 메일이 나가지 않습니다. 서버 설정을 켜면 실제 회원에게 메일이 나갑니다.";
    }
    const waiting = counts ? counts.PENDING + counts.RETRY_WAIT : 0;
    const backlog = waiting > 0 ? ` 대기 중인 요청 ${waiting}건도 바로 보냅니다.` : "";
    return `쪽지 메일 발송을 켤까요? 실제 회원에게 메일이 나갑니다.${backlog} ${data.deadlineHours}시간이 지난 요청은 보내지 않고 건너뜁니다.`;
  };

  const toggle = async (field: SwitchField) => {
    if (!data || updating) return;
    const next = !data[field].switchOn;
    if (!window.confirm(confirmMessage(field, next))) return;
    setUpdating(field);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.updateConfig(data[field].configKey), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ value: String(next) }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "설정을 바꾸지 못했습니다."));
      switches.reload();
      summary.reload();
    } catch (cause) {
      switches.setError((cause as Error).message);
    } finally {
      setUpdating(null);
    }
  };

  const stateLabel = (field: SwitchField) => {
    if (!data) return "-";
    if (updating === field) return "바꾸는 중";
    const state = data[field];
    if (!state.switchOn) return "꺼짐";
    return state.effective ? "켜짐" : "켜 둠, 막힘";
  };

  return (
    <>
      <PageHeading
        title="쪽지 메일"
        description="쪽지가 오면 받는 사람에게 메일로 알립니다. 발송 요청을 쌓는 기록과 실제로 보내는 발송을 따로 켜고 끕니다."
      />
      {error && (
        <p className="admin-error" role="alert">
          {error}
        </p>
      )}
      {serverBlocked && (
        <p className="admin-ai-notice" role="status">
          서버 설정에서 막혀 있습니다. 아래 스위치를 켜 둘 수는 있지만, 서버 설정을 켜기 전까지는 동작하지 않습니다.
        </p>
      )}
      {providerBlocked && (
        <p className="admin-ai-notice" role="status">
          서버의 메일 발송 기능이 꺼져 있습니다. 발송 스위치를 켜도 메일이 나가지 않습니다.
        </p>
      )}
      <section className="panel admin-ai-panel" aria-label="쪽지 메일 스위치">
        <header className="admin-ai-summary">
          <h2>스위치</h2>
          <span className="admin-meta">{data ? "바꾸면 바로 반영됩니다" : "불러오는 중..."}</span>
        </header>
        <ul className="admin-ai-list">
          {SWITCHES.map(({ field, label, description }) => {
            const state = data?.[field];
            const on = state?.switchOn ?? false;
            const blocked = state ? !state.serverAllowed : false;
            return (
              <li
                className="admin-ai-row"
                data-on={state ? String(state.effective) : undefined}
                data-blocked={blocked ? "true" : undefined}
                key={field}
              >
                <div className="admin-ai-text">
                  <strong id={`dm-mail-switch-${field}`}>{label}</strong>
                  <span>{description(data?.deadlineHours ?? 24)}</span>
                  {blocked && <span className="admin-mail-blocked">서버 설정에서 막혀 있습니다.</span>}
                </div>
                <div className="admin-ai-control">
                  <span className="admin-ai-state" aria-hidden="true">
                    {stateLabel(field)}
                  </span>
                  <button
                    className="admin-switch"
                    type="button"
                    role="switch"
                    aria-checked={on}
                    aria-labelledby={`dm-mail-switch-${field}`}
                    disabled={!data || updating !== null}
                    onClick={() => void toggle(field)}
                  >
                    <span className="admin-switch-knob" />
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      </section>
      <section className="panel admin-section" aria-label="발송 상태">
        <div className="admin-toolbar">
          <h2>발송 상태</h2>
          <button className="admin-button" type="button" disabled={summary.loading} onClick={summary.reload}>
            새로고침
          </button>
        </div>
        {counts ? (
          <>
            <dl className="admin-mail-counts">
              {COUNTS.map(({ status, label, attention }) => (
                <div key={status} data-attention={attention && counts[status] > 0 ? "true" : undefined}>
                  <dt>{label}</dt>
                  <dd>{counts[status].toLocaleString("ko-KR")}</dd>
                </div>
              ))}
            </dl>
            <ul className="admin-audit">
              {counts.PROCESSING > 0 && <li>보내는 중: {counts.PROCESSING}건</li>}
              <li>가장 오래 기다린 요청: {formatDateTime(summary.data?.oldestOpenCreatedAt ?? null)}</li>
              <li>확인 시각: {formatDateTime(summary.data?.checkedAt ?? null)}</li>
              <li>성공은 메일 서비스가 요청을 받았다는 뜻입니다. 확인 필요는 나갔는지 알 수 없어 다시 보내지 않은 요청입니다.</li>
            </ul>
          </>
        ) : (
          <p className="admin-meta">{summary.error ? "불러오지 못했습니다." : "불러오는 중..."}</p>
        )}
      </section>
    </>
  );
}
