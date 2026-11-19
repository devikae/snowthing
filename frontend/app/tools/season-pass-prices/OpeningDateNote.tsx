"use client";

import { useSeasonOpenings } from "../../lib/useSeasonOpenings";

// 시즌권 가격 데이터는 리조트 코드 표기가 조금 다르다(WELLIHILLI). 밑줄을 빼고 맞춘다.
const plainCode = (code: string) => code.replace(/_/g, "");

/** 시즌권 표의 리조트 이름 아래 개장일. 개장일을 받지 못했거나 날짜가 없으면 그리지 않는다. */
export function OpeningDateNote({ resortCode }: { resortCode: string }) {
  const data = useSeasonOpenings();
  const opening = data?.resorts.find((item) => plainCode(item.resortCode) === plainCode(resortCode));
  if (!opening?.openingDate) return null;
  const [, month, day] = opening.openingDate.split("-").map(Number);
  return <small>{`${opening.confirmed ? "개장" : "개장 예상"} ${month}월 ${day}일`}</small>;
}
