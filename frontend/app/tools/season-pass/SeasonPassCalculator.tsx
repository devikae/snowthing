"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  calculateSeasonPass,
  parseSeasonPassSearch,
  seasonPassSearch,
  SEASON_PASS_FIELDS,
  type SeasonPassInput,
} from "../../lib/seasonPass";
import ShareResult from "../ShareResult";
import { currentPass, RESORT_PASS_PRICES, SEASON_PASS_PRICE_CHECKED_AT } from "../../lib/seasonPassPrices";

const labels: Record<keyof SeasonPassInput, string> = {
  passPrice: "시즌권 결제액",
  ticketPrice: "리프트권 1회",
  passTripCost: "시즌권 이용 시 1회 부대비용",
  ticketTripCost: "개별권 이용 시 1회 부대비용",
  visited: "다녀온 횟수",
  planned: "예상 총 방문 (선택)",
};

// 화면에 보이는 입력 칸. 부대비용 두 칸은 계산에서 0으로 두고 보이지 않는다(공유 주소 형식은 그대로).
const VISIBLE_FIELDS = ["passPrice", "ticketPrice", "visited", "planned"] as const;

const initial: Record<keyof SeasonPassInput, string> = {
  passPrice: "",
  ticketPrice: "",
  passTripCost: "",
  ticketTripCost: "",
  visited: "0",
  planned: "",
};

const KST_TODAY = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" });

const won = (value: number) => `${Math.round(value).toLocaleString("ko-KR")}원`;

function sanitizeInput(key: keyof SeasonPassInput, raw: string): string {
  const digits = raw.replace(/[^0-9]/g, "");
  if (!digits) return "";

  // 가격 및 예상 방문 횟수는 0으로 시작할 수 없음 (맨 앞 0 원천 차단)
  if (key === "passPrice" || key === "ticketPrice" || key === "planned") {
    return digits.replace(/^0+/, "");
  }

  // 부대비용, 이미 방문한 횟수는 0 허용하되, '01', '0123' 등 앞자리 0 제거
  if (/^0+$/.test(digits)) {
    return "0";
  }
  return digits.replace(/^0+/, "");
}

function formatDisplay(key: keyof SeasonPassInput, digitsOnly: string): string {
  if (!digitsOnly) return "";
  if (key === "visited" || key === "planned") {
    return digitsOnly;
  }
  const n = Number(digitsOnly);
  return isNaN(n) ? "" : n.toLocaleString("ko-KR");
}

// 고른 리조트의 대략 가격으로 시즌권과 리프트권 칸을 채운다. 채운 뒤에도 직접 고칠 수 있다.
function resortFill(code: string) {
  const resort = RESORT_PASS_PRICES.find((item) => item.code === code);
  if (!resort) return null;
  const pass = currentPass(resort, KST_TODAY.format(new Date()));
  const fields: Partial<typeof initial> = {
    ticketPrice: formatDisplay("ticketPrice", String(resort.ticketPrice)),
  };
  if (pass.price !== null) fields.passPrice = formatDisplay("passPrice", String(pass.price));
  return {
    fields,
    note: `시즌권은 ${pass.label}, 리프트권은 ${resort.ticketLabel} 기준이에요. ${SEASON_PASS_PRICE_CHECKED_AT}에 조사한 대략값이라 실제와 다를 수 있어요. 고쳐 써도 돼요.`,
  };
}

