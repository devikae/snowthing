"use client";
import { PageHeading } from "../../components/PageHeading";

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
  passPrice: "시즌권 실제 결제액",
  ticketPrice: "할인 리프트권 1회 가격",
  passTripCost: "시즌권 이용 시 1회 부대비용",
  ticketTripCost: "개별권 이용 시 1회 부대비용",
  visited: "이미 방문한 횟수",
  planned: "예상 총 방문 횟수",
};

const initial: Record<keyof SeasonPassInput, string> = {
  passPrice: "",
  ticketPrice: "",
  passTripCost: "",
  ticketTripCost: "",
  visited: "",
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

export default function SeasonPassCalculator() {
  const [fields, setFields] = useState(initial);
  const [input, setInput] = useState<SeasonPassInput | null>(null);
  const [error, setError] = useState("");
  const [resortCode, setResortCode] = useState("");
  const [resortNote, setResortNote] = useState("");

  useEffect(() => {
    const restore = () => {
      const sharedInputs =
        window.location.hash.slice(1) || window.location.search;
      if (!sharedInputs) return;
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

  // 고른 리조트의 대략 가격으로 시즌권과 리프트권 칸을 채운다. 채운 뒤에도 직접 고칠 수 있다.
  const pickResort = (code: string) => {
    setResortCode(code);
    const resort = RESORT_PASS_PRICES.find((item) => item.code === code);
    if (!resort) {
      setResortNote("");
      return;
    }
    const pass = currentPass(resort, KST_TODAY.format(new Date()));
    setFields((prev) => ({
      ...prev,
      passPrice: pass.price === null ? prev.passPrice : formatDisplay("passPrice", String(pass.price)),
      ticketPrice: formatDisplay("ticketPrice", String(resort.ticketPrice)),
    }));
    setResortNote(
      `시즌권은 ${pass.label}, 리프트권은 ${resort.ticketLabel} 기준이에요. ${SEASON_PASS_PRICE_CHECKED_AT}에 조사한 대략값이라 실제와 다를 수 있어요. 고쳐 써도 돼요.`,
    );
    setInput(null);
    setError("");
  };

  const calculate = (event: React.FormEvent) => {
    event.preventDefault();
    const next = Object.fromEntries(
      SEASON_PASS_FIELDS.map((key) => {
        const raw = fields[key].replace(/,/g, "").trim();
        if (
          raw === "" &&
          (key === "passTripCost" ||
            key === "ticketTripCost" ||
            key === "visited")
        ) {
          return [key, 0];
        }
        return [key, /^\d+$/.test(raw) ? Number(raw) : NaN];
      }),
    ) as unknown as SeasonPassInput;

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
    <>
      <PageHeading
        title="시즌권 본전 계산기"
        description="실제 시즌권 결제액과 평소 이용하는 리프트권 가격을 비교해 보세요."
        breadcrumb={<Link href="/tools">← 라이더 도구</Link>}
      />
      <div className="tool-workspace">
        <form className="tool-form" onSubmit={calculate}>
          <h2>내 시즌 비용</h2>
          <div className="tool-fields">
            <label>
              리조트 골라서 가격 채우기
              <select value={resortCode} onChange={(event) => pickResort(event.target.value)}>
                <option value="">직접 입력</option>
                {RESORT_PASS_PRICES.map((resort) => (
                  <option key={resort.code} value={resort.code}>{resort.name}</option>
                ))}
              </select>
              {resortNote && <small className="tool-resort-note">{resortNote}</small>}
            </label>
            {SEASON_PASS_FIELDS.map((key) => (
              <label key={key}>
                {labels[key]}
                <div className="tool-unit-input">
                  <input
                    name={key}
                    inputMode="numeric"
                    pattern="[0-9,]*"
                    maxLength={key === "visited" || key === "planned" ? 3 : 13}
                    placeholder={
                      key === "passTripCost" ||
                      key === "ticketTripCost" ||
                      key === "visited"
                        ? "0"
                        : key === "planned"
                          ? "예: 15"
                          : "0"
                    }
                    required={
                      key === "passPrice" ||
                      key === "ticketPrice" ||
                      key === "planned"
                    }
                    value={fields[key]}
                    onFocus={(e) => e.target.select()}
                    onChange={(event) =>
                      handleInputChange(key, event.target.value)
                    }
                  />
                  <span>
                    {key === "visited" || key === "planned" ? "회" : "원"}
                  </span>
                </div>
              </label>
            ))}
          </div>
          <p className="tool-help">
            부대비용에는 교통·주차·렌털 등 방문마다 드는 비용을 넣으세요. 시즌권
            혜택으로 절약하는 비용이 없다면 양쪽에 같은 값을 입력하면 됩니다.
          </p>
          <button type="submit" className="tool-primary">
            본전과 방문 목표 계산
          </button>
          {error && (
            <p role="alert" className="tool-error">
              {error}
            </p>
          )}
        </form>
        <section className="tool-result" aria-live="polite">
          <h2>계산 결과</h2>
          {result?.valid && input ? (
            <>
              <div className="tool-key-result">
                <span>시즌권 비용 회수 기준</span>
                <strong>
                  {result.breakEvenVisits === null
                    ? "회수 불가"
                    : `${result.breakEvenVisits}회`}
                </strong>
                <p>
                  {result.breakEvenVisits === null
                    ? "방문마다 드는 시즌권 쪽 비용이 개별권 비용 이상입니다. 조건을 다시 비교해 보세요."
                    : !result.staysBeneficial
                      ? "무료 시즌권이라 시작 비용은 같지만, 방문할수록 시즌권 쪽 비용이 더 큽니다."
                      : result.remainingVisits === 0
                        ? "입력한 방문 횟수로 비용 회수 기준에 도달했습니다."
                        : `현재까지 ${input.visited}회 방문했다면 앞으로 ${result.remainingVisits}회 더 방문하면 됩니다.`}
                </p>
              </div>
              <dl className="tool-metrics">
                <div>
                  <dt>현재 시즌권 1회 비용</dt>
                  <dd>
                    {result.current.costPerVisit === null
                      ? "첫 방문 전"
                      : won(result.current.costPerVisit)}
                  </dd>
                </div>
                <div>
                  <dt>예상 {input.planned}회 방문 시 1회 비용</dt>
                  <dd>
                    {result.planned.costPerVisit === null
                      ? "방문 횟수를 입력해 주세요"
                      : won(result.planned.costPerVisit)}
                  </dd>
                </div>
                <div>
                  <dt>예상 개별권 대비 차액</dt>
                  <dd>
                    {won(Math.abs(result.planned.saving))}{" "}
                    {result.planned.saving >= 0 ? "절약" : "추가"}
                  </dd>
                </div>
              </dl>
              {result.breakEvenVisits !== null &&
                result.breakEvenVisits > 730 && (
                  <p>
                    계산상 {result.breakEvenVisits.toLocaleString()}회가 필요해
                    입력 가능한 방문 범위를 넘습니다. 실제 방문 가능 횟수와
                    비교해 주세요.
                  </p>
                )}
              <div className="tool-table-wrap">
                <table>
                  <caption>방문 횟수별 총비용 비교</caption>
                  <thead>
                    <tr>
                      <th>방문</th>
                      <th>시즌권</th>
                      <th>개별권</th>
                    </tr>
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
              <ShareResult
                key={seasonPassSearch(input)}
                path="/tools/season-pass"
                search={seasonPassSearch(input)}
                text={`예상 ${input.planned}회 방문, 시즌권 총비용 ${won(result.planned.passTotal)}. 내 조건으로 계산해 보세요.`}
              />
            </>
          ) : (
            <div className="tool-empty">
              <p>
                내 가격과 방문 계획을 입력하면 본전 횟수와 비용 비교표가 여기에
                표시됩니다.
              </p>
            </div>
          )}
        </section>
      </div>
      <section className="tool-explanation">
        <h2>어떻게 계산하나요?</h2>
        <p>
          시즌권 총비용은 결제액에 방문별 부대비용을 더합니다. 개별권 총비용은
          방문 횟수에 리프트권과 부대비용을 곱합니다. 두 비용이 같아지거나
          시즌권 쪽이 저렴해지는 첫 방문 횟수를 본전으로 표시합니다.
        </p>
        <details>
          <summary>시즌권 종류나 사용 시간은 반영되나요?</summary>
          <p>
            시간대·이용 가능 리조트·횟수 제한·환불 조건은 가격만으로 비교할 수
            없습니다. 이용 가능한 날짜와 시간을 먼저 확인하고, 실제로 대체할 수
            있는 리프트권 가격을 입력해 주세요.
          </p>
        </details>
        <details>
          <summary>예상 방문 횟수는 어떻게 잡으면 좋을까요?</summary>
          <p>
            작년 실제 방문 횟수나 이번 시즌 확정된 일정을 기준으로 적게 가는
            경우와 많이 가는 경우를 각각 계산해 보세요.
          </p>
        </details>
        <div className="tools-related">
          <Link href="/carpool">카풀·동행으로 교통비 비교하기 →</Link>
          <Link href="/resort-reports">오늘의 설질 확인하기 →</Link>
        </div>
      </section>
    </>
  );
}
