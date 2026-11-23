"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { calculateDeckSize, checkModelWeight, deckQuestionDraft, deckSearch, parseDeckSearch, DECK_SOURCE, DECK_SOURCE_CHECKED, BOOT_SIZE_SOURCE, type DeckInput } from "../../lib/deckSize";
import ShareResult from "../ShareResult";
import { DECK_QUESTION_KEY } from "../../lib/toolQuestionDraft";

const bandGuide = {
  short: { label: "Short", use: "그라운드 트릭", focus: "회전, 프레스 등, 반응성 중심" },
  middle: { label: "Mid", use: "올라운드, 파크", focus: "슬로프, 킥커 등, 균형 중심" },
  long: { label: "Long", use: "라이딩", focus: "카빙, 고속 라이딩 등, 안정성 중심" },
};
export default function DeckSizeCalculator() {
  const [weight, setWeight] = useState(""); const [boot, setBoot] = useState("");
  const [input, setInput] = useState<DeckInput | null>(null); const [error, setError] = useState("");
  const [modelMin, setModelMin] = useState(""); const [modelMax, setModelMax] = useState("");
  const [draftVisible, setDraftVisible] = useState(false); const [draftNotice, setDraftNotice] = useState("");
  useEffect(() => {
    const restore = () => {
      const sharedInputs = window.location.hash.slice(1) || window.location.search;
      if (!sharedInputs) return;
      const restored = parseDeckSearch(sharedInputs);
      if (!restored) { setError("공유 링크의 입력값을 확인할 수 없습니다. 직접 입력해 주세요."); return; }
      setWeight(String(restored.weight)); setBoot(String(restored.bootMm)); setInput(restored);
    };
    const timer = window.setTimeout(restore, 0);
    window.addEventListener("hashchange", restore);
    return () => { window.clearTimeout(timer); window.removeEventListener("hashchange", restore); };
  }, []);
  const result = input ? calculateDeckSize(input) : null;
  const range = result?.valid ? `${result.minimum}${result.maximum === null ? "cm 이상" : `~${result.maximum}cm`}` : "";
  const draft = input && range ? deckQuestionDraft(input, range) : "";
  const model = input && modelMin && modelMax ? checkModelWeight(input.weight, Number(modelMin), Number(modelMax)) : null;
  const invalidate = () => { setInput(null); setDraftVisible(false); setDraftNotice(""); };
  const calculate = (event: React.FormEvent) => {
    event.preventDefault();
    const next: DeckInput = { weight: weight.trim() ? Number(weight) : NaN, bootMm: boot.trim() ? Number(boot) : NaN };
    const computed = calculateDeckSize(next);
    if (!computed.valid) { setError(computed.error); setInput(null); return; }
    setInput(next); setError(""); setDraftVisible(false); setDraftNotice("");
  };
  const prepareQuestion = (event: React.MouseEvent<HTMLAnchorElement>) => {
    try { sessionStorage.setItem(DECK_QUESTION_KEY, JSON.stringify({ title: "제 조건에 맞는 데크 사이즈가 궁금합니다", content: draft, savedAt: Date.now() })); }
    catch { event.preventDefault(); setDraftNotice("이 브라우저에서는 초안을 저장할 수 없습니다. 아래 내용을 복사해 글쓰기에 붙여 넣어 주세요."); }
  };
  const copyDraft = async () => {
    try { await navigator.clipboard.writeText(draft); setDraftNotice("초안을 복사했습니다."); }
    catch { setDraftNotice("위 초안을 선택해 복사해 주세요."); }
  };
  return <div className="tool-page">
    <header className="tool-hero">
      <Link href="/tools" className="tool-back">← 라이더 도구</Link>
      <p className="tool-eyebrow">데크 사이즈 계산기</p>
      <h1>내 데크는<br />몇 cm가 맞을까</h1>
      <p>체중과 부츠 사이즈만 넣으면<br />제조사 체중표 기준 참고 길이를 알려줘요</p>
    </header>

    <div className="tool-card">
      <form onSubmit={calculate}>
        <div className="tool-tiles">
          <label className="tool-tile">
            <span>체중</span>
            <span className="tool-tile-value"><input name="weight" type="number" inputMode="decimal" min={34} max={200} step="0.1" placeholder="0" value={weight} onChange={event => { setWeight(event.target.value); invalidate(); }} required /><b>kg</b></span>
          </label>
          <label className="tool-tile">
            <span>부츠 사이즈</span>
            <span className="tool-tile-value"><input name="bootMm" type="number" inputMode="numeric" min={210} max={340} step={5} placeholder="270" value={boot} onChange={event => { setBoot(event.target.value); invalidate(); }} required /><b>mm</b></span>
          </label>
        </div>
        <small className="tool-tile-help">부츠에 적힌 한국 사이즈(270, 275처럼)를 넣어 주세요. 발 실측 길이와는 달라요.</small>
        <button className="tool-primary">참고 사이즈 확인</button>
        {error && <p className="tool-error" role="alert">{error}</p>}
      </form>

      <section className="tool-answer" aria-live="polite">
        {input && result?.valid ? <>
          <div className="tool-answer-block">
            <h2>제조사 체중표 기준 참고 범위</h2>
            <strong>{range}</strong>
            <p>{result.lookupWeight}kg에 겹치는 {result.rowsMatched}개 구간의 범위예요</p>
          </div>
          {result.bands.length > 0 ? <ol className="tool-bands" aria-label="길이별 선택 방향">{result.bands.map(band => {
            const guide = bandGuide[band.position];
            return <li key={band.position}><small>{guide.label}</small><strong>{band.minimum === band.maximum ? band.minimum : `${band.minimum}~${band.maximum}`}cm</strong><b>{guide.use}</b><span className="tool-band-focus">{guide.focus}</span></li>;
          })}</ol> : <p className="tool-answer-note">참고표의 상한이 없거나 범위가 좁아 길이 구간을 나누지 않았어요.</p>}
        </> : <><h2>계산 결과</h2><p className="tool-answer-empty">체중과 부츠 사이즈를 넣으면 참고 길이가 여기에 나와요</p></>}
      </section>
    </div>

    {input && result?.valid && <section className="tool-more">
      <p className="tool-help">짧을수록 조작성, 길수록 안정성에 무게를 둔 선택 방향이에요. 성향별 전용 규격은 아니고, 파크도 기물은 Short, 큰 점프는 Long을 함께 비교해 보세요. 모델별 권장 체중을 먼저 확인해 주세요.</p>
      <ShareResult key={deckSearch(input)} path="/tools/deck-size" search={deckSearch(input)} text={`데크 참고 범위 ${range}. 내 조건으로 확인해 보세요.`} />
    </section>}

    {input && result?.valid && <section className="tool-explanation"><h2>고민 중인 모델과 비교하기</h2><p>제품 상세 페이지의 해당 사이즈, 연식에 적힌 권장 체중을 입력하세요. 이 비교는 길이, 플렉스, 폭의 최종 적합 판정을 대신하지 않습니다.</p><div className="tool-model-fields"><label>모델 권장 최소 체중 (kg)<input type="number" min={1} max={300} step="0.1" value={modelMin} onChange={event => setModelMin(event.target.value)} /></label><label>모델 권장 최대 체중 (kg)<input type="number" min={1} max={300} step="0.1" value={modelMax} onChange={event => setModelMax(event.target.value)} /></label></div>
      {model && <p role="status">{!model.valid ? "모델 권장 체중의 최소, 최대 값을 확인해 주세요." : model.withinRange ? "입력한 체중은 이 모델, 사이즈의 권장 체중 안에 있습니다. 폭과 용도도 확인해 주세요." : model.position === "below" ? "입력한 체중이 권장 최소보다 낮습니다. 예상보다 단단하게 느껴질 수 있어 다른 사이즈도 비교해 보세요." : "입력한 체중이 권장 최대보다 높습니다. 다른 사이즈와 해당 모델 제조사 안내를 확인해 주세요."}</p>}
      <h2>라이더에게 물어보기</h2><p>질문에 필요한 조건을 정리해 드립니다. 초안을 공개하기 전에 포함된 체중, 부츠 정보와 내용을 확인하세요.</p><button type="button" onClick={() => setDraftVisible(value => !value)}>{draftVisible ? "초안 접기" : "질문 초안 확인"}</button>
      {draftVisible && <div className="tool-question-draft"><label>질문 초안<textarea readOnly value={draft} rows={12} onFocus={event => event.target.select()} /></label><div className="tools-related"><button type="button" onClick={() => void copyDraft()}>초안 복사</button><Link href="/posts/create?category=QNA&draft=deck" onClick={prepareQuestion}>이 초안으로 장비 질문 쓰기 →</Link></div>{draftNotice && <p role="status">{draftNotice}</p>}</div>}
    </section>}
    <section className="tool-explanation"><h2>어떤 자료를 사용하나요?</h2><p>Jones의 성인 체중, 부츠 참고표를 바탕으로 계산합니다. 체중은 표 조회를 위해 가까운 정수 kg로 반올림하고, 키나 성별만으로 길이를 정하지 않아요. Short, Mid, Long은 이 범위를 셋으로 나눈 선택 가이드입니다. 제조사가 정한 성향별 길이 경계나 실제 판매 사이즈를 뜻하지 않습니다.</p><div className="tools-related"><a href={BOOT_SIZE_SOURCE} target="_blank" rel="noreferrer">Burton 공식 부츠 사이즈표 ↗</a><a href={DECK_SOURCE} target="_blank" rel="noreferrer">Jones 공식 사이징 가이드 ↗</a><a href="https://www.burton.com/en-us/blogs/the-burton-blog/snowboard-buying-guide" target="_blank" rel="noreferrer">Burton 공식 구매 가이드 ↗</a><a href="https://www.spreadboard.net/" target="_blank" rel="noreferrer">SPREAD 공식 모델, 보드 진단 ↗</a></div><p>자료 확인: {DECK_SOURCE_CHECKED}. 구매 전 해당 모델의 최신 권장 체중, 폭과 재고 사이즈를 확인해 주세요.</p></section>
  </div>;
}
