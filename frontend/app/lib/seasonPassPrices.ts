// 시즌권 계산기에 미리 채우는 리조트별 대략 가격. 2026년 11월에 공식 누리집과 판매처 자료로 조사했다.
// 리프트권은 6시간 안팎 평일 권종에 흔히 받는 카드 할인을 적용한 값이다.
// 판매 차수가 바뀌면 값이 달라진다: 비발디 11/15, X6+ 2차 11/19, 휘닉스 2차 11/30. 그 뒤에 다시 확인한다.
export const SEASON_PASS_PRICE_CHECKED_AT = "2026년 11월";

const X6 = "X6+ 통합 시즌권(6곳 공용) 2차 판매가";

export interface ResortPassPrice {
  code: string;
  name: string;
  passPrice: number | null;
  passLabel: string;
  ticketPrice: number;
  ticketLabel: string;
  /** 이 날짜(KST, YYYY-MM-DD)부터 시즌권 값이 바뀐다. */
  passChange?: { from: string; price: number; label: string };
}

export const RESORT_PASS_PRICES: ResortPassPrice[] = [
  { code: "HIGH1", name: "하이원", passPrice: 600000, passLabel: "하이원 단독 시즌권 정상가", ticketPrice: 62000, ticketLabel: "7시간권 88,000원에 카드 30% 할인" },
  { code: "YONGPYONG", name: "용평", passPrice: 649000, passLabel: X6, ticketPrice: 62000, ticketLabel: "주간권 96,000원에 35% 할인" },
  { code: "PHOENIX", name: "휘닉스 평창", passPrice: 599000, passLabel: "베이직 시즌패스 2차 판매가", ticketPrice: 71000, ticketLabel: "7시간권 99,000원에 온라인 할인" },
  {
    code: "VIVALDI",
    name: "비발디파크",
    passPrice: 440000,
    passLabel: "스마트 리프트 시즌패스 특가",
    ticketPrice: 58000,
    ticketLabel: "7시간권 평일 83,000원에 카드 30% 할인",
    passChange: { from: "2026-11-16", price: 560000, label: "스마트 리프트 시즌패스 정상가" },
  },
  { code: "WELLIHILLI", name: "웰리힐리", passPrice: 649000, passLabel: X6, ticketPrice: 56000, ticketLabel: "6시간권 80,000원에 카드 30% 할인" },
  { code: "JISAN", name: "지산", passPrice: 649000, passLabel: X6, ticketPrice: 55000, ticketLabel: "6시간권 평일 79,000원에 카드 30% 할인" },
  { code: "KONJIAM", name: "곤지암", passPrice: 900000, passLabel: "전일 시즌권 (주중권, 주말권은 각 580,000원)", ticketPrice: 65000, ticketLabel: "6시간권 평일 87,000원에 카드 25% 할인" },
  { code: "MUJU", name: "무주덕유산", passPrice: 1080000, passLabel: "통합권 I 정상가 (인터넷회원)", ticketPrice: 56000, ticketLabel: "6시간권 평일 80,000원에 30% 할인" },
  { code: "ELYSIAN", name: "엘리시안 강촌", passPrice: 649000, passLabel: X6, ticketPrice: 62000, ticketLabel: "6시간권 평일 82,000원에 카드 25% 할인" },
  { code: "ALPENSIA", name: "알펜시아", passPrice: 260000, passLabel: "알펜시아 단독 시즌권 (지난 시즌 가격)", ticketPrice: 48000, ticketLabel: "주간권 69,000원에 카드 30% 할인" },
  { code: "OAK_VALLEY", name: "오크밸리", passPrice: 210000, passLabel: "전일 시즌권", ticketPrice: 53000, ticketLabel: "복합권 76,000원에 카드 30% 할인" },
  { code: "O2_RESORT", name: "오투리조트", passPrice: 272000, passLabel: "성인 시즌권 2차 판매가", ticketPrice: 70000, ticketLabel: "7시간권 평일 70,000원 (카드 할인 없음)" },
  { code: "EDEN_VALLEY", name: "에덴밸리", passPrice: null, passLabel: "시즌권 가격을 찾지 못했어요", ticketPrice: 53000, ticketLabel: "7시간권 79,000원에 온라인 33% 할인" },
];

/** 오늘(KST) 기준으로 적용되는 시즌권 값과 설명. */
export function currentPass(resort: ResortPassPrice, todayKst: string) {
  if (resort.passChange && todayKst >= resort.passChange.from) {
    return { price: resort.passChange.price, label: resort.passChange.label };
  }
  return { price: resort.passPrice, label: resort.passLabel };
}
