export type AnswerDraftStatus = "DRAFT" | "REVIEW_REQUIRED" | "APPROVED" | "PUBLISHED" | "REJECTED";

export const ANSWER_DRAFT_STATUS_LABEL: Record<AnswerDraftStatus, string> = {
  DRAFT: "작성 중",
  REVIEW_REQUIRED: "검토 대기",
  APPROVED: "승인됨",
  PUBLISHED: "게시됨",
  REJECTED: "거절됨",
};

export interface AnswerDraftListItem {
  draftId: string;
  status: AnswerDraftStatus;
  targetPostPublicId: string;
  bodyPreview: string;
  evidenceCount: number;
  createdByNickname: string;
  createdByToken: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AnswerDraftListResponse {
  items: AnswerDraftListItem[];
  page: number;
  size: number;
  hasNext: boolean;
  publishEnabled: boolean;
}

export interface AnswerDraftEvidence {
  evidenceId: string;
  chunkHash: string;
  currentStatus: "MATCH" | "CHANGED" | "UNAVAILABLE";
  title: string | null;
  url: string | null;
  text: string | null;
}

export interface AnswerDraftAudit {
  action: "SAVE" | "APPROVE" | "REJECT" | "PUBLISH" | "PUBLISH_REJECTED";
  actorNickname: string | null;
  viaToken: boolean;
  reasonCode: string | null;
  createdAt: string;
}

export interface AnswerDraftDetail {
  draftId: string;
  status: AnswerDraftStatus;
  version: number;
  target: {
    postPublicId: string;
    visible: boolean;
    title: string | null;
    url: string | null;
    contentVersion: number | null;
  };
  body: string;
  evidence: AnswerDraftEvidence[];
  createdByNickname: string;
  createdByToken: boolean;
  createdAt: string;
  reviewedByNickname: string | null;
  reviewedAt: string | null;
  approvedByNickname: string | null;
  approvedAt: string | null;
  approvedPostContentVersion: number | null;
  rejectReason: string | null;
  publishedCommentId: string | null;
  publishedByNickname: string | null;
  publishedAt: string | null;
  publishEnabled: boolean;
  audits: AnswerDraftAudit[];
}

export interface ApiTokenItem {
  tokenId: string;
  name: string;
  tokenPrefix: string;
  scopes: Array<"READ" | "DRAFT">;
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  lastUsedAt: string | null;
  active: boolean;
}

export type AdminMemberStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";

export const MEMBER_STATUS_LABEL: Record<AdminMemberStatus, string> = {
  ACTIVE: "정상",
  SUSPENDED: "정지",
  WITHDRAWN: "탈퇴",
};

/** admin.css의 상태 배지 색을 그대로 쓴다. */
export const MEMBER_STATUS_BADGE: Record<AdminMemberStatus, string> = {
  ACTIVE: "APPROVED",
  SUSPENDED: "REJECTED",
  WITHDRAWN: "DRAFT",
};

export const MEMBER_ROLE_LABEL: Record<AdminMember["role"], string> = {
  ROLE_USER: "일반 회원",
  ROLE_ADMIN: "운영자",
};

export interface AdminMember {
  publicId: string;
  email: string;
  nickname: string;
  status: AdminMemberStatus;
  role: "ROLE_USER" | "ROLE_ADMIN";
  createdAt: string;
}

export interface AdminMemberListResponse {
  items: AdminMember[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface AdminPage<T> {
  items: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface AdminDailyCount {
  date: string;
  signups: number;
  posts: number;
  comments: number;
}

export interface AdminOverview {
  days: number;
  today: AdminDailyCount;
  series: AdminDailyCount[];
  pendingReports: number;
  unansweredInquiries: number;
}

export interface AdminActionLogItem {
  id: number;
  action: string;
  targetType: string;
  targetId: string;
  reason: string | null;
  actorNickname: string | null;
  actorPublicId: string | null;
  createdAt: string;
}

export interface AdminMemberDetail {
  publicId: string;
  maskedEmail: string;
  nickname: string;
  status: AdminMemberStatus;
  role: AdminMember["role"];
  createdAt: string;
  emailVerified: boolean;
  hasPassword: boolean;
  socialProviders: string[];
  postCount: number;
  commentCount: number;
  recentPosts: {
    publicId: string;
    title: string;
    categoryCode: string;
    categoryName: string;
    status: string;
    isDeleted: boolean;
    createdAt: string;
  }[];
  recentComments: {
    commentId: number;
    contentPreview: string;
    postPublicId: string;
    postTitle: string;
    postCategoryCode: string;
    isDeleted: boolean;
    isHeld: boolean;
    createdAt: string;
  }[];
  history: AdminActionLogItem[];
}

export interface AdminCommentItem {
  commentId: number;
  contentPreview: string;
  writerNickname: string | null;
  writerPublicId: string | null;
  isAnonymous: boolean;
  isDeleted: boolean;
  isHeld: boolean;
  postPublicId: string;
  postTitle: string;
  postCategoryCode: string;
  createdAt: string;
}

export interface AdminInquiryItem {
  publicId: string;
  title: string;
  writerNickname: string | null;
  answered: boolean;
  commentCount: number;
  createdAt: string;
}

export interface AdminResortReportItem {
  reportId: number;
  resortName: string;
  content: string;
  writerNickname: string | null;
  status: "NORMAL" | "HIDDEN" | "BLOCKED" | "DELETED";
  createdAt: string;
}

export const ACTION_LABEL: Record<string, string> = {
  MEMBER_SUSPEND: "회원 정지",
  MEMBER_REINSTATE: "정지 해제",
  ADMIN_GRANT: "운영자 지정",
  ADMIN_REVOKE: "운영자 해제",
  POST_BLIND: "글 블라인드",
  POST_UNBLIND: "글 블라인드 해제",
  POST_DELETE: "글 삭제",
  COMMENT_BLIND: "댓글 블라인드",
  COMMENT_UNBLIND: "댓글 블라인드 해제",
  COMMENT_DELETE: "댓글 삭제",
  MARKET_MODERATION: "시장 글 상태 변경",
  RESORT_REPORT_MODERATION: "설질 제보 상태 변경",
  CONTENT_REPORT_ACTION: "신고 처리",
  BANNER_CREATE: "배너 추가",
  BANNER_UPDATE: "배너 수정",
  BANNER_DELETE: "배너 삭제",
  CHAT_IP_BAN: "라이브톡 IP 차단",
  CHAT_IP_UNBAN: "라이브톡 IP 차단 해제",
  CONFIG_UPDATE: "운영 설정 변경",
};

export const TARGET_LABEL: Record<string, string> = {
  MEMBER: "회원",
  POST: "글",
  COMMENT: "댓글",
  RESORT_REPORT: "설질 제보",
  CONTENT_REPORT: "신고",
  BANNER: "배너",
  CHAT_BAN: "IP 차단",
  CONFIG: "설정",
};

/** 게시판 코드에 맞는 공개 상세 주소. 시장과 카풀은 전용 화면이 있다. */
export function postHref(categoryCode: string, publicId: string) {
  if (categoryCode === "MARKET") return `/market/${publicId}`;
  if (categoryCode === "CARPOOL") return `/carpool/${publicId}`;
  return `/posts/${publicId}`;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
}

export async function readErrorMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as ApiErrorBody;
    return body.message || fallback;
  } catch {
    return fallback;
  }
}

export function formatDateTime(value: string | null) {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
}
