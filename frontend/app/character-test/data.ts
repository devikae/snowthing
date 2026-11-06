// 캐릭터 테스트 문구. 이름과 질문은 사용자가 확정했다(docs/conception/024_character_test/01_design.md). 고칠 때 설계 문서도 함께 본다.
// 유형 계산은 서버가 한다. 이 파일에는 직업 코드와 계획형, 즉흥형 구분만 둔다.

export type JobCode = "CAPTAIN" | "RESORT_INSIDER" | "HYPE" | "MOOD_MAKER" | "GRINDER" | "HOT_PACK" | "GEAR_GEEK" | "RESORT_OUTSIDER";
export type PlanStyle = "PLANNED" | "SPONTANEOUS";
export type Sport = "SKI" | "BOARD";
export type LevelCode = "BEST" | "GOOD" | "NEUTRAL" | "BICKER" | "APART";

export interface CharacterLabel {
  job: JobCode;
  planStyle: PlanStyle;
}

export interface CharacterStats {
  social: number;
  adventure: number;
  warmth: number;
  planning: number;
}

export interface Question {
  situation: string;
  a: string;
  b: string;
  c?: string;
}

// 배열 순서가 답변 문자열의 자리다. 화면에서는 섞어 보여 주고 저장할 때 이 순서로 되돌린다.
export const QUESTIONS: readonly Question[] = [
  { situation: "리프트에 친구와 둘이 탔다", a: "내가 계속 떠든다", b: "조용히 슬로프 구경한다" },
  { situation: "이번 주말 타러 가고 싶다", a: "단톡에 \"이번 주 타는 사람?\" 먼저 물어본다", b: "혼자 가도 좋다" },
  { situation: "종일 타고 숙소에 왔다", a: "모여서 한잔 더 한다", b: "씻고 바로 눕는다" },
  { situation: "처음 가는 리조트에 왔다", a: "슬로프맵 보고 쉬운 곳부터 탄다", b: "멋져 보이는 곳부터 가 본다" },
  { situation: "친구가 타는 법을 알려준다", a: "동작을 하나씩 알려 주는 게 좋다", b: "왜 그렇게 타는지부터 듣는 게 좋다" },
  { situation: "리프트 위에서 드는 생각", a: "아까 그 턴은 왜 밀렸지", b: "나도 올림픽 나갔으면 어땠을까" },
  { situation: "친구가 넘어져서 민망해한다", a: "\"무릎을 더 굽혀 봐\"", b: "\"괜찮아? 방금 좀 멋있었어\"" },
  { situation: "친구가 \"나 오늘 좀 늘지 않았어?\"라고 묻는다 (사실 비슷하다)", a: "\"음, 아직 턴이 밀려\"", b: "\"응, 많이 늘었어!\"" },
  { situation: "일행 중 한 명이 상급 코스를 무서워한다", a: "실력별로 나눠서 타자고 한다", b: "다 같이 갈 수 있는 곳으로 간다" },
  { situation: "스키장 가기 전날 밤", a: "장비, 옷, 주차까지 확인한다", b: "아침에 챙기면 된다" },
  { situation: "오늘 언제부터 탈까", a: "오픈 시간에 맞춰 간다", b: "일어나서 정한다" },
  { situation: "점심은 언제 먹을까", a: "붐비기 전에 미리 먹는다", b: "배고플 때 먹는다" },
  // 뒤 3개는 타는 습관이다. 유형에는 영향이 없고 궁합 점수에만 쓰며, A, B, C 순서가 척도다.
  { situation: "주로 타는 코스", a: "초급", b: "중급", c: "상급" },
  { situation: "쉬는 타이밍", a: "쉬지 않고 계속 탄다", b: "두세 번 타고 쉰다", c: "자주 쉰다" },
  { situation: "보통 언제까지 타나", a: "오전만", b: "오후까지", c: "야간까지" },
];

const HABIT_TOPICS = ["코스는", "쉬는 타이밍은", "타는 시간은"];

// 서버가 준 습관 차이(0~2)로 지도에서 사람을 눌렀을 때의 한 줄을 만든다.
export function habitNote(gaps: number[]) {
  const pick = (gap: number) => HABIT_TOPICS.filter((_, index) => gaps[index] === gap);
  const near = pick(0).map((topic, index, list) => (index < list.length - 1 ? topic.replace(/[은는]$/, "") : topic)).join(", ");
  const far = pick(2).map((topic, index, list) => (index < list.length - 1 ? topic.replace(/[은는]$/, "") : topic)).join(", ");
  if (near && far) return `${near} 잘 맞는데 ${far} 많이 달라요.`;
  if (near) return `${near} 잘 맞아요.`;
  if (far) return `${far} 많이 달라요.`;
  return "타는 습관은 비슷한 편이에요.";
}

