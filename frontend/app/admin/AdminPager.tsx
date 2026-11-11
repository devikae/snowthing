"use client";

/** 관리자 목록 공통 쪽 이동. 전체 쪽 수 없이 다음 쪽 여부만 받는다. */
export function AdminPager({
  page,
  hasNext,
  loading,
  onChange,
}: {
  page: number;
  hasNext: boolean;
  loading: boolean;
  onChange: (page: number) => void;
}) {
  return (
    <div className="admin-actions" style={{ justifyContent: "center" }}>
      <button className="admin-button" type="button" disabled={page === 0 || loading} onClick={() => onChange(page - 1)}>
        이전
      </button>
      <span className="admin-meta">{page + 1}쪽</span>
      <button className="admin-button" type="button" disabled={!hasNext || loading} onClick={() => onChange(page + 1)}>
        다음
      </button>
    </div>
  );
}
