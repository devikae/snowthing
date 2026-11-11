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