export const JOBS: Record<JobCode, { name: string; planned: string; spontaneous: string }> = {
  CAPTAIN: { name: "대장님", planned: "부지런한 대장님", spontaneous: "대문자 P 대장님" },
  RESORT_INSIDER: { name: "리조트 인싸", planned: "약속 많은 리조트 인싸", spontaneous: "낯가림 없는 리조트 인싸" },
  HYPE: { name: "바람잡이", planned: "치밀한 바람잡이", spontaneous: "말만 앞선 바람잡이" },
  MOOD_MAKER: { name: "분위기 메이커", planned: "준비된 분위기 메이커", spontaneous: "아무 말 분위기 메이커" },
  GRINDER: { name: "연습벌레", planned: "성실한 연습벌레", spontaneous: "가끔 연습벌레" },
  HOT_PACK: { name: "인간 핫팩", planned: "계획적인 인간 핫팩", spontaneous: "느긋한 인간 핫팩" },
  GEAR_GEEK: { name: "장비 덕후", planned: "후기 다 읽은 장비 덕후", spontaneous: "일단 지른 장비 덕후" },
  RESORT_OUTSIDER: { name: "리조트 아싸", planned: "계획적인 리조트 아싸", spontaneous: "즉흥적인 리조트 아싸" },
};

// 특성 설명 초안. 성격 용어 대신 스키장 장면으로 쓴다. 사용자 검수 대상이다.
export const DESCRIPTIONS: Record<JobCode, Record<PlanStyle, string>> = {
  CAPTAIN: {
    PLANNED: "출발 시간부터 집합 장소까지 정해 두고 단톡에 일정표를 올린다. 일행이 늦으면 제일 먼저 전화한다. 덕분에 일행은 따라가기만 하면 된다.",
    SPONTANEOUS: "\"일단 가자\"가 오늘의 계획이다. 앞장서서 내려가면서 다음 행선지를 정한다. 따라가다 보면 어떻게든 재미있는 데 도착한다.",
  },
  RESORT_INSIDER: {
    PLANNED: "오전 친구, 점심 친구, 야간 친구 약속이 따로 있다. 곤돌라 줄에서도 아는 사람을 만난다. 같이 오면 심심할 틈이 없다.",
    SPONTANEOUS: "리프트 옆자리 처음 본 사람과 내릴 때쯤 친구가 되어 있다. 리프트 직원도 이미 아는 사이다. 혼자 왔어도 갈 때는 일행이 생긴다.",
  },
  HYPE: {
    PLANNED: "며칠 전부터 단톡에서 이번엔 상급 가자고 분위기를 잡는다. 말대로 되는 날이 꽤 많다. 듣다 보면 나도 갈 수 있을 것 같아진다.",
    SPONTANEOUS: "\"다음엔 저 점프대 간다\"고 매번 말한다. 진짜로 갔는지는 아무도 모른다. 그래도 말을 듣다 보면 일행 전체가 신이 난다.",
  },
  MOOD_MAKER: {
    PLANNED: "넘어진 친구를 먼저 챙기고, 간식까지 미리 준비해 온다. 이 사람이 있는 날은 일행 분위기부터 다르다. 혼자 떨어진 사람이 없는지 제일 먼저 살핀다.",
    SPONTANEOUS: "아무 말이나 했는데 일행이 다 웃는다. 슬로프 위에서 갑자기 노래를 부르기도 한다. 덕분에 줄 서는 시간도 지루하지 않다.",
  },
  GRINDER: {
    PLANNED: "첫 리프트를 타고 오늘 연습할 턴을 정해 둔다. 점심도 붐비기 전에 끝낸다. 같이 타면 자세까지 봐 주는데 꽤 정확하다.",
    SPONTANEOUS: "느낌 오는 날엔 한 기술만 연습한다. 안 내키는 날엔 리프트 3번밖에 안 타고 튄다. 그래도 꽂힌 날에는 연습량이 엄청나다.",
  },
  HOT_PACK: {
    PLANNED: "간식을 챙겨 와서 배고파 보이는 사람 손에 쥐어 준다. 일행 컨디션은 이 사람이 제일 잘 안다. 남 챙기는 게 먼저인 사람이다.",
    SPONTANEOUS: "힘들다고 하면 같이 쉬어 주고, 코코아 한 잔을 말없이 건넨다. 서두르는 법이 없다. 누가 뒤처져도 싫은 티 한번 안 내는 따뜻한 사람이다.",
  },
  GEAR_GEEK: {
    PLANNED: "데크 후기를 전부 읽고 왁싱에 엣징까지 직접 한다. 장비 질문은 일단 이 사람에게 간다. 덕분에 일행 장비 고민이 금방 끝난다.",
    SPONTANEOUS: "새벽에 본 후기 하나로 바인딩을 질렀다. 타 보기 전에 벌써 다음 장비를 보고 있다. 지갑은 가벼워도 장비 이야기는 끝이 없다.",
  },
  RESORT_OUTSIDER: {
    PLANNED: "붐비는 게 싫어서 사람 적은 시간에 맞춰 와 조용히 즐긴다. 마음 맞는 한 명이면 충분하다. 친해지면 속 깊은 얘기를 제일 잘 들어 준다.",
    SPONTANEOUS: "이어폰 끼고 혼자 타다가 설경이 좋으면 한참 멈춰 있다. 오늘 몇 번 탔는지는 세지 않는다. 말은 적어도 같이 타면 은근히 편하다.",
  },
};

