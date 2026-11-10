"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { getResortReportDisplayName } from "../lib/resortReports";
import { fetchViewer } from "../lib/viewer";
import { SeasonLog, SeasonVisit, shareUrl } from "./types";

interface ResortMaster {
  id: number;
  name: string;
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 시즌 달력. 날짜 키는 YYYY-MM-DD 문자열이고 계산은 UTC로 해서 기기 시간대와 무관하다.
function seasonMonths(startDate: string, endDate: string) {
  const [startYear, startMonth] = startDate.split("-").map(Number);
  const [endYear, endMonth] = endDate.split("-").map(Number);
  const months: { key: string; label: string; blanks: number; days: string[] }[] = [];
  for (let index = 0; ; index++) {
    const year = startYear + Math.floor((startMonth - 1 + index) / 12);
    const month = ((startMonth - 1 + index) % 12) + 1;
    if (year > endYear || (year === endYear && month > endMonth)) break;
    const count = new Date(Date.UTC(year, month, 0)).getUTCDate();
    const prefix = `${year}-${String(month).padStart(2, "0")}`;
    months.push({
      key: prefix,
      label: `${year}년 ${month}월`,
      blanks: new Date(Date.UTC(year, month - 1, 1)).getUTCDay(),
      days: Array.from({ length: count }, (_, index) => `${prefix}-${String(index + 1).padStart(2, "0")}`),
    });
  }
  return months;
}

function koreanDate(date: string) {
  const [, month, day] = date.split("-").map(Number);
  return `${month}월 ${day}일`;
}

async function failureMessage(response: Response, fallback: string) {
  const data = await response.json().catch(() => null);
  return typeof data?.message === "string" ? data.message : fallback;
}

export function SeasonLogApp() {
  const [loggedIn, setLoggedIn] = useState<boolean | null>(null);
  const [season, setSeason] = useState<number | undefined>();
  const [log, setLog] = useState<SeasonLog | null>(null);
  const [loadError, setLoadError] = useState("");
  const [resorts, setResorts] = useState<ResortMaster[]>([]);
  const [resortId, setResortId] = useState("");
  const [otherDate, setOtherDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    void fetchViewer().then((viewer) => setLoggedIn(viewer != null));
    void fetch(API_ENDPOINTS.master.resorts, { credentials: "include" })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: ResortMaster[]) => setResorts(Array.isArray(data) ? data : []))
      .catch(() => setResorts([]));
  }, []);

  const load = useCallback(async () => {
    const response = await fetch(API_ENDPOINTS.seasonLogs.me(season), { credentials: "include", cache: "no-store" });
    if (!response.ok) throw new Error(await failureMessage(response, "시즌 기록을 불러오지 못했습니다."));
    return (await response.json()) as SeasonLog;
  }, [season]);

  useEffect(() => {
    if (!loggedIn) return;
    let cancelled = false;
    load()
      .then((data) => {
        if (cancelled) return;
        setLog(data);
        setLoadError("");
      })
      .catch((error: unknown) => {
        if (!cancelled) setLoadError(error instanceof Error && !(error instanceof TypeError) ? error.message : "시즌 기록을 불러오지 못했습니다.");
      });
    return () => {
      cancelled = true;
    };
  }, [loggedIn, load]);

  const reload = async () => setLog(await load());

  const visitsByDate = useMemo(() => new Map<string, SeasonVisit>((log?.visits ?? []).map((visit) => [visit.visitDate, visit])), [log]);
  const months = useMemo(() => (log ? seasonMonths(log.season.startDate, log.season.endDate) : []), [log]);
  // 리조트를 고르지 않았으면 이번 시즌에 가장 많이 간 곳을 먼저 고른다.
  const chosenResortId = resortId || (log?.summary.topResort ? String(log.summary.topResort.resortId) : "");

  if (loggedIn === false) {
    return <>
      <PageHeading title="내 시즌 기록" description="리조트를 다녀온 날을 남기면 시즌 결산을 볼 수 있어요." />
      <section className="season-log-login"><p>시즌 기록은 로그인하면 쓸 수 있어요.</p><div><Link href={`/login?returnUrl=${encodeURIComponent("/season-log")}`} className="snow-btn-primary">로그인</Link><Link href="/signup" className="snow-btn-secondary">회원가입</Link></div></section>
    </>;
  }

  const today = log?.today ?? "";
  const todayInSeason = log != null && today >= log.season.startDate && today <= log.season.endDate;
  const recordedToday = visitsByDate.get(today);

  const addVisit = async (visitDate: string) => {
    if (!chosenResortId) {
      setNotice("리조트를 골라 주세요.");
      return;
    }
    setBusy(true);
    setNotice("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.seasonLogs.visits, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ visitDate, resortId: Number(chosenResortId) }),
      });
      if (!response.ok) {
        setNotice(await failureMessage(response, "기록하지 못했습니다."));
        return;
      }
      const saved: SeasonVisit = await response.json();
      setNotice(`${koreanDate(saved.visitDate)} ${saved.resortName} 기록했어요.`);
      setOtherDate("");
      // 다른 시즌 날짜를 남겼으면 그 시즌으로 옮겨 보여 준다.
      const [year, month] = saved.visitDate.split("-").map(Number);
      const savedSeason = month >= 11 ? year : year - 1;
      if (log && savedSeason !== log.season.startYear) setSeason(savedSeason);
      else await reload();
    } catch {
      setNotice("기록하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const deleteVisit = async (visit: SeasonVisit) => {
    if (!window.confirm(`${koreanDate(visit.visitDate)} ${visit.resortName} 기록을 지울까요?`)) return;
    setBusy(true);
    setNotice("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.seasonLogs.visit(visit.visitDate), { method: "DELETE" });
      if (!response.ok && response.status !== 404) {
        setNotice(await failureMessage(response, "기록을 지우지 못했습니다."));
        return;
      }
      await reload();
    } catch {
      setNotice("기록을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const setShare = async (enabled: boolean) => {
    if (!log) return;
    if (!enabled && !window.confirm("공유를 끄면 지금 링크는 다시 열리지 않아요. 끌까요?")) return;
    setBusy(true);
    setNotice("");
    try {
      const response = await csrfFetch(API_ENDPOINTS.seasonLogs.share(log.season.startYear), { method: enabled ? "PUT" : "DELETE" });
      if (!response.ok) {
        setNotice(await failureMessage(response, "공유 설정을 바꾸지 못했습니다."));
        return;
      }
      await reload();
      setNotice(enabled ? "공유 링크를 만들었어요." : "공유를 껐어요.");
    } catch {
      setNotice("공유 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  };

  const shareLink = async () => {
    if (!log?.shareToken) return;
    const url = shareUrl(window.location.origin, log.shareToken);
    try {
      if (navigator.share) await navigator.share({ title: `${log.season.label} 결산`, url });
      else {
        await navigator.clipboard.writeText(url);
        setNotice("링크를 복사했어요.");
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) setNotice("아래 링크를 선택해서 복사해 주세요.");
    }
  };

  const summary = log?.summary;
  const maxDays = summary?.resorts[0]?.days ?? 1;

  return <>
    <PageHeading title="내 시즌 기록" description="리조트를 다녀온 날을 하루에 한 번 남기면 시즌 결산을 만들어 드려요."
      actions={log && <select className="season-log-select" aria-label="시즌 선택" value={log.season.startYear} onChange={(event) => setSeason(Number(event.target.value))}>
        {[log.latestSeasonStartYear, log.latestSeasonStartYear - 1].map((year) => <option key={year} value={year}>{`${year}-${String((year + 1) % 100).padStart(2, "0")} 시즌`}</option>)}
      </select>} />

    {loadError && <p className="tool-error" role="alert">{loadError}</p>}
    {!log && !loadError && <p className="season-log-loading">불러오는 중</p>}

    {log && summary && <div className="season-log-layout">
      <section className="season-log-record" aria-labelledby="season-log-record-title">
        <h2 id="season-log-record-title">기록하기</h2>
        <label>리조트
          <select value={chosenResortId} onChange={(event) => setResortId(event.target.value)} disabled={busy}>
            <option value="">리조트 선택</option>
            {resorts.map((resort) => <option key={resort.id} value={resort.id}>{resort.name}</option>)}
          </select>
        </label>
        {todayInSeason ? (
          recordedToday
            ? <p className="season-log-today-done">오늘은 {recordedToday.resortName}에 다녀온 것으로 기록했어요.</p>
            : <button type="button" className="tool-primary" disabled={busy} onClick={() => void addVisit(today)}>오늘 다녀왔어요</button>
        ) : <p className="season-log-hint">{today < log.season.startDate ? "시즌은 11월 1일부터 기록할 수 있어요." : "이 시즌은 끝났어요. 빠뜨린 날만 아래에서 남겨 주세요."}</p>}
        <div className="season-log-other">
          <label>다른 날 남기기
            <input type="date" value={otherDate} min={log.recordableFrom} max={today} onChange={(event) => setOtherDate(event.target.value)} disabled={busy} />
          </label>
          <button type="button" disabled={busy || !otherDate} onClick={() => void addVisit(otherDate)}>남기기</button>
        </div>
        <p className="season-log-hint">하루에 한 곳만 남길 수 있어요. 기간은 11월 1일부터 4월 30일까지이고, 지난 시즌까지 채울 수 있어요.</p>
        {notice && <p role="status" className="season-log-notice">{notice}</p>}
      </section>

      <section className="season-log-summary" aria-labelledby="season-log-summary-title">
        <h2 id="season-log-summary-title">{log.season.label} 결산</h2>
        <div className="tool-key-result"><span>탄 날</span><strong>{summary.totalDays}일</strong></div>
        {summary.totalDays > 0 ? <>
          <dl className="season-log-facts">
            <div><dt>가장 많이 간 곳</dt><dd>{summary.topResort?.resortName} {summary.topResort?.days}일</dd></div>
            <div><dt>첫 날</dt><dd>{summary.firstDate && koreanDate(summary.firstDate)}</dd></div>
            <div><dt>마지막 날</dt><dd>{summary.lastDate && koreanDate(summary.lastDate)}</dd></div>
          </dl>
          <ul className="season-log-bars">
            {summary.resorts.map((resort) => <li key={resort.resortId}><span>{resort.resortName}</span><span className="season-log-bar" style={{ width: `${Math.max(6, (resort.days / maxDays) * 100)}%` }} /><strong>{resort.days}일</strong></li>)}
          </ul>
        </> : <p className="season-log-hint">아직 기록이 없어요. 다녀온 날을 남기면 여기에 결산이 쌓여요.</p>}

        <div className="season-log-share">
          <h3>결산 공유</h3>
          {log.shareToken ? <>
            <p>링크를 받은 사람은 닉네임, 탄 날 수, 리조트별 일수를 볼 수 있어요. 날짜는 보이지 않아요.</p>
            <input readOnly aria-label="공유 링크" value={typeof window === "undefined" ? "" : shareUrl(window.location.origin, log.shareToken)} onFocus={(event) => event.target.select()} />
            <div className="season-log-share-actions">
              <button type="button" className="tool-primary" disabled={busy} onClick={() => void shareLink()}>링크 공유</button>
              <Link href={`/season-log/s/${log.shareToken}`}>공유 화면 보기</Link>
              <button type="button" disabled={busy} onClick={() => void setShare(false)}>공유 끄기</button>
            </div>
          </> : <>
            <p>공유는 꺼져 있어요. 켜면 닉네임, 탄 날 수, 리조트별 일수가 링크로 공개돼요. 날짜는 공개하지 않아요.</p>
            <button type="button" disabled={busy} onClick={() => void setShare(true)}>공유 링크 만들기</button>
          </>}
        </div>
      </section>

      <section className="season-log-calendar" aria-labelledby="season-log-calendar-title">
        <h2 id="season-log-calendar-title">달력</h2>
        <p className="season-log-hint">기록한 날을 누르면 지울 수 있어요.</p>
        <div className="season-log-months">
          {months.map((month) => <div key={month.key} className="season-log-month">
            <h3>{month.label}</h3>
            <div className="season-log-grid" role="grid" aria-label={month.label}>
              {WEEKDAYS.map((weekday) => <span key={weekday} className="season-log-weekday" aria-hidden="true">{weekday}</span>)}
              {Array.from({ length: month.blanks }, (_, index) => <span key={`blank-${index}`} aria-hidden="true" />)}
              {month.days.map((day) => {
                const visit = visitsByDate.get(day);
                const dayNumber = Number(day.slice(8));
                if (!visit) return <span key={day} className={`season-log-day${day === today ? " today" : ""}`}>{dayNumber}</span>;
                return <button key={day} type="button" className="season-log-day visited" disabled={busy} title={`${koreanDate(day)} ${visit.resortName}`} aria-label={`${koreanDate(day)} ${visit.resortName} 기록 지우기`} onClick={() => void deleteVisit(visit)}>
                  {dayNumber}<small>{getResortReportDisplayName(visit.resortName)}</small>
                </button>;
              })}
            </div>
          </div>)}
        </div>
      </section>
    </div>}
  </>;
}
