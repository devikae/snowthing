// IKA-98 슬로프 제원 비교 화면이 쓰는 타입과 표시 규칙.
// 제원 값이 null이면 공식 자료에 없는 값이라 "미공개"로 보여 준다. 추정해서 채우지 않는다.

export type Country = "KR" | "JP";
export type Difficulty = "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";
export type Relation =
  | "MERGE"
  | "BRANCH"
  | "CONTINUE"
  | "STARTS_FROM"
  | "SHARE_START"
  | "SHARE_END"
  | "CROSS"
  | "PARALLEL";

export type ResortInfo = { code: string; name: string; region: string; country: Country };

export type SlopeItem = {
  id: number;
  name: string;
  nameLocal: string | null;
  officialDifficulty: string | null;
  difficulty: Difficulty;
  lengthM: number | null;
  avgGradient: number | null;
  maxGradient: number | null;
  widthM: number | null;
  liftName: string | null;
  osmWayIds: number[];
  sourceUrl: string;
  checkedAt: string;
};

export type ResortSummary = ResortInfo & {
  slopeCount: number;
  longestLengthM: number | null;
  steepestMaxGradient: number | null;
};

export type Connection = { slopeId: number; relatedSlopeId: number; relation: Relation; distanceM: number | null };

export type ResortDetail = { resort: ResortInfo; slopes: SlopeItem[]; connections: Connection[] };

export type Similar = {
  resort: ResortInfo;
  slope: SlopeItem;
  metric: "AVG" | "MAX";
  baseGradient: number;
  gradient: number;
  gradientDiff: number;
  lengthDiff: number | null;
};

export type SlopeDetail = {
  resort: ResortInfo;
  slope: SlopeItem;
  neighbors: { id: number; name: string; difficulty: Difficulty; relation: Relation; distanceM: number | null }[];
  similar: Similar[];
};

export type CompareItem = {
  resort: ResortInfo;
  slopeCount: number;
  totalLengthM: number | null;
  longestLengthM: number | null;
  steepestMaxGradient: number | null;
  meanAvgGradient: number | null;
  avgGradientCount: number;
  difficultyCounts: Record<Difficulty, number>;
};

// 코스 맵 파일 (frontend/public/slope-maps). 좌표는 미터이고 y가 클수록 아래(베이스 쪽)다.
export type MapWay = {
  id: number;
  kind: "piste" | "lift";
  name: string | null;
  difficulty: string | null;
  liftType: string | null;
  // 공식 슬로프맵을 보고 직접 그린 선(OSM에 선이 없는 코스)
  drawn?: boolean;
  pts: [number, number][];
};
export type SlopeMap = { resortCode: string; attribution: string; width: number; height: number; ways: MapWay[] };

export const COUNTRY_LABEL: Record<Country, string> = { KR: "국내", JP: "일본" };

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  BEGINNER: "초급",
  INTERMEDIATE: "중급",
  ADVANCED: "상급",
  EXPERT: "최상급",
};

export const DIFFICULTY_ORDER: Difficulty[] = ["BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"];

// OSM 난이도 표기를 색 묶음으로 바꾼다. 우리 제원과 연결되지 않은 주변 선에만 쓴다.
export function osmDifficulty(value: string | null): Difficulty | null {
  switch (value) {
    case "novice":
    case "easy":
      return "BEGINNER";
    case "intermediate":
      return "INTERMEDIATE";
    case "advanced":
      return "ADVANCED";
    case "expert":
    case "freeride":
    case "extreme":
      return "EXPERT";
    default:
      return null;
  }
}

export function relationLabel(relation: Relation, distanceM: number | null): string {
  switch (relation) {
    case "MERGE":
      return "이 코스로 합류";
    case "BRANCH":
      return "이 코스에서 갈라짐";
    case "CONTINUE":
      return "도착 후 이어짐";
    case "STARTS_FROM":
      return "여기서 갈라져 시작";
    case "SHARE_START":
      return "출발점 공유";
    case "SHARE_END":
      return "도착점 공유";
    case "CROSS":
      return "교차";
    case "PARALLEL":
      return distanceM ? `나란히 (약 ${distanceM}m)` : "나란히";
  }
}

export function resortSlug(code: string) {
  return code.toLowerCase().replaceAll("_", "-");
}

export function codeFromSlug(slug: string) {
  return slug.toUpperCase().replaceAll("-", "_");
}

export function slopePath(resortCode: string, slopeId: number) {
  return `/slopes/${resortSlug(resortCode)}/${slopeId}`;
}

export function formatLength(value: number | null) {
  return value == null ? "미공개" : `${value.toLocaleString("ko-KR")}m`;
}

export function formatGradient(value: number | null) {
  return value == null ? "미공개" : `${Number(value).toFixed(1)}°`;
}

export function displayName(slope: Pick<SlopeItem, "name" | "nameLocal">) {
  return slope.nameLocal && slope.nameLocal !== slope.name ? `${slope.name} (${slope.nameLocal})` : slope.name;
}

// 일본 리조트의 공식 등급(初級 등)은 읽기 어려워 우리 묶음 이름을 앞에 둔다.
export function difficultyText(slope: Pick<SlopeItem, "difficulty" | "officialDifficulty">, country: Country) {
  const label = DIFFICULTY_LABEL[slope.difficulty];
  if (!slope.officialDifficulty) return label;
  return country === "JP" ? `${label} (${slope.officialDifficulty})` : slope.officialDifficulty;
}
