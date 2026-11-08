export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
export const API_V1_URL = `${API_BASE_URL}/api/v1`;

export type SocialLoginProvider = "google" | "naver";

export const API_ENDPOINTS = {
  csrf: `${API_V1_URL}/csrf`,
  auth: {
    login: `${API_V1_URL}/auth/login`,
    logout: `${API_V1_URL}/auth/logout`,
    oauth2Authorize: (provider: SocialLoginProvider, returnUrl?: string | null) =>
      `${API_V1_URL}/auth/oauth2/authorize/${provider}${returnUrl ? `?returnUrl=${encodeURIComponent(returnUrl)}` : ""}`,
    oauth2SignUpPending: `${API_V1_URL}/auth/oauth2/signup/pending`,
    oauth2SignUp: `${API_V1_URL}/auth/oauth2/signup`,
    requestSignUpVerification: `${API_V1_URL}/auth/email-verifications/sign-up`,
    confirmSignUpVerification: `${API_V1_URL}/auth/email-verifications/sign-up/confirm`,
    requestPasswordReset: `${API_V1_URL}/auth/password-reset/requests`,
    confirmPasswordReset: `${API_V1_URL}/auth/password-reset/confirm`,
    resetPassword: `${API_V1_URL}/auth/password-reset`,
  },
  members: {
    settings: `${API_V1_URL}/members/me/settings`,
    marketingConsent: `${API_V1_URL}/members/me/marketing-consent`,
    nicknameAvailability: `${API_V1_URL}/members/me/nickname-availability`,
    changePassword: `${API_V1_URL}/members/me/password`,
    withdrawal: `${API_V1_URL}/members/me/withdrawal`,
    resetProfileImage: `${API_V1_URL}/members/me/profile-image`,
    me: `${API_V1_URL}/members/me`,
    signup: `${API_V1_URL}/members`,
    emailAvailability: `${API_V1_URL}/members/email-availability`,
  },
  images: {
    profileUpload: `${API_V1_URL}/images/profile`,
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
    weather: (resortCode: string) => `${API_V1_URL}/resort-cams/${encodeURIComponent(resortCode)}/weather`,
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
  reports: {
    create: `${API_V1_URL}/reports`,
  },
  seasonLogs: {
    me: (season?: number) => `${API_V1_URL}/season-logs/me${season ? `?season=${season}` : ""}`,
    visits: `${API_V1_URL}/season-logs/me/visits`,
    visit: (visitDate: string) => `${API_V1_URL}/season-logs/me/visits/${visitDate}`,
    share: (seasonStartYear: number) => `${API_V1_URL}/season-logs/me/shares/${seasonStartYear}`,
    shared: (shareToken: string) => `${API_V1_URL}/season-shares/${encodeURIComponent(shareToken)}`,
  },
  admin: {
    reports: (status: string, page: number) => `${API_V1_URL}/admin/reports?status=${status}&page=${page}`,
    reportAction: (reportId: number) => `${API_V1_URL}/admin/reports/${reportId}/actions`,
    answerDrafts: (status: string, page: number, size = 20) =>
      `${API_V1_URL}/admin/answer-drafts?page=${page}&size=${size}${status ? `&status=${encodeURIComponent(status)}` : ""}`,
    answerDraft: (draftId: string) => `${API_V1_URL}/admin/answer-drafts/${encodeURIComponent(draftId)}`,
    answerDraftAction: (draftId: string, action: "approve" | "reject" | "publish") =>
      `${API_V1_URL}/admin/answer-drafts/${encodeURIComponent(draftId)}/${action}`,
    apiTokens: `${API_V1_URL}/admin/api-tokens`,
    revokeApiToken: (tokenId: string) => `${API_V1_URL}/admin/api-tokens/${encodeURIComponent(tokenId)}/revoke`,
    members: (query: string, page: number, size = 20) =>
      `${API_V1_URL}/admin/members?page=${page}&size=${size}${query ? `&query=${encodeURIComponent(query)}` : ""}`,
    memberStatusAction: (publicId: string, action: "suspend" | "reinstate") =>
      `${API_V1_URL}/admin/members/${encodeURIComponent(publicId)}/${action}`,
    searchReconcile: `${API_V1_URL}/admin/search/reconcile`,
    searchOutboxSummary: `${API_V1_URL}/admin/search/outbox/summary`,
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
