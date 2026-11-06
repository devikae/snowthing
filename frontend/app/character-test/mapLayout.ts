import { LEVELS, type CharacterLabel, type LevelCode, type Sport } from "./data";

// 화면(SVG)과 저장 이미지(canvas)가 같은 배치를 쓰도록 좌표를 한 곳에서 계산한다.
export const HEAD_WIDTH = 32;
export const ME_HEAD_WIDTH = 42;
export const ME_RADIUS = 32;
// 캐릭터 그림(40칸)에서 얼굴 부분만 쓴다.
export const HEAD_CROP = { x: 3 / 40, y: 0, width: 34 / 40, height: 26 / 40 };
const NODE_WIDTH = 46;
const NODE_HEIGHT = 50;
const INNER_RADIUS = 60;
const EDGE_SPACE = 26;
const LABEL_HEIGHT = 16;
const TREE_GRID = 30;
const TREE_MARGIN = 14;
const GOLDEN_ANGLE = 2.399963;
const ANGLE_STEPS = 72;
const RADIUS_SHIFTS = [0, 6, -6, 12, -12, 18, -18, 24, -24, 32, -32, 40, -40, 50, -50, 60, 72, 84, 96];
const NICKNAME_LIMIT = 5;
// 단계마다 원의 폭은 같고, 단계 안의 위치는 점수로 정한다. 서버의 단계 경계와 맞춘다.
const BAND_SCORES: Record<LevelCode, { top: number; bottom: number }> = {
  BEST: { top: 100, bottom: 78 },
  GOOD: { top: 78, bottom: 68 },
  NEUTRAL: { top: 68, bottom: 57 },
  BICKER: { top: 57, bottom: 45 },
  APART: { top: 45, bottom: 16 },
};

export interface MapInputPerson {
  participantPublicId: string;
  nickname: string;
  sport: Sport;
  character: CharacterLabel;
  score: number;
  habitGaps: number[];
}

export interface MapInputLevel {
  level: LevelCode;
  participants: MapInputPerson[];
}

export interface MapBand {
  code: LevelCode;
  label: string;
  count: number;
  inner: number;
  outer: number;
}

export interface PersonNode {
  x: number;
  y: number;
  id: string;
  name: string;
  jobLabel: CharacterLabel;
  sport: Sport;
  score: number;
  level: LevelCode;
  habitGaps: number[];
}

export interface RingLabel {
  code: LevelCode;
  text: string;
  x: number;
  y: number;
  width: number;
}

export interface MapLayout {
  size: number;
  center: number;
  bands: MapBand[];
  labels: RingLabel[];
  trees: { x: number; y: number; s: number }[];
  people: PersonNode[];
  empties: { x: number; y: number; id: string }[];
}

function clip(name: string) {
  return name.length > NICKNAME_LIMIT ? `${name.slice(0, NICKNAME_LIMIT - 1)}…` : name;
}

// 사람이 적으면 지도를 작게 잡아 같은 화면 폭에서 얼굴과 글자가 크게 보이게 한다.
function mapSize(count: number) {
  if (count <= 12) return 360;
  if (count <= 28) return 440;
  return 520;
}

/**
 * 내가 정중앙이고 가까울수록 잘 맞는다. 안쪽부터 점수 순서로 황금각만큼 돌려 놓아 고르게 퍼뜨리고,
 * 겹치면 같은 원을 따라 빈자리를 찾는다. 원 안에 자리가 없을 때만 안팎으로 옮겨 거리의 뜻을 지킨다.
 */
