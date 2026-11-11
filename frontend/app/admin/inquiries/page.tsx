"use client";

import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { API_ENDPOINTS } from "../../lib/api";
import { AdminPager } from "../AdminPager";
import { type AdminInquiryItem, type AdminPage, formatDateTime } from "../adminTypes";
import { useAdminFetch } from "../useAdminFetch";

const FILTERS = [
  { value: "UNANSWERED", label: "답변 대기" },
  { value: "ANSWERED", label: "답변 완료" },
  { value: "ALL", label: "전체" },
];

/** 1:1 문의 목록 (IKA-78). 답변은 문의 글에 관리자 댓글로 달고, 관리자 댓글이 하나라도 있으면 답변 완료다. */
export default function AdminInquiriesPage() {
  const [filter, setFilter] = useState("UNANSWERED");
  const [page, setPage] = useState(0);
  const list = useAdminFetch<AdminPage<AdminInquiryItem>>(
    API_ENDPOINTS.admin.inquiries(filter, page),
    "문의 목록을 불러오지 못했습니다.",
  );
  const data = list.data;

  return (
    <>
      <PageHeading title="1:1 문의" description="회원 문의를 확인합니다. 답변은 문의 글을 열어 댓글로 남기면 됩니다." />
      <section className="panel admin-section" aria-label="문의 목록">
        <div className="admin-filter" role="group" aria-label="답변 상태">
          {FILTERS.map((item) => (
            <button
              key={item.value}
              type="button"
              aria-pressed={filter === item.value}
              onClick={() => {
                if (filter === item.value) return;
                list.setLoading(true);
                setPage(0);
                setFilter(item.value);
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        {list.error && (
          <p className="admin-error" role="alert">
            {list.error}
          </p>
        )}
        <div className="admin-list">
          {list.loading && !data && <p className="admin-meta">불러오는 중...</p>}
          {!list.loading && data?.items.length === 0 && <p className="admin-meta">해당하는 문의가 없습니다.</p>}
          {data?.items.map((inquiry) => (
            <div className="admin-row" key={inquiry.publicId}>
              <span className="admin-status" data-status={inquiry.answered ? "APPROVED" : "REJECTED"}>
                {inquiry.answered ? "답변 완료" : "답변 대기"}
              </span>
              <span style={{ minWidth: 0 }}>
                <p style={{ overflowWrap: "anywhere" }}>{inquiry.title}</p>
                <small>
                  {inquiry.writerNickname ?? "알 수 없음"}, 댓글 {inquiry.commentCount}개, {formatDateTime(inquiry.createdAt)}
                </small>
              </span>
              <Link className="admin-button" href={`/posts/${inquiry.publicId}`} style={{ display: "inline-flex", alignItems: "center" }}>
                {inquiry.answered ? "보기" : "답변하기"}
              </Link>
            </div>
          ))}
        </div>
        <AdminPager
          page={page}
          hasNext={!!data?.hasNext}
          loading={list.loading}
          onChange={(next) => {
            list.setLoading(true);
            setPage(next);
          }}
        />
      </section>
    </>
  );
}
