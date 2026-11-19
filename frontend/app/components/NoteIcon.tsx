/**
 * 덮개를 접어 넣은 쪽지 모양. 헤더 쪽지함과 쪽지 보내기 버튼에 쓴다.
 * 옆의 Material Symbols 아이콘과 굵기와 크기를 맞추려고 24칸 viewBox에 선으로 그리고,
 * 색은 currentColor를 따라가서 라이트와 다크 모드 모두 글자색과 같게 보인다.
 */
export function NoteIcon({ size = 20 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {/* 접힌 종이 몸통 */}
      <rect x="3.5" y="6" width="17" height="12" rx="1.5" />
      {/* 안으로 접어 넣은 삼각 덮개 */}
      <path d="m3.5 7 8.5 6.5L20.5 7" />
    </svg>
  );
}
