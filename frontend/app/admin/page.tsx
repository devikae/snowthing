"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { csrfFetch } from "../lib/csrfFetch";
import { API_ENDPOINTS, type AdminDashboardStats } from "../lib/api";
import { type AdminDailyCount, type AdminOverview, readErrorMessage } from "./adminTypes";
import { useAdminFetch } from "./useAdminFetch";

type Metric = "signups" | "posts" | "comments";

const METRICS: { key: Metric; label: string }[] = [
  { key: "signups", label: "가입" },
  { key: "posts", label: "글" },
  { key: "comments", label: "댓글" },
];

/** 일별 막대 그래프. 차트 라이브러리 없이 SVG로 그린다. 막대마다 날짜와 값을 title로 붙여 마우스를 올리면 보인다. */
function DailyBars({ series, metric, label }: { series: AdminDailyCount[]; metric: Metric; label: string }) {
  const max = Math.max(1, ...series.map((day) => day[metric]));
  const width = 100 / series.length;
  const total = series.reduce((sum, day) => sum + day[metric], 0);
  return (
    <figure className="admin-chart" style={{ margin: 0 }}>
      <figcaption className="admin-meta">
        {label} 합계 {total.toLocaleString()}건, 하루 최대 {max.toLocaleString()}건
      </figcaption>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label={`일별 ${label} 수`}>
        {series.map((day, index) => {
          const height = (day[metric] / max) * 100;
          return (
            <rect key={day.date} x={index * width + width * 0.15} y={100 - height} width={width * 0.7} height={height}>
              <title>{`${day.date}: ${day[metric]}건`}</title>
            </rect>
          );
        })}
      </svg>
      <div className="admin-chart-axis">
        <span>{series[0]?.date.slice(5)}</span>
        <span>{series[series.length - 1]?.date.slice(5)}</span>
      </div>
    </figure>
  );
}

/** 관리자 대시보드. 오늘 숫자, 처리 대기 건수, 최근 7일이나 30일 추이, 비회원 라이브톡 설정을 보여 준다. 날짜는 KST 기준이다. */
export default function AdminDashboardPage() {
  const [days, setDays] = useState<7 | 30>(7);
  const [metric, setMetric] = useState<Metric>("signups");
  const [updatingChat, setUpdatingChat] = useState(false);
  const overview = useAdminFetch<AdminOverview>(API_ENDPOINTS.admin.overview(days), "운영 현황을 불러오지 못했습니다.");
  const stats = useAdminFetch<AdminDashboardStats>(API_ENDPOINTS.admin.stats, "회원 현황을 불러오지 못했습니다.");

  const toggleAnonymousChat = async () => {
    if (!stats.data || updatingChat) return;
    const next = !stats.data.anonymousChatEnabled;
    const message = next
      ? "로그인하지 않은 사용자도 라이브톡에 메시지를 보낼 수 있게 할까요?"
      : "로그인하지 않은 사용자의 라이브톡 전송을 막고 로그인 회원만 보낼 수 있게 할까요?";
    if (!window.confirm(message)) return;
    setUpdatingChat(true);
    try {
      const response = await csrfFetch(API_ENDPOINTS.admin.updateConfig("CHAT_ANONYMOUS_ENABLED"), {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ value: String(next) }),
      });
      if (!response.ok) throw new Error(await readErrorMessage(response, "설정을 바꾸지 못했습니다."));
      stats.reload();
    } catch (cause) {
      stats.setError((cause as Error).message);
    } finally {
      setUpdatingChat(false);
    }
  };

  const today = overview.data?.today;
  const metricLabel = METRICS.find((item) => item.key === metric)?.label ?? "";

  return (
    <>
      <PageHeading title="운영 현황" description="오늘 숫자와 처리할 일, 최근 추이를 봅니다. 날짜는 한국 시간 기준입니다." />
      {(overview.error || stats.error) && (
        <p className="admin-error" role="alert">
          {overview.error ?? stats.error}
        </p>
      )}
      <section className="panel admin-section" aria-label="오늘과 처리 대기">
        <h2>오늘</h2>
        <div className="admin-kpis">
          <div className="admin-kpi">
            <span className="admin-meta">신규 가입</span>
            <strong>{today ? today.signups.toLocaleString() : "-"}</strong>
            <span className="admin-meta">전체 회원 {stats.data ? stats.data.totalMembers.toLocaleString() : "-"}명</span>
          </div>
          <div className="admin-kpi">
            <span className="admin-meta">새 글</span>
            <strong>{today ? today.posts.toLocaleString() : "-"}</strong>
          </div>
          <div className="admin-kpi">
            <span className="admin-meta">새 댓글</span>
            <strong>{today ? today.comments.toLocaleString() : "-"}</strong>
          </div>
          <div className="admin-kpi">
            <span className="admin-meta">처리 대기 신고</span>
            <strong>{overview.data ? overview.data.pendingReports.toLocaleString() : "-"}</strong>
            <Link href="/admin/reports">신고 처리로 가기</Link>
          </div>
          <div className="admin-kpi">
            <span className="admin-meta">답변 대기 1:1 문의</span>
            <strong>{overview.data ? overview.data.unansweredInquiries.toLocaleString() : "-"}</strong>
            <Link href="/admin/inquiries">1:1 문의로 가기</Link>
          </div>
        </div>
      </section>
      <section className="panel admin-section" aria-label="일별 추이">
        <div className="admin-toolbar">
          <h2>일별 추이</h2>
          <div className="admin-filter" role="group" aria-label="기간">
            {([7, 30] as const).map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={days === value}
                onClick={() => {
                  if (days === value) return;
                  overview.setLoading(true);
                  setDays(value);
                }}
              >
                {value}일
              </button>
            ))}
          </div>
        </div>
        <div className="admin-filter" role="group" aria-label="항목">
          {METRICS.map((item) => (
            <button key={item.key} type="button" aria-pressed={metric === item.key} onClick={() => setMetric(item.key)}>
              {item.label}
            </button>
          ))}
        </div>
        {overview.loading && !overview.data && <p className="admin-meta">불러오는 중...</p>}
        {overview.data && <DailyBars series={overview.data.series} metric={metric} label={metricLabel} />}
      </section>
      <section className="panel admin-section" aria-label="비로그인 라이브톡">
        <div className="admin-toolbar">
          <span>
            <h2>비로그인 라이브톡</h2>
            <small className="admin-meta">
              {stats.data?.anonymousChatEnabled ? "로그인하지 않은 사람도 메시지를 보낼 수 있습니다." : "로그인한 회원만 메시지를 보낼 수 있습니다."}
            </small>
          </span>
          <button
            className="admin-button"
            type="button"
            disabled={!stats.data || updatingChat}
            onClick={() => void toggleAnonymousChat()}
          >
            {stats.data?.anonymousChatEnabled ? "회원만 허용하기" : "비회원도 허용하기"}
          </button>
        </div>
      </section>
    </>
  );
}
