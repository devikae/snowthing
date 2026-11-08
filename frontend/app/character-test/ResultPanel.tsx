import Image from "next/image";
import type { CharacterResult } from "./api";
import { QUESTIONS, SPORT_LABEL, STATS, STAT_MAX, characterImage, characterName, descriptionSentences, gearImage, type Sport } from "./data";

export function ResultPanel({ result, sport, answers }: { result: CharacterResult; sport: Sport; answers: string }) {
  const { character, stats, highlightQuestions, bestMatch } = result;
  return (
    <section className="ct-panel ct-result" aria-labelledby="ct-result-name">
      <p className="ct-eyebrow">나의 스키장 캐릭터</p>
      <div className="ct-sprite-wrap">
        <Image className="ct-sprite-gear" src={gearImage(sport, character.job)} alt={SPORT_LABEL[sport]} width={77} height={192} unoptimized priority />
        <Image className="ct-sprite" src={characterImage(character.job)} alt="" width={192} height={192} unoptimized priority />
      </div>
      <h2 id="ct-result-name">{characterName(character)}</h2>
      <dl className="ct-stats">
        {STATS.map((stat) => (
          <div key={stat.key}>
            <dt>{stat.label}</dt>
            <dd aria-label={`${STAT_MAX}점 중 ${stats[stat.key]}점`}>
              {Array.from({ length: STAT_MAX }, (_, index) => <span key={index} className={index < stats[stat.key] ? "is-on" : ""} />)}
              <b>{stats[stat.key]}</b>
            </dd>
          </div>
        ))}
      </dl>
      <div className="ct-description">
        <strong>이런 타입이에요</strong>
        {descriptionSentences(character).map((sentence) => <p key={sentence}>{sentence}</p>)}
      </div>
      {bestMatch && <p className="ct-best-match"><span>나와 찰떡인 타입</span><strong>{characterName(bestMatch)}</strong></p>}
      <h3>이렇게 골랐어요</h3>
      <ul className="ct-evidence">
        {highlightQuestions.map((number) => {
          const question = QUESTIONS[number - 1];
          return <li key={number}><span>{question.situation}</span><strong>{answers[number - 1] === "A" ? question.a : question.b}</strong></li>;
        })}
      </ul>
      <p className="ct-fineprint">재미로 보는 결과예요. 고른 답으로 정해질 뿐, 사람을 진단하지 않습니다.</p>
    </section>
  );
}
