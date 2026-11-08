// 파비콘(app/icon.svg)과 같은 눈꽃 마크. 타일은 글자색, 눈꽃은 배경색을 따라가서 다크 모드에서는 색이 뒤집힌다.
const FLAKE_PATH =
  "M32 32L32 12M26.43 14.48L32 19.5L37.57 14.48M32 32L49.32 22M44.38 18.41L42.83 25.75L49.96 28.07M32 32L49.32 42M49.96 35.93L42.83 38.25L44.38 45.59M32 32L32 52M37.57 49.52L32 44.5L26.43 49.52M32 32L14.68 42M19.62 45.59L21.17 38.25L14.04 35.93M32 32L14.68 22M14.04 28.07L21.17 25.75L19.62 18.41";

export function SnowflakeMark() {
  return (
    <svg className="snowflake-mark" viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <rect width="64" height="64" rx="15" fill="currentColor" />
      <path
        d={FLAKE_PATH}
        fill="none"
        stroke="var(--snow-canvas)"
        strokeWidth="5.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
