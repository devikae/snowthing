"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { calculateSkiSize, parseSkiSearch, skiSearch, SKI_SOURCE, SKI_SOURCE_CHECKED, type SkiInput } from "../../lib/skiSize";
import ShareResult from "../ShareResult";

const bandGuide = {
  short: { label: "Short", use: "숏턴, 회전", focus: "초중급, 카빙 스키 등, 반응성 중심" },
  middle: { label: "Mid", use: "올라운드", focus: "정설 사면, 다양한 턴 등, 균형 중심" },
  long: { label: "Long", use: "롱턴, 대회전", focus: "고속, 비정설 사면 등, 안정성 중심" },
};

const buildNote = {
  light: "키에 비해 가벼운 편이라 Short 쪽부터 보세요",
  average: "키에 맞는 체중이라 Mid 쪽부터 보세요",
  heavy: "키에 비해 무거운 편이라 Long 쪽부터 보세요",
};

export default function SkiSizeCalculator() {
  const [height, setHeight] = useState(""); const [weight, setWeight] = useState("");
  const [input, setInput] = useState<SkiInput | null>(null); const [error, setError] = useState("");
  useEffect(() => {
    const restore = () => {
      const sharedInputs = window.location.hash.slice(1) || window.location.search;
      if (!sharedInputs) return;
      const restored = parseSkiSearch(sharedInputs);
      if (!restored) { setError("공유 링크의 입력값을 확인할 수 없습니다. 직접 입력해 주세요."); return; }
      setHeight(String(restored.height)); setWeight(String(restored.weight)); setInput(restored);
    };
    const timer = window.setTimeout(restore, 0);
    window.addEventListener("hashchange", restore);
    return () => { window.clearTimeout(timer); window.removeEventListener("hashchange", restore); };
  }, []);
  const result = input ? calculateSkiSize(input) : null;
  const range = result?.valid ? `${result.minimum}~${result.maximum}cm` : "";
  const calculate = (event: React.FormEvent) => {
    event.preventDefault();
    const next: SkiInput = { height: height.trim() ? Number(height) : NaN, weight: weight.trim() ? Number(weight) : NaN };
    const computed = calculateSkiSize(next);
    if (!computed.valid) { setError(computed.error); setInput(null); return; }
    setInput(next); setError("");
  };
  return <div className="tool-page">
    <header className="tool-hero">
      <Link href="/tools" className="tool-back">← 라이더 도구</Link>
      <p className="tool-eyebrow">스키 사이즈 계산기</p>
      <h1>내 스키는<br />몇 cm가 맞을까</h1>
      <p>키와 체중만 넣으면<br />제조사 사이즈표 기준 참고 길이를 알려줘요</p>
    </header>

    <div className="tool-card">
      <form onSubmit={calculate}>
        <div className="tool-tiles">
          <label className="tool-tile">
            <span>키</span>
            <span className="tool-tile-value"><input name="height" type="number" inputMode="numeric" min={122} max={210} step={1} placeholder="0" value={height} onChange={event => { setHeight(event.target.value); setInput(null); }} required /><b>cm</b></span>
          </label>
          <label className="tool-tile">
            <span>체중</span>
            <span className="tool-tile-value"><input name="weight" type="number" inputMode="decimal" min={30} max={200} step="0.1" placeholder="0" value={weight} onChange={event => { setWeight(event.target.value); setInput(null); }} required /><b>kg</b></span>
          </label>
        </div>
        <small className="tool-tile-help">성인 사이즈표를 사용해요. 체중은 길이를 바꾸지 않고, 세 구간 중 어디부터 볼지 고르는 데만 써요.</small>
        <button className="tool-primary">참고 사이즈 확인</button>
        {error && <p className="tool-error" role="alert">{error}</p>}
      </form>

      <section className="tool-answer" aria-live="polite">
        {input && result?.valid ? <>
          <div className="tool-answer-block">
            <h2>제조사 사이즈표 기준 참고 범위</h2>
            <strong>{range}</strong>
            <p>키 {result.openEnded ? "188cm 이상" : `${result.chartHeight}cm`} 줄의 범위예요</p>
          </div>
          <ol className="tool-bands" aria-label="길이별 선택 방향">{result.bands.map(band => {
            const guide = bandGuide[band.position];
            const picked = band.position === result.highlight;
            return <li key={band.position} className={picked ? "is-picked" : undefined} aria-current={picked ? "true" : undefined}><small>{guide.label}</small><strong>{band.minimum === band.maximum ? band.minimum : `${band.minimum}~${band.maximum}`}cm</strong><b>{guide.use}</b><span className="tool-band-focus">{guide.focus}</span></li>;
          })}</ol>
          <p className="tool-answer-note">{buildNote[result.build]} (BMI {result.bmi})</p>
        </> : <><h2>계산 결과</h2><p className="tool-answer-empty">키와 체중을 넣으면 참고 길이가 여기에 나와요</p></>}
      </section>
    </div>

    {input && result?.valid && <section className="tool-more">
      <ul className="tool-notes">
        <li>짧을수록 돌리기 쉽고, 길수록 속도와 긴 턴에서 안정적이에요. 회전, 대회전 전용 규격은 아니에요.</li>
        <li>체격 판단은 BMI 기준이라 근육량이 많으면 실제보다 무겁게 잡힐 수 있어요.</li>
        <li>실력이 상급이면 Long, 입문이면 Short 쪽도 함께 비교해 보세요.</li>
      </ul>
      <ShareResult key={skiSearch(input)} path="/tools/ski-size" search={skiSearch(input)} text={`스키 참고 범위 ${range}. 내 조건으로 확인해 보세요.`} />
    </section>}

    <section className="tool-explanation"><h2>어떤 자료를 사용하나요?</h2><p>Nordica의 성인 스키 사이즈표에서 키에 가장 가까운 줄의 범위를 보여줘요. 두 줄의 한가운데면 짧은 쪽 줄을 써요. 같은 자료는 초중급, 짧은 턴, 카빙 스키, 키에 비해 가벼운 체중이면 짧게, 상급, 빠른 속도와 긴 턴, 비정설 사면, 로커가 많은 스키, 키에 비해 무거운 체중이면 길게 고르라고 안내해요.</p><p>Short, Mid, Long은 이 범위를 셋으로 나눈 선택 가이드예요. 제조사가 정한 구간이나 실제 판매 사이즈를 뜻하지 않아요. 키에 비해 가볍고 무거운 기준은 자료에 숫자가 없어서, BMI 18.5 미만을 가벼움, 25 이상을 무거움으로 봐요.</p><div className="tools-related"><a href={SKI_SOURCE} target="_blank" rel="noreferrer">Nordica 공식 스키 사이즈 안내 ↗</a><Link href="/tools/deck-size">데크 사이즈 계산기 →</Link></div><p>자료 확인: {SKI_SOURCE_CHECKED}. 구매 전 해당 모델의 사이즈별 성격과 재고 길이를 확인해 주세요.</p></section>
  </div>;
}
