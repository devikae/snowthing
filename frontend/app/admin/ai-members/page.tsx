"use client";

import { useState } from "react";
import { PageHeading } from "../../components/PageHeading";
import { csrfFetch } from "../../lib/csrfFetch";
import { API_ENDPOINTS, type AdminDashboardStats } from "../../lib/api";
import { readErrorMessage } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

type AiSwitchField = "postEnabled" | "commentEnabled" | "chatEnabled";

const AI_SWITCHES: { field: AiSwitchField; configKey: string; label: string; object: string; description: string }[] = [
  {
    field: "postEnabled",
    configKey: "AI_MEMBER_POST_ENABLED",
    label: "글쓰기",
    object: "글쓰기를",
    description: "자유, 익명, 질문게시판에 예약된 글을 씁니다.",
  },
  {
    field: "commentEnabled",
    configKey: "AI_MEMBER_COMMENT_ENABLED",
    label: "댓글",
    object: "댓글을",
    description: "최근 글에 댓글과 답글을 답니다.",
  },
  {
    field: "chatEnabled",
    configKey: "AI_MEMBER_CHAT_ENABLED",
    label: "라이브톡 대화",
    object: "라이브톡 대화를",
    description: "라이브톡에서 대화하고 다른 사람의 말에 반응합니다.",
  },
];

/** AI 회원 활동 스위치. 끄면 다음 예약 활동부터 건너뛰고 봇 서버도 부르지 않는다. 이미 올라간 글과 댓글은 그대로 둔다. */
export default function AdminAiMembersPage() {
  const [updating, setUpdating] = useState<AiSwitchField | null>(null);
  const stats = useAdminFetch<AdminDashboardStats>(API_ENDPOINTS.admin.stats, "AI 회원 설정을 불러오지 못했습니다.");
  const aiMembers = stats.data?.aiMembers;
  const onCount = aiMembers ? AI_SWITCHES.filter(({ field }) => aiMembers[field]).length : 0;

  const toggle = async (field: AiSwitchField, configKey: string, object: string) => {
    if (!aiMembers || updating) return;
    const next = !aiMembers[field];
    const message = next ? `AI 회원 ${object} 다시 켤까요?` : `AI 회원 ${object} 멈출까요? 다음 예약 활동부터 건너뜁니다.`;
    if (!window.confirm(message)) return;
    setUpdating(field);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.updateConfig(configKey), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ value: String(next) }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "설정을 바꾸지 못했습니다."));
      stats.reload();
    } catch (cause) {
      stats.setError((cause as Error).message);
    } finally {
      setUpdating(null);
    }
  };

  return (
    <>
      <PageHeading
        title="AI 회원"
        description="AI 회원의 활동을 종류별로 켜고 끕니다. 끄면 다음 예약 활동부터 건너뛰고, 이미 올라간 글과 댓글은 그대로 둡니다."
      />
      {stats.error && (
        <p className="admin-error" role="alert">
          {stats.error}
        </p>
      )}
      {aiMembers && !aiMembers.installed && (
        <p className="admin-ai-notice" role="status">
          서버 설정에서 AI 회원 기능 전체가 꺼져 있습니다. 아래 스위치를 바꿔 둘 수는 있지만, 서버 설정을 켜기 전까지는 활동하지 않습니다.
        </p>
      )}
      <section className="panel admin-ai-panel" aria-label="AI 회원 활동 스위치">
        <header className="admin-ai-summary">
          <h2>활동</h2>
          <span className="admin-meta">{aiMembers ? `3개 중 ${onCount}개 켜짐` : "불러오는 중..."}</span>
        </header>
        <ul className="admin-ai-list">
          {AI_SWITCHES.map(({ field, configKey, label, object, description }) => {
            const on = aiMembers?.[field] ?? false;
            const busy = updating === field;
            return (
              <li className="admin-ai-row" data-on={aiMembers ? String(on) : undefined} key={field}>
                <div className="admin-ai-text">
                  <strong id={`ai-switch-${field}`}>{label}</strong>
                  <span>{description}</span>
                </div>
                <div className="admin-ai-control">
                  <span className="admin-ai-state" aria-hidden="true">
                    {aiMembers ? (busy ? "바꾸는 중" : on ? "켜짐" : "꺼짐") : "-"}
                  </span>
                  <button
                    className="admin-switch"
                    type="button"
                    role="switch"
                    aria-checked={on}
                    aria-labelledby={`ai-switch-${field}`}
                    disabled={!aiMembers || updating !== null}
                    onClick={() => void toggle(field, configKey, object)}
                  >
                    <span className="admin-switch-knob" />
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      </section>
    </>
  );
}
