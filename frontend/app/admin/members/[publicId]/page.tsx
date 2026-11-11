"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { PageHeading } from "../../../components/PageHeading";
import { API_ENDPOINTS } from "../../../lib/api";
import { csrfFetch } from "../../../lib/csrfFetch";
import {
  ACTION_LABEL,
  type AdminMemberDetail,
  MEMBER_ROLE_LABEL,
  MEMBER_STATUS_BADGE,
  MEMBER_STATUS_LABEL,
  formatDateTime,
  postHref,
  readErrorMessage,
} from "../../adminTypes";
import { useAdminFetch } from "../../useAdminFetch";

const PROVIDER_LABEL: Record<string, string> = { GOOGLE: "구글", NAVER: "네이버", KAKAO: "카카오" };

/** 관리자 회원 상세 (IKA-78). 이메일은 가려서 받고, 비밀번호는 있는지만 보여 준다. */
export default function AdminMemberDetailPage() {
  const params = useParams<{ publicId: string }>();
  const publicId = params.publicId;
  const detail = useAdminFetch<AdminMemberDetail>(
    publicId ? API_ENDPOINTS.admin.memberDetail(publicId) : null,
    "회원 정보를 불러오지 못했습니다.",
  );
  const [myPublicId, setMyPublicId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch(API_ENDPOINTS.members.me, { credentials: "include" })
      .then(async (response) => (response.ok ? ((await response.json()) as { publicId?: string }) : null))
      .then((me) => {
        if (!cancelled) setMyPublicId(me?.publicId ?? null);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const member = detail.data;
  const isSelf = member?.publicId === myPublicId;
  const isAdmin = member?.role === "ROLE_ADMIN";

  const run = async (message: string, url: string, failure: string) => {
    if (!window.confirm(message)) return;
    setBusy(true);
    setActionError(null);
    try {
      const response = await csrfFetch(url, { method: "POST" });
      if (!response.ok) throw new Error(await readErrorMessage(response, failure));
      detail.reload();
    } catch (cause) {
      setActionError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  };

  if (!member) {
    return (
      <section className="panel admin-section">
        {detail.error ? (
          <p className="admin-error" role="alert">
            {detail.error}
          </p>
        ) : (
          <p className="admin-meta">불러오는 중...</p>
        )}
        <Link className="admin-link" href="/admin/members">
          회원 목록으로
        </Link>
      </section>
    );
  }

  const loginMethods = [
    ...(member.hasPassword ? ["이메일과 비밀번호"] : []),
    ...member.socialProviders.map((provider) => PROVIDER_LABEL[provider] ?? provider),
  ];

  return (
    <>
      <PageHeading title={`${member.nickname} 회원`} description="가입 정보, 활동, 조치 이력을 보고 정지나 운영자 권한을 바꿉니다." />
      <section className="panel admin-section" aria-label="기본 정보">
        <div className="admin-toolbar">
          <span className="admin-status" data-status={MEMBER_STATUS_BADGE[member.status]}>
            {MEMBER_STATUS_LABEL[member.status]}
          </span>
          <Link className="admin-link" href="/admin/members">
            회원 목록으로
          </Link>
        </div>
        <dl className="admin-detail-grid">
          <div>
            <dt>이메일</dt>
            <dd>{member.maskedEmail}</dd>
          </div>
          <div>
            <dt>권한</dt>
            <dd>{MEMBER_ROLE_LABEL[member.role]}</dd>
          </div>
          <div>
            <dt>가입일</dt>
            <dd>{formatDateTime(member.createdAt)}</dd>
          </div>
          <div>
            <dt>이메일 인증</dt>
            <dd>{member.emailVerified ? "완료" : "안 함"}</dd>
          </div>
          <div>
            <dt>로그인 방법</dt>
            <dd>{loginMethods.length > 0 ? loginMethods.join(", ") : "없음"}</dd>
          </div>
          <div>
            <dt>작성한 글, 댓글</dt>
            <dd>
              글 {member.postCount.toLocaleString()}개, 댓글 {member.commentCount.toLocaleString()}개
            </dd>
          </div>
        </dl>
        {actionError && (
          <p className="admin-error" role="alert">
            {actionError}
          </p>
        )}
        <div className="admin-actions">
          {member.status === "SUSPENDED" ? (
            <button
              className="admin-button"
              type="button"
              disabled={busy}
              onClick={() =>
                void run(
                  `${member.nickname} 회원의 정지를 해제할까요?`,
                  API_ENDPOINTS.admin.memberStatusAction(member.publicId, "reinstate"),
                  "회원 상태를 바꾸지 못했습니다.",
                )
              }
            >
              정지 해제
            </button>
          ) : (
            <button
              className="admin-button danger"
              type="button"
              disabled={busy || member.status === "WITHDRAWN" || isAdmin}
              onClick={() =>
                void run(
                  `${member.nickname} 회원을 정지할까요? 접속이 바로 끊기고 해제할 때까지 로그인할 수 없습니다.`,
                  API_ENDPOINTS.admin.memberStatusAction(member.publicId, "suspend"),
                  "회원 상태를 바꾸지 못했습니다.",
                )
              }
            >
              정지
            </button>
          )}
          {isAdmin ? (
            <button
              className="admin-button"
              type="button"
              disabled={busy || isSelf}
              title={isSelf ? "본인의 운영자 권한은 해제할 수 없습니다." : undefined}
              onClick={() =>
                void run(
                  `${member.nickname} 회원의 운영자 권한을 해제할까요? 관리자 화면에서 바로 로그아웃됩니다.`,
                  API_ENDPOINTS.admin.memberRoleAction(member.publicId, "revoke-admin"),
                  "운영자 권한을 바꾸지 못했습니다.",
                )
              }
            >
              운영자 해제
            </button>
          ) : (
            <button
              className="admin-button"
              type="button"
              disabled={busy || member.status !== "ACTIVE"}
              title={member.status !== "ACTIVE" ? "활동 중인 회원만 운영자로 지정할 수 있습니다." : undefined}
              onClick={() =>
                void run(
                  `${member.nickname} 회원을 운영자로 지정할까요? 관리자 화면의 모든 기능을 쓸 수 있게 되고, 지금 접속은 끊겨 다시 로그인해야 합니다.`,
                  API_ENDPOINTS.admin.memberRoleAction(member.publicId, "grant-admin"),
                  "운영자 권한을 바꾸지 못했습니다.",
                )
              }
            >
              운영자 지정
            </button>
          )}
          {isSelf && isAdmin && <small className="admin-meta">본인의 운영자 권한은 해제할 수 없습니다.</small>}
        </div>
      </section>
      <section className="panel admin-section" aria-label="최근 글">
        <h2>최근 글 {member.recentPosts.length}개</h2>
        <div className="admin-list">
          {member.recentPosts.length === 0 && <p className="admin-meta">작성한 글이 없습니다.</p>}
          {member.recentPosts.map((post) => (
            <div className="admin-row" key={post.publicId}>
              <span className="admin-status" data-status={post.isDeleted ? "REJECTED" : post.status === "NORMAL" ? "APPROVED" : "DRAFT"}>
                {post.isDeleted ? "삭제됨" : post.status === "NORMAL" ? "정상" : post.status}
              </span>
              <span style={{ minWidth: 0 }}>
                <p>{post.isDeleted ? post.title : <Link href={postHref(post.categoryCode, post.publicId)}>{post.title}</Link>}</p>
                <small>
                  {post.categoryName}, {formatDateTime(post.createdAt)}
                </small>
              </span>
            </div>
          ))}
        </div>
      </section>
      <section className="panel admin-section" aria-label="최근 댓글">
        <h2>최근 댓글 {member.recentComments.length}개</h2>
        <div className="admin-list">
          {member.recentComments.length === 0 && <p className="admin-meta">작성한 댓글이 없습니다.</p>}
          {member.recentComments.map((comment) => (
            <div className="admin-row" key={comment.commentId}>
              <span className="admin-status" data-status={comment.isDeleted ? "REJECTED" : "APPROVED"}>
                {comment.isHeld ? "가림" : comment.isDeleted ? "삭제됨" : "정상"}
              </span>
              <span style={{ minWidth: 0 }}>
                <p style={{ whiteSpace: "normal", overflowWrap: "anywhere" }}>{comment.contentPreview}</p>
                <small>
                  <Link href={postHref(comment.postCategoryCode, comment.postPublicId)}>{comment.postTitle}</Link>,{" "}
                  {formatDateTime(comment.createdAt)}
                </small>
              </span>
            </div>
          ))}
        </div>
      </section>
      <section className="panel admin-section" aria-label="조치 이력">
        <h2>조치 이력</h2>
        <div className="admin-list">
          {member.history.length === 0 && (
            <p className="admin-meta">기록된 조치가 없습니다. 조치 기록은 이 기능을 배포한 뒤부터 남습니다.</p>
          )}
          {member.history.map((log) => (
            <div className="admin-row" key={log.id}>
              <span className="admin-meta">{formatDateTime(log.createdAt)}</span>
              <span style={{ minWidth: 0 }}>
                <p>{ACTION_LABEL[log.action] ?? log.action}</p>
                <small>{log.actorNickname ? `처리 ${log.actorNickname}` : "처리자 기록 없음"}</small>
              </span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