export default function SeasonPassCalculator() {
  const [fields, setFields] = useState(initial);
  const [input, setInput] = useState<SeasonPassInput | null>(null);
  // 예상 방문 횟수를 비우고 계산하면 예상 관련 결과는 숨긴다.
  const [plannedGiven, setPlannedGiven] = useState(true);
  const [error, setError] = useState("");
  const [resortCode, setResortCode] = useState("");
  const [resortNote, setResortNote] = useState("");

  useEffect(() => {
    const restore = () => {
      // 공유 링크의 입력값이 먼저다. 입력값이 없고 ?resort=CODE만 있으면 그 리조트 가격을 채운다.
      const search = window.location.search;
      const params = new URLSearchParams(search);
      const sharedInputs =
        window.location.hash.slice(1) ||
        (SEASON_PASS_FIELDS.some((key) => params.has(key)) ? search : "");
      if (!sharedInputs) {
        const code = params.get("resort");
        const fill = code ? resortFill(code) : null;
        if (code && fill) {
          setResortCode(code);
          setFields((prev) => ({ ...prev, ...fill.fields }));
          setResortNote(fill.note);
        }
        return;
      }
      const restored = parseSeasonPassSearch(sharedInputs);
      if (!restored) {
        setError(
          "공유 링크의 입력값이 올바르지 않습니다. 직접 입력해 계산해 주세요.",
        );
        return;
      }
      setFields(
        Object.fromEntries(
          SEASON_PASS_FIELDS.map((key) => [
            key,
            formatDisplay(key, String(restored[key])),
          ]),
        ) as typeof initial,
      );
      setInput(restored);
    };
    const timer = window.setTimeout(restore, 0);
    window.addEventListener("hashchange", restore);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("hashchange", restore);
    };
  }, []);

  const result = input ? calculateSeasonPass(input) : null;

  const handleInputChange = (key: keyof SeasonPassInput, raw: string) => {
    const sanitized = sanitizeInput(key, raw);
    const formatted = formatDisplay(key, sanitized);
    setFields((prev) => ({ ...prev, [key]: formatted }));
    setInput(null);
  };

  const pickResort = (code: string) => {
    setResortCode(code);
    const fill = resortFill(code);
    setResortNote(fill?.note ?? "");
    if (!fill) return;
    setFields((prev) => ({ ...prev, ...fill.fields }));
    setInput(null);
    setError("");
  };

  const calculate = (event: React.FormEvent) => {
    event.preventDefault();
    const next = Object.fromEntries(
      SEASON_PASS_FIELDS.map((key) => {
        const raw = fields[key].replace(/,/g, "").trim();
        if (key === "passTripCost" || key === "ticketTripCost") return [key, 0];
        if (raw === "" && key === "visited") return [key, 0];
        if (raw === "" && key === "planned") return [key, -1];
        return [key, /^\d+$/.test(raw) ? Number(raw) : NaN];
      }),
    ) as unknown as SeasonPassInput;
    // 예상 방문 횟수가 없으면 방문한 횟수로 계산하고, 예상 결과는 보이지 않는다.
    const hasPlanned = next.planned !== -1;
    if (!hasPlanned) next.planned = next.visited;
    setPlannedGiven(hasPlanned);

    const computed = calculateSeasonPass(next);
    if (!computed.valid) {
      setError(computed.error);
      setInput(null);
      return;
    }
    setError("");
    setInput(next);
  };

  return (
    <div className="tool-page">
      <header className="tool-hero">
        <Link href="/tools" className="tool-back">← 라이더 도구</Link>
        <p className="tool-eyebrow">시즌권 본전 계산기</p>
        <h1>시즌권,<br />몇 번 가야 본전일까</h1>
        <p>결제한 금액과 리프트권 값만 넣으면<br />본전까지 남은 횟수가 나와요</p>
      </header>

      <div className="tool-card">
        <form onSubmit={calculate}>
          <label className="tool-row tool-row-select">
            <span>리조트 골라서 채우기</span>
            <select value={resortCode} onChange={(event) => pickResort(event.target.value)}>
              <option value="">직접 입력</option>
              {RESORT_PASS_PRICES.map((resort) => (
                <option key={resort.code} value={resort.code}>{resort.name}</option>
              ))}
            </select>
          </label>
          {resortNote && <small className="tool-resort-note">{resortNote}</small>}
          {VISIBLE_FIELDS.map((key) => (
            <label key={key} className="tool-row">
              <span>{labels[key]}</span>
              <span className="tool-row-value">
                <input
                  name={key}
                  inputMode="numeric"
                  pattern="[0-9,]*"
                  maxLength={key === "visited" || key === "planned" ? 3 : 13}
                  placeholder={key === "planned" ? "비워도 돼요" : "0"}
                  required={key === "passPrice" || key === "ticketPrice"}
                  value={fields[key]}
                  onFocus={(e) => e.target.select()}
                  onChange={(event) => handleInputChange(key, event.target.value)}
                />
                <b>{key === "visited" || key === "planned" ? "회" : "원"}</b>
              </span>
            </label>
          ))}
          <button type="submit" className="tool-primary">본전 계산하기</button>
          {error && <p role="alert" className="tool-error">{error}</p>}
        </form>

        <section className="tool-answer" aria-live="polite">
          <h2>계산 결과</h2>
          {result?.valid && input ? (
            <>
              <p className="tool-answer-head">
                {result.breakEvenVisits === null
                  ? "이 조건으로는 본전이 안 나와요"
                  : !result.staysBeneficial
                    ? "갈수록 리프트권이 더 싸요"
                    : result.breakEvenVisits === 0
                      ? "첫 방문부터 이득이에요"
                      : <><em>{result.breakEvenVisits}번째</em> 방문부터 본전</>}
              </p>
              {result.breakEvenVisits !== null && result.staysBeneficial && result.breakEvenVisits > 0 && (
                <VisitProgress target={result.breakEvenVisits} visited={input.visited} />
              )}
              <p className="tool-answer-note">
                {result.breakEvenVisits === null
                  ? "방문마다 드는 시즌권 쪽 비용이 리프트권 이상이에요. 조건을 다시 비교해 보세요."
                  : !result.staysBeneficial
                    ? "무료 시즌권이라 시작 비용은 같지만, 방문할수록 시즌권 쪽 비용이 더 커요."
                    : result.remainingVisits === 0
                      ? `지금 ${input.visited}번 갔으면 이미 본전을 넘었어요`
                      : input.visited > 0
                        ? `지금 ${input.visited}번 갔으면 ${result.remainingVisits}번 더 가면 돼요`
                        : `${result.breakEvenVisits}번 가면 시즌권 값을 다 뽑아요`}
              </p>
              {result.breakEvenVisits !== null && result.breakEvenVisits > 730 && (
                <p className="tool-answer-note">
                  계산상 {result.breakEvenVisits.toLocaleString()}회가 필요해 입력 가능한 방문 범위를 넘어요. 실제 방문 가능 횟수와 비교해 주세요.
                </p>
              )}
            </>
          ) : (
            <p className="tool-answer-empty">값을 넣고 계산하면 본전 횟수가 여기에 나와요</p>
          )}
        </section>
      </div>

      <Link href="/tools/season-pass-prices" className="tool-plus-link">
        + 26/27 시즌 스키장 {RESORT_PASS_PRICES.length}곳 시즌권, 리프트권 가격표도 있어요
      </Link>

      {result?.valid && input && (
        <section className="tool-more">
          <details>
            <summary>1회 비용과 방문 횟수별 비교</summary>
            <dl className="tool-metrics">
              <div>
                <dt>현재 시즌권 1회 비용</dt>
                <dd>{result.current.costPerVisit === null ? "첫 방문 전" : won(result.current.costPerVisit)}</dd>
              </div>
              {plannedGiven && (<>
                <div>
                  <dt>예상 {input.planned}회 방문 시 1회 비용</dt>
                  <dd>{result.planned.costPerVisit === null ? "방문 횟수를 입력해 주세요" : won(result.planned.costPerVisit)}</dd>
                </div>
                <div>
                  <dt>예상 리프트권 대비 차액</dt>
                  <dd>{won(Math.abs(result.planned.saving))} {result.planned.saving >= 0 ? "절약" : "추가"}</dd>
                </div>
              </>)}
            </dl>
            <div className="tool-table-wrap">
              <table>
                <caption>방문 횟수별 총비용 비교</caption>
                <thead>
                  <tr><th>방문</th><th>시즌권</th><th>리프트권</th></tr>
                </thead>
                <tbody>
                  {result.rows.map((row) => (
                    <tr key={row.visits}>
                      <th>{row.visits}회</th>
                      <td>{won(row.passTotal)}</td>
                      <td>{won(row.ticketsTotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
          <ShareResult
            key={seasonPassSearch(input)}
            path="/tools/season-pass"
            search={seasonPassSearch(input)}
            text={plannedGiven
              ? `예상 ${input.planned}회 방문, 시즌권 총비용 ${won(result.planned.passTotal)}. 내 조건으로 계산해 보세요.`
              : `시즌권 본전은 ${result.breakEvenVisits === null ? "회수 불가" : `${result.breakEvenVisits}회`}. 내 조건으로 계산해 보세요.`}
          />
        </section>
      )}

      <section className="tool-explanation">
        <h2>어떻게 계산하나요?</h2>
        <p>
          시즌권 총비용은 결제액 그대로이고, 리프트권 총비용은 방문 횟수에 리프트권 가격을 곱해요.
          두 비용이 같아지거나 시즌권 쪽이 싸지는 첫 방문을 본전으로 보여줘요.
        </p>
        <details>
          <summary>시즌권 종류나 사용 시간은 반영되나요?</summary>
          <p>
            시간대, 이용 가능 리조트, 횟수 제한, 환불 조건은 가격만으로 비교할 수 없어요. 이용 가능한 날짜와
            시간을 먼저 확인하고, 실제로 대체할 수 있는 리프트권 가격을 넣어 주세요.
          </p>
        </details>
        <details>
          <summary>예상 방문 횟수는 어떻게 잡으면 좋을까요?</summary>
          <p>
            작년 실제 방문 횟수나 이번 시즌 확정된 일정을 기준으로 적게 가는 경우와 많이 가는 경우를 각각
            계산해 보세요.
          </p>
        </details>
        <div className="tools-related">
          <Link href="/carpool">카풀과 동행에서 교통비 비교하기 →</Link>
          <Link href="/resort-reports">오늘의 설질 확인하기 →</Link>
        </div>
      </section>
    </div>
  );
}

// 본전까지의 방문을 동그라미로 보여준다. 다녀온 횟수만큼 채우고, 본전 회차는 강조색 테두리.
// 15회를 넘으면 동그라미가 너무 많아져 막대 하나로 바꾼다.
const MAX_DOTS = 15;

function VisitProgress({ target, visited }: { target: number; visited: number }) {
  const done = Math.min(visited, target);
  const label = `본전 ${target}회 중 ${done}회 다녀옴`;
  if (target > MAX_DOTS) {
    return (
      <div className="tool-visit-bar" role="img" aria-label={label}>
        <span style={{ width: `${(done / target) * 100}%` }} />
      </div>
    );
  }
  return (
    <ol className="tool-visit-dots" aria-label={label}>
      {Array.from({ length: target }, (_, i) => i + 1).map((n) => (
        <li key={n} className={[n <= done ? "is-done" : "", n === target ? "is-goal" : ""].join(" ").trim() || undefined}>
          {n}
        </li>
      ))}
    </ol>
  );
}
