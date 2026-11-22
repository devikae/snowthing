// 카풀 목록의 서버 첫 화면(page.tsx)과 브라우저 목록(CarpoolListClient)이 같이 쓰는 값.
// "use client" 파일에서 내보낸 값은 서버 컴포넌트에서 클라이언트 참조가 되어 그대로 읽을 수 없어 따로 둔다.

export interface CarpoolItem {
  publicId: string;
  title: string;
  writerName: string;
  departureRegion: string;
  destinationResortName: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  departureAt: string;
  passengerCapacity: number;
  costMode: "AUTO" | "MANUAL";
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
}

export interface CarpoolPageResponse {
  content: CarpoolItem[];
  totalElements: number;
  totalPages: number;
}

/** 서버에서 받은 첫 목록. query가 지금 주소의 목록 요청과 같을 때만 쓴다. */
export interface InitialCarpoolList {
  query: string;
  data: CarpoolPageResponse;
}

export const PAGE_SIZE = 20;
export const MAX_PAGE = 100;

export function readCarpoolPage(value: string | null) {
  const requestedPage = Number(value || "1");
  return Number.isFinite(requestedPage) && requestedPage > 0
    ? Math.min(Math.floor(requestedPage), MAX_PAGE)
    : 1;
}

export function keywordQueryOf(keyword: string) {
  return keyword ? `&keyword=${encodeURIComponent(keyword)}` : "";
}

export function carpoolListQuery(page: number, keywordQuery: string) {
  return `page=${page - 1}&size=${PAGE_SIZE}${keywordQuery}`;
}

export function normalizeCarpoolPage(response: Partial<CarpoolPageResponse>): CarpoolPageResponse {
  return {
    content: Array.isArray(response.content) ? response.content : [],
    totalElements: response.totalElements || 0,
    totalPages: response.totalPages || 0,
  };
}
