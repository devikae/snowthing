// 홈 첫 화면 목록의 타입과 요청 크기. 서버 컴포넌트(page.tsx)와 HomePageClient가 함께 쓴다.
// "use client" 파일에서 값을 내보내면 서버 쪽에서는 클라이언트 참조가 되어 실제 숫자를 읽을 수 없어 따로 둔다.

export interface HomePost {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  writerProfileImageUrl?: string | null;
  writerPublicId?: string | null;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

export interface HomeCarpoolItem {
  publicId: string;
  departureRegion: string;
  destinationResortName: string;
  departureAt: string;
  passengerCapacity: number;
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
}

export interface MarketPreviewItem {
  publicId: string;
  thumbnailImageUrl: string | null;
}

// 서버가 첫 화면용으로 받아 둔 공개 목록이다. 값이 null이면 서버에서 받지 못한 것이라 브라우저가 기존처럼 다시 부른다.
export interface HomeInitialData {
  posts: HomePost[] | null;
  carpools: HomeCarpoolItem[] | null;
  marketPreviews: MarketPreviewItem[] | null;
}

// 홈 첫 화면 요청 크기. 서버와 브라우저가 같은 값을 쓴다.
export const HOME_FEED_SIZE = 15;
export const HOME_CARPOOL_SIZE = 4;
export const HOME_MARKET_SIZE = 4;
