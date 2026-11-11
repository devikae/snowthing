// 목록 제목 앞 아이콘. 두 아이콘은 같은 틀(18x15 둥근 사각형)을 써서 크기가 같아 보인다.
// 사진이 있는 글은 강조색 사진 아이콘, 없는 글은 말머리와 같은 회색 글 아이콘이다.
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
      <rect x="3" y="4.5" width="18" height="15" rx="3" />
      <path d="M7.5 9.5h9M7.5 14.5h6" />
    </svg>
  );
}
