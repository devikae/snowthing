// 목록 제목 앞 아이콘. 사진이 있는 글은 색 있는 사진 아이콘, 없는 글은 회색 글 아이콘으로 구분한다.
export function PostImageIcon({ hasImage }: { hasImage: boolean }) {
  if (hasImage) {
    return (
      <svg className="post-type-icon has-image" role="img" aria-label="사진 첨부" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="4.5" width="18" height="15" rx="3" />
        <circle cx="8.5" cy="9.5" r="1.6" fill="currentColor" stroke="none" />
        <path d="m4 17.5 4.5-4.5 3 3 3.5-4 5 5.5" />
      </svg>
    );
  }
  return (
    <svg className="post-type-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="3.5" width="16" height="17" rx="3" />
      <path d="M8 9h8M8 12.5h8M8 16h5" />
    </svg>
  );
}