export function layoutMap(levels: MapInputLevel[], hideNames: boolean): MapLayout {
  const entries = LEVELS.flatMap((meta) => (levels.find((level) => level.level === meta.code)?.participants ?? []).map((person) => ({ person, level: meta.code })));
  const size = mapSize(entries.length);
  const center = size / 2;
  const outerRadius = center - EDGE_SPACE;
  const bandWidth = (outerRadius - INNER_RADIUS) / LEVELS.length;

  const bands = LEVELS.map((meta, index): MapBand => ({
    code: meta.code,
    label: meta.label,
    count: entries.filter((entry) => entry.level === meta.code).length,
    inner: index === 0 ? 0 : INNER_RADIUS + bandWidth * index,
    outer: INNER_RADIUS + bandWidth * (index + 1),
  }));

  // 단계 이름은 각 원의 위쪽 가운데에 두고, 사람이 그 자리를 가리지 않게 먼저 자리를 차지한다.
  const labels = bands.map((band): RingLabel => {
    return { code: band.code, text: band.label, x: center, y: center - band.outer + 10, width: band.label.length * 9 + 8 };
  });
  // 안쪽 사람부터 놓는다. 자기 점수 자리가 차 있으면 같은 원을 따라 돌며 빈자리를 찾고, 그래도 없으면 조금씩 안팎으로 옮긴다.
  const placed: { x: number; y: number }[] = [];
  const isFree = (x: number, y: number) =>
    placed.every((other) => Math.abs(other.x - x) >= NODE_WIDTH || Math.abs(other.y - y) >= NODE_HEIGHT)
    && labels.every((label) => Math.abs(label.x - x) >= (label.width + NODE_WIDTH) / 2 || Math.abs(label.y - y) >= (LABEL_HEIGHT + NODE_HEIGHT) / 2);
  const nodes = entries.map(({ person, level }, index) => {
    const range = BAND_SCORES[level];
    const bandIndex = LEVELS.findIndex((meta) => meta.code === level);
    const within = (range.top - Math.min(range.top, Math.max(range.bottom, person.score))) / (range.top - range.bottom);
    const radius = INNER_RADIUS + bandWidth * (bandIndex + within);
    const startAngle = index * GOLDEN_ANGLE;
    for (const shift of RADIUS_SHIFTS) {
      const tryRadius = radius + shift;
      if (tryRadius < INNER_RADIUS || tryRadius > outerRadius) continue;
      for (let step = 0; step < ANGLE_STEPS; step += 1) {
        const angle = startAngle + (step * Math.PI * 2) / ANGLE_STEPS;
        const x = center + tryRadius * Math.cos(angle);
        const y = center + tryRadius * Math.sin(angle);
        if (isFree(x, y)) {
          placed.push({ x, y });
          return { person, level, x, y };
        }
      }
    }
    // 50명 안에서는 오지 않지만, 자리가 정말 없으면 원래 자리에 겹쳐 그린다.
    const x = center + radius * Math.cos(startAngle);
    const y = center + radius * Math.sin(startAngle);
    placed.push({ x, y });
    return { person, level, x, y };
  });

  const people = nodes.map(({ person, level, x, y }, index): PersonNode => ({
    x: Math.round(x),
    y: Math.round(y),
    id: person.participantPublicId,
    name: hideNames ? `친구 ${index + 1}` : clip(person.nickname),
    jobLabel: person.character,
    sport: person.sport,
    score: person.score,
    level,
    habitGaps: person.habitGaps,
  }));

  const empties = people.length > 0 ? [] : [
    { x: center, y: center - 92, id: "empty-up" },
    { x: center - 92, y: center, id: "empty-left" },
    { x: center + 92, y: center, id: "empty-right" },
    { x: center, y: center + 92, id: "empty-down" },
  ];
  // 원 바깥 네 모서리에 나무를 심는다. 새로고침해도 같은 자리에 있도록 고정된 수식으로 어긋나게 놓는다.
  const trees: MapLayout["trees"] = [];
  for (let row = 0; row * TREE_GRID <= size; row += 1) {
    for (let column = 0; column * TREE_GRID <= size; column += 1) {
      const x = column * TREE_GRID + ((row * 13 + column * 7) % 11) - 5;
      const y = row * TREE_GRID + ((row * 5 + column * 17) % 13) - 6;
      if (Math.hypot(x - center, y - center) < outerRadius + 16) continue;
      if (x < TREE_MARGIN || y < TREE_MARGIN || x > size - TREE_MARGIN || y > size - TREE_MARGIN) continue;
      trees.push({ x, y, s: 8 + ((row * 3 + column * 5) % 4) * 1.5 });
    }
  }
  return { size, center, bands, labels, trees, people, empties };
}

// 나무 그림. 화면과 저장 이미지가 같은 경로를 쓴다. canvas는 Path2D로 그대로 그릴 수 있다.
export function treePaths(trees: MapLayout["trees"]) {
  let pine = "";
  let cap = "";
  let trunk = "";
  for (const { x, y, s } of trees) {
    pine += `M ${x} ${y - s} L ${x + s * 0.55} ${y - s * 0.2} L ${x + s * 0.3} ${y - s * 0.2} L ${x + s * 0.75} ${y + s * 0.55} L ${x - s * 0.75} ${y + s * 0.55} L ${x - s * 0.3} ${y - s * 0.2} L ${x - s * 0.55} ${y - s * 0.2} Z `;
    cap += `M ${x} ${y - s} L ${x + s * 0.28} ${y - s * 0.58} L ${x - s * 0.28} ${y - s * 0.58} Z `;
    trunk += `M ${x - s * 0.12} ${y + s * 0.55} H ${x + s * 0.12} V ${y + s * 0.85} H ${x - s * 0.12} Z `;
  }
  return { pine, cap, trunk };
}
