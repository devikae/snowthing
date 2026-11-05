const DAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];

export function formatCarpoolDateTime(isoString: string): string {
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return isoString;
  const hour = date.getHours();
  const minute = String(date.getMinutes()).padStart(2, "0");
  return `${date.getMonth() + 1}.${date.getDate()} (${DAY_NAMES[date.getDay()]}) ${
    hour < 12 ? "오전" : "오후"
  } ${hour % 12 || 12}:${minute}`;
}

export function carpoolCostLabel(costMode: "AUTO" | "MANUAL"): string {
  return costMode === "MANUAL" ? "1인 비용" : "예상 1인 비용";
}