// 카드와 결과 화면 모두 한 줄에 한 문장씩 보여 준다.
export function descriptionSentences(label: CharacterLabel) {
  return DESCRIPTIONS[label.job][label.planStyle].split(/(?<=\.)\s+/);
}

export const STATS: readonly { key: keyof CharacterStats; label: string }[] = [
  { key: "social", label: "사교력" },
  { key: "adventure", label: "모험심" },
  { key: "warmth", label: "다정함" },
  { key: "planning", label: "계획력" },
];
export const STAT_MAX = 3;

// 위에서 아래 순서. 맨 아래 칸 문구는 놀림이 아니라 농담으로 쓴다.
export const LEVELS: readonly { code: LevelCode; label: string; note: string }[] = [
  { code: "BEST", label: "찰떡", note: "리프트 옆자리 예약" },
  { code: "GOOD", label: "같이 타요", note: "점심까지 같이 먹는 사이" },
  { code: "NEUTRAL", label: "무난", note: "슬로프에서 만나면 반가운 사이" },
  { code: "BICKER", label: "티격태격", note: "코스 정할 때 조금 시끄러움" },
  { code: "APART", label: "각자 타요", note: "리프트는 따로, 치킨은 같이" },
];

// 관계 지도는 테마와 상관없이 설원 바탕이다. 색은 나를 표시하는 강조색 하나만 쓴다.
export const MAP_SNOW = { sky: "#e0f0fa", pine: "#7fb5ac", trunk: "#9c8672", ink: "#28384a", muted: "#5c7a92", accent: "#e2603f", ring: "#a9cbe0" };
// 원은 흰색을 겹쳐 그린다. 안쪽으로 갈수록 희고 밝아진다.
export const RING_WHITE: Record<LevelCode, number> = { BEST: 0.75, GOOD: 0.5, NEUTRAL: 0.4, BICKER: 0.35, APART: 0.4 };

export const SPORT_LABEL: Record<Sport, string> = { SKI: "스키", BOARD: "보드" };

export function characterName(label: CharacterLabel) {
  const job = JOBS[label.job];
  return label.planStyle === "PLANNED" ? job.planned : job.spontaneous;
}

export function characterImage(job: JobCode) {
  return `/character-test/${job}.png`;
}

// 스키는 하나를 같이 쓰고, 보드는 직업 의상에 맞춰 8가지다.
export function gearImage(sport: Sport, job: JobCode) {
  return sport === "SKI" ? "/character-test/gear-SKI.png" : `/character-test/gear-BOARD-${job}.png`;
}

// 캐릭터는 40칸, 장비는 16x40칸 그림이다. 캐릭터와 겹치지 않도록 8칸 왼쪽에 놓는다.
export function gearBox(spriteX: number, spriteY: number, spriteSize: number) {
  const cell = spriteSize / 40;
  return { x: spriteX - 8 * cell, y: spriteY, width: 16 * cell, height: spriteSize };
}
