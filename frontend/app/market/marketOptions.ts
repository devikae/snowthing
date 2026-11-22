// 판매글 작성, 수정, 목록, 상세가 같이 쓰는 상품 상태 이름과 작성 화면의 거래 방식 선택지.
export const PRODUCT_CONDITION_LABELS: Record<string, string> = {
  NEW: "새 상품",
  LIKE_NEW: "거의 새 상품",
  GOOD: "사용감 적음",
  USED: "사용감 있음",
  DAMAGED: "수리, 하자 있음",
};

export const PRODUCT_CONDITION_OPTIONS = Object.entries(PRODUCT_CONDITION_LABELS);

// 상세 화면은 BOTH를 "직거래, 택배"로 따로 적는다. 작성 화면의 선택지 이름과 다르다.
export const TRANSACTION_METHOD_OPTIONS = [
  ["DIRECT", "직거래"],
  ["DELIVERY", "택배"],
  ["BOTH", "모두 가능"],
] as const;
