export interface ResortReportPreview {
  id: string;
  resortCode: string;
  content: string;
  time: string;
}

export const RESORT_REPORT_PREVIEWS: ResortReportPreview[] = [
  { id: "report-1", resortCode: "PHOENIX", content: "챔피언 상단 단단하고 엣지 잘 잡힙니다.", time: "10:32" },
  { id: "report-2", resortCode: "YONGPYONG", content: "레인보우 오전 설질 좋고 대기 거의 없습니다.", time: "10:18" },
  { id: "report-3", resortCode: "HIGH1", content: "아테나 하단은 오후부터 살짝 아이스입니다.", time: "09:54" },
  { id: "report-4", resortCode: "VIVALDI", content: "발라드 슬러시가 조금 있지만 타기 괜찮습니다.", time: "09:37" },
  { id: "report-5", resortCode: "JISAN", content: "야간 정설 직후라 초급 슬로프 상태 좋습니다.", time: "09:12" },
  { id: "report-6", resortCode: "WELLI_HILLI", content: "C3 상단은 압설 상태 좋고 하단은 조금 무겁습니다.", time: "08:48" },
  { id: "report-7", resortCode: "MUJU", content: "설천 상단 바람이 강하지만 설질은 양호합니다.", time: "08:21" },
];

export function formatKoreanCalendarDate(date: Date): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "numeric",
    day: "numeric",
  }).format(date);
}
