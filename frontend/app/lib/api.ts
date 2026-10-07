export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
export const API_V1_URL = `${API_BASE_URL}/api/v1`;

export const API_ENDPOINTS = {
  csrf: `${API_V1_URL}/csrf`,
  auth: {
    login: `${API_V1_URL}/auth/login`,
    logout: `${API_V1_URL}/auth/logout`,
    requestSignUpVerification: `${API_V1_URL}/auth/email-verifications/sign-up`,
    confirmSignUpVerification: `${API_V1_URL}/auth/email-verifications/sign-up/confirm`,
    requestPasswordReset: `${API_V1_URL}/auth/password-reset/requests`,
    confirmPasswordReset: `${API_V1_URL}/auth/password-reset/confirm`,
    resetPassword: `${API_V1_URL}/auth/password-reset`,
  },
  members: {
    me: `${API_V1_URL}/members/me`,
    signup: `${API_V1_URL}/members`,
    emailAvailability: `${API_V1_URL}/members/email-availability`,
  },
  images: {
    upload: `${API_V1_URL}/images`,
  },
  master: {
    resorts: `${API_V1_URL}/master/resorts`,
    ridingStyles: `${API_V1_URL}/master/riding-styles`,
  },
  posts: {
    list: `${API_V1_URL}/posts`,
    create: `${API_V1_URL}/posts`,
    detail: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    update: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    delete: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    reaction: (publicId: string, type: "LIKE" | "DISLIKE") =>
      `${API_V1_URL}/posts/${publicId}/reaction?type=${type}`,
    comments: (publicId: string, cursor?: string | null, size = 20) =>
      `${API_V1_URL}/posts/${publicId}/comments?${
        cursor != null ? `cursor=${cursor}&size=${size}` : `size=${size}`
      }`,
  },
  comments: {
    replies: (commentId: string, cursor?: string | null, size = 20) =>
      `${API_V1_URL}/comments/${commentId}/replies?${
        cursor != null ? `cursor=${cursor}&size=${size}` : `size=${size}`
      }`,
    delete: (commentId: string) => `${API_V1_URL}/comments/${commentId}`,
  },
  chat: {
    recent: `${API_V1_URL}/chat/recent`,
  },
  resortCams: {
    list: `${API_V1_URL}/resort-cams`,
  },
  market: {
    categories: `${API_V1_URL}/market/categories`,
    preview: `${API_V1_URL}/market-listings/preview`,
    list: `${API_V1_URL}/market-listings`,
    create: `${API_V1_URL}/market-listings`,
    detail: (publicId: string) => `${API_V1_URL}/market-listings/${publicId}`,
    update: (publicId: string) => `${API_V1_URL}/market-listings/${publicId}`,
    delete: (publicId: string) => `${API_V1_URL}/market-listings/${publicId}`,
    tradeStatus: (publicId: string) => `${API_V1_URL}/market-listings/${publicId}/trade-status`,
  },
  carpool: {
    list: `${API_V1_URL}/carpools`,
    detail: (publicId: string) => `${API_V1_URL}/carpools/${publicId}`,
    create: `${API_V1_URL}/carpools`,
    update: (publicId: string) => `${API_V1_URL}/carpools/${publicId}`,
    delete: (publicId: string) => `${API_V1_URL}/carpools/${publicId}`,
    autoPreview: `${API_V1_URL}/carpools/auto-preview`,
    places: (query: string) => `${API_V1_URL}/carpools/places?query=${encodeURIComponent(query)}`,
  },
  resortReports: {
    today: (resortId?: number, page = 1, size = 20) =>
      `${API_V1_URL}/resort-reports/today?page=${page}&size=${size}${resortId ? `&resortId=${resortId}` : ""}`,
    create: `${API_V1_URL}/resort-reports`,
    delete: (reportId: number) => `${API_V1_URL}/resort-reports/${reportId}`,
  },
} as const;

export interface ResortReportItem {
  reportId: number;
  resortId: number;
  resortName: string;
  resortCode: string;
  authorNickname: string;
  content: string;
  createdAt: string;
  canDelete: boolean;
}

export interface OffsetPage<T> {
  content: T[];
  pageInfo: {
    page: number;
    totalPages: number;
    totalElements: number;
    hasNext: boolean;
    pageSize: number;
  };
}
