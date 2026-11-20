"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import styles from "./slopeSpecs.module.css";
import {
  osmDifficulty,
  resortSlug,
  slopePath,
  type Connection,
  type Difficulty,
  type MapWay,
  type SlopeItem,
  type SlopeMap,
} from "./specs";

// ---------- 코스 맵 파일 불러오기 ----------

type MapState = { status: "loading" } | { status: "missing" } | { status: "ready"; map: SlopeMap };

const mapCache = new Map<string, Promise<SlopeMap | null>>();

function loadMap(resortCode: string) {
  const slug = resortSlug(resortCode);
  let pending = mapCache.get(slug);
  if (!pending) {
    pending = fetch(`/slope-maps/${slug}.json`)
      .then((response) => (response.ok ? (response.json() as Promise<SlopeMap>) : null))
      .catch(() => null);
    mapCache.set(slug, pending);
  }
  return pending;
}

export function useSlopeMap(resortCode: string): MapState {
  const [state, setState] = useState<{ code: string; value: MapState } | null>(null);
  useEffect(() => {
    let alive = true;
    loadMap(resortCode).then((map) => {
      if (alive) setState({ code: resortCode, value: map ? { status: "ready", map } : { status: "missing" } });
    });
    return () => {
      alive = false;
    };
  }, [resortCode]);
  return state && state.code === resortCode ? state.value : { status: "loading" };
}

// ---------- 기하 ----------

type Pt = [number, number];

function segNearest(p: Pt, a: Pt, b: Pt): [number, Pt] {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const length = dx * dx + dy * dy;
  const t = length ? Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length)) : 0;
  const q: Pt = [a[0] + t * dx, a[1] + t * dy];
  return [Math.hypot(p[0] - q[0], p[1] - q[1]), q];
}

function nearestOnLines(p: Pt, lines: Pt[][]): [number, Pt] {
  let best: [number, Pt] = [Infinity, p];
  for (const line of lines) {
    for (let i = 0; i < line.length - 1; i += 1) {
      const result = segNearest(p, line[i], line[i + 1]);
      if (result[0] < best[0]) best = result;
    }
  }
  return best;
}

// 두 슬로프가 가장 가까운 곳. 합류와 교차 지점 표시에 쓴다.
function closestPoint(a: Pt[][], b: Pt[][]): Pt {
  let best: [number, Pt] = [Infinity, a[0][0]];
  for (const line of b) {
    for (const p of line) {
      const result = nearestOnLines(p, a);
      if (result[0] < best[0]) best = result;
    }
  }
  return best[1];
}

function slopeEnds(lines: Pt[][]): [Pt, Pt] {
  const points = lines.flatMap((line) => [line[0], line[line.length - 1]]);
  const start = points.reduce((top, p) => (p[1] < top[1] ? p : top));
  const end = points.reduce((bottom, p) => (p[1] > bottom[1] ? p : bottom));
  return [start, end];
}

const path = (pts: Pt[]) => "M" + pts.map((p) => `${p[0]},${p[1]}`).join("L");

const colorVar = (difficulty: Difficulty | null) => `var(--slope-${(difficulty ?? "NONE").toLowerCase()})`;

function wayColor(way: MapWay, slopeByWay: Map<number, SlopeItem>) {
  return colorVar(slopeByWay.get(way.id)?.difficulty ?? osmDifficulty(way.difficulty));
}

function indexWays(slopes: SlopeItem[]) {
  const slopeByWay = new Map<number, SlopeItem>();
  for (const slope of slopes) for (const id of slope.osmWayIds) slopeByWay.set(id, slope);
  return slopeByWay;
}

function normName(value: string | null) {
  return (value ?? "").toLowerCase().replace(/\(.*?\)/g, "").replace(/[\s\-_.]/g, "");
}

// ---------- 리조트 전체 맵 ----------

export function ResortMap({ resortCode, slopes }: { resortCode: string; slopes: SlopeItem[] }) {
  const state = useSlopeMap(resortCode);
  const slopeByWay = useMemo(() => indexWays(slopes), [slopes]);

  if (state.status === "loading") return <div className={styles.mapEmpty}>코스 맵을 불러오는 중입니다.</div>;
  if (state.status === "missing") return <div className={styles.mapEmpty}>이 리조트는 아직 코스 맵이 없습니다.</div>;
  const { map } = state;
  const pad = Math.max(map.width, map.height) * 0.04;
  const unit = Math.max(map.width, map.height) / 900;

  return (
    <figure className={styles.mapFrame}>
      <svg
        className={styles.mapSvg}
        viewBox={`${-pad} ${-pad} ${map.width + pad * 2} ${map.height + pad * 2}`}
        role="img"
        aria-label="리조트 전체 코스 맵"
      >
        {map.ways
          .filter((way) => way.kind === "lift")
          .map((way) => (
            <path
              key={way.id}
              d={path(way.pts)}
              fill="none"
              stroke="var(--slope-lift)"
              strokeWidth={1.4 * unit}
              strokeDasharray={`${6 * unit} ${5 * unit}`}
            />
          ))}
        {map.ways
          .filter((way) => way.kind === "piste")
          .map((way) => {
            const slope = slopeByWay.get(way.id);
            const line = (
              <path
                key={way.id}
                d={path(way.pts)}
                fill="none"
                stroke={wayColor(way, slopeByWay)}
                strokeWidth={(slope ? 4.5 : 2.5) * unit}
                strokeLinecap="round"
                strokeLinejoin="round"
                opacity={slope ? 1 : 0.55}
              />
            );
            return slope ? (
              <Link key={way.id} href={slopePath(resortCode, slope.id)} aria-label={`${slope.name} 코스 보기`}>
                {line}
                <path d={path(way.pts)} fill="none" stroke="transparent" strokeWidth={16 * unit} />
              </Link>
            ) : (
              line
            );
          })}
        {placeMapLabels(map, slopeByWay, unit).map((label) => (
          <text
            key={label.key}
            x={label.x}
            y={label.y}
            className={styles.mapLabel}
            fontSize={15 * unit}
            strokeWidth={3.5 * unit}
          >
            {label.text}
          </text>
        ))}
      </svg>
      <figcaption className={styles.attribution}>{map.attribution}</figcaption>
    </figure>
  );
}

// 슬로프 이름을 선 위 여러 자리에 놓아 보고, 다른 이름과 겹치지 않는 첫 자리에 둔다. 자리가 없으면 이름을 생략한다.
function placeMapLabels(map: SlopeMap, slopeByWay: Map<number, SlopeItem>, unit: number) {
  const fs = 15 * unit;
  const boxes: [number, number, number, number][] = [];
  const labels: { key: string; x: number; y: number; text: string }[] = [];
  const seen = new Set<number>();
  const textWidth = (text: string) =>
    Array.from(text).reduce((sum, ch) => sum + (/[\u0000-ÿ]/.test(ch) ? 0.6 : 1) * fs, 0);
  const pistes = map.ways.filter((way) => way.kind === "piste" && slopeByWay.has(way.id));
  // 긴 선을 가진 슬로프부터 자리를 잡는다.
  pistes.sort((a, b) => b.pts.length - a.pts.length);
  for (const way of pistes) {
    const slope = slopeByWay.get(way.id)!;
    if (seen.has(slope.id)) continue;
    const w = textWidth(slope.name);
    const h = fs * 1.1;
    for (const t of [0.5, 0.35, 0.65, 0.2, 0.8]) {
      const p = way.pts[Math.floor((way.pts.length - 1) * t)];
      const x = p[0] + 5 * unit;
      const y = p[1] - h / 2;
      const hit = boxes.some(([bx, by, bw, bh]) => x < bx + bw && x + w > bx && y < by + bh && y + h > by);
      if (!hit) {
        boxes.push([x, y, w, h]);
        labels.push({ key: `label-${way.id}`, x, y: y + fs * 0.85, text: slope.name });
        seen.add(slope.id);
        break;
      }
    }
  }
  return labels;
}

// ---------- 슬로프 코스 카드 ----------

type CourseCardProps = {
  resortCode: string;
  slope: SlopeItem;
  slopes: SlopeItem[];
  connections: Connection[];
  big?: boolean;
};

type Pill = { key: string; x: number; y: number; w: number; h: number; text: string; color: string; strong: boolean };

export function CourseCard({ resortCode, slope, slopes, connections, big = false }: CourseCardProps) {
  const state = useSlopeMap(resortCode);
  const drawing = useMemo(
    () => (state.status === "ready" ? layoutCourse(state.map, slope, slopes, connections, big) : null),
    [state, slope, slopes, connections, big],
  );

  if (state.status === "loading") return <div className={big ? styles.courseEmptyBig : styles.courseEmpty} />;
  if (!drawing) {
    return (
      <div className={big ? styles.courseEmptyBig : styles.courseEmpty}>
        <span>코스 맵 없음</span>
      </div>
    );
  }
  const d = drawing;
  return (
    <svg className={styles.courseSvg} viewBox={d.viewBox} role="img" aria-label={`${slope.name} 코스 맵`}>
      {d.others.map((way) => (
        <path key={way.id} d={path(way.pts)} fill="none" stroke="var(--slope-context)" strokeWidth={(big ? 2.2 : 1.4) * d.s} strokeLinecap="round" />
      ))}
      {d.lifts.map(({ way, served }) => (
        <path
          key={way.id}
          d={path(way.pts)}
          fill="none"
          stroke={served ? "var(--slope-lift-strong)" : "var(--slope-lift)"}
          strokeWidth={(served ? 1.8 : 1.2) * d.s}
          strokeDasharray={`${6 * d.s} ${5 * d.s}`}
        />
      ))}
      {d.related.map(({ way, color }) => (
        <path key={way.id} d={path(way.pts)} fill="none" stroke={color} strokeOpacity={0.8} strokeWidth={(big ? 3.6 : 2.4) * d.s} strokeLinecap="round" strokeLinejoin="round" />
      ))}
      {d.mine.map((line, index) => (
        <path key={`casing-${index}`} d={path(line)} fill="none" stroke="var(--snow-canvas, #fff)" strokeWidth={(big ? 11 : 7.5) * d.s} strokeLinecap="round" strokeLinejoin="round" />
      ))}
      {d.mine.map((line, index) => (
        <path key={`line-${index}`} d={path(line)} fill="none" stroke={d.color} strokeWidth={(big ? 6.5 : 4.5) * d.s} strokeLinecap="round" strokeLinejoin="round" />
      ))}
      <circle cx={d.start[0]} cy={d.start[1]} r={d.r} fill="var(--snow-canvas, #fff)" stroke={d.color} strokeWidth={2.5 * d.s} />
      <circle cx={d.end[0]} cy={d.end[1]} r={d.r} fill={d.color} stroke="var(--snow-canvas, #fff)" strokeWidth={2 * d.s} />
      {d.joints.map((joint) => (
        <circle key={joint.key} cx={joint.at[0]} cy={joint.at[1]} r={4.5 * d.s} fill={joint.color} stroke="var(--snow-canvas, #fff)" strokeWidth={2 * d.s} />
      ))}
      {d.pills.map((pill) => (
        <g key={pill.key}>
          <rect
            x={pill.x}
            y={pill.y}
            width={pill.w}
            height={pill.h}
            rx={pill.h / 2}
            fill={pill.strong ? pill.color : "var(--snow-canvas, #fff)"}
            stroke={pill.color}
            strokeWidth={1.2 * d.s}
          />
          <text
            x={pill.x + pill.w / 2}
            y={pill.y + pill.h * 0.68}
            textAnchor="middle"
            fontSize={d.fs}
            fontWeight={600}
            fill={pill.strong ? "#fff" : pill.color === colorVar("ADVANCED") ? "var(--snow-ink, #1e293b)" : pill.color}
          >
            {pill.text}
          </text>
        </g>
      ))}
    </svg>
  );
}

function layoutCourse(map: SlopeMap, slope: SlopeItem, slopes: SlopeItem[], connections: Connection[], big: boolean) {
  const byId = new Map(map.ways.map((way) => [way.id, way]));
  const mine = slope.osmWayIds.map((id) => byId.get(id)?.pts).filter((pts): pts is Pt[] => !!pts);
  if (mine.length === 0) return null;

  const slopeById = new Map(slopes.map((item) => [item.id, item]));
  const slopeByWay = indexWays(slopes);
  const myConnections = connections.filter((connection) => connection.slopeId === slope.id);
  const relatedIds = new Set(myConnections.map((connection) => connection.relatedSlopeId));
  const myWays = new Set(slope.osmWayIds);

  // 화면 범위: 이 슬로프를 감싸는 사각형에 여백을 두고 카드 비율에 맞춘다.
  const xs = mine.flat().map((p) => p[0]);
  const ys = mine.flat().map((p) => p[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
  const pad = Math.max(x1 - x0, y1 - y0) * 0.16 + 40;
  let W = x1 - x0 + pad * 2;
  let H = y1 - y0 + pad * 2;
  const [rmin, rmax] = big ? [0.8, 1.35] : [0.85, 0.85];
  if (W / H < rmin) W = H * rmin;
  else if (W / H > rmax) H = W / rmax;
  if (!big) {
    if (W / H > rmin) H = W / rmin;
    else W = H * rmin;
  }
  const vx = (x0 + x1) / 2 - W / 2;
  const vy = (y0 + y1) / 2 - H / 2;
  const s = W / (big ? 560 : 160);
  const color = colorVar(slope.difficulty);

  const pistes = map.ways.filter((way) => way.kind === "piste" && !myWays.has(way.id));
  const related = pistes
    .filter((way) => {
      const owner = slopeByWay.get(way.id);
      return owner && relatedIds.has(owner.id);
    })
    .map((way) => ({ way, color: wayColor(way, slopeByWay) }));
  const relatedWayIds = new Set(related.map(({ way }) => way.id));
  const others = pistes.filter((way) => !relatedWayIds.has(way.id));

  // 타는 리프트는 공식 리프트 이름이 OSM 리프트 이름과 맞을 때만 강조한다. 추정하지 않는다.
  const liftNames = (slope.liftName ?? "").split(/[,/]/).map(normName).filter(Boolean);
  const lifts = map.ways
    .filter((way) => way.kind === "lift")
    .map((way) => ({ way, served: liftNames.length > 0 && liftNames.includes(normName(way.name)) }));

  const [start, end] = slopeEnds(mine);
  const r = (big ? 6 : 4.5) * s;
  const fs = 11.5 * s;
  const joints: { key: string; at: Pt; color: string }[] = [];
  const pills: Pill[] = [];

  if (big) {
    const relatedLines = (id: number) =>
      (slopeById.get(id)?.osmWayIds ?? []).map((wayId) => byId.get(wayId)?.pts).filter((pts): pts is Pt[] => !!pts);
    for (const connection of myConnections) {
      if (connection.relation === "PARALLEL") continue;
      const lines = relatedLines(connection.relatedSlopeId);
      const other = slopeById.get(connection.relatedSlopeId);
      if (lines.length === 0 || !other) continue;
      joints.push({ key: `j-${connection.relatedSlopeId}`, at: closestPoint(mine, lines), color: colorVar(other.difficulty) });
    }

    const ph = fs * 1.7;
    const hit = (x: number, y: number, w: number, h: number) =>
      pills.some((p) => x < p.x + p.w && x + w > p.x && y < p.y + p.h && y + h > p.y) ||
      x < vx + 2 * s ||
      y < vy + 2 * s ||
      x + w > vx + W - 2 * s ||
      y + h > vy + H - 2 * s;
    const offMain = (x: number, y: number, w: number, h: number) =>
      ([
        [x, y],
        [x + w, y],
        [x, y + h],
        [x + w, y + h],
        [x + w / 2, y + h / 2],
      ] as Pt[]).every((p) => nearestOnLines(p, mine)[0] > 7 * s);
    const place = (key: string, text: string, at: Pt, pillColor: string, strong: boolean) => {
      const w = text.length * fs * 0.92 + fs * 1.1;
      const tries: Pt[] = [
        [8 * s, -ph / 2],
        [-w - 8 * s, -ph / 2],
        [-w / 2, -ph - 8 * s],
        [-w / 2, 8 * s],
        [12 * s, -ph - 4 * s],
        [-w - 12 * s, 4 * s],
      ];
      for (const [dx, dy] of tries) {
        const x = at[0] + dx;
        const y = at[1] + dy;
        if (!hit(x, y, w, ph) && (strong || offMain(x, y, w, ph))) {
          pills.push({ key, x, y, w, h: ph, text, color: pillColor, strong });
          return true;
        }
      }
      return false;
    };
    place("start", "출발", start, color, true);
    place("end", "도착", end, color, true);
    for (const connection of myConnections.slice(0, 8)) {
      const other = slopeById.get(connection.relatedSlopeId);
      if (!other) continue;
      const candidates = other.osmWayIds
        .flatMap((wayId) => byId.get(wayId)?.pts ?? [])
        .filter((p) => p[0] > vx && p[0] < vx + W && p[1] > vy && p[1] < vy + H)
        .map((p) => ({ p, d: nearestOnLines(p, mine)[0] }))
        .filter((candidate) => candidate.d > 10 * s)
        .sort((a, b) => a.d - b.d);
      for (const candidate of candidates) {
        if (place(`n-${other.id}`, other.name, candidate.p, colorVar(other.difficulty), false)) break;
      }
    }
    for (const { way, served } of lifts) {
      if (!served || !way.name) continue;
      const mid = way.pts[Math.floor(way.pts.length / 2)];
      if (mid[0] > vx && mid[0] < vx + W && mid[1] > vy && mid[1] < vy + H) {
        place(`lift-${way.id}`, `${way.name} 리프트`, mid, "var(--slope-lift)", false);
      }
    }
  }

  return { viewBox: `${vx} ${vy} ${W} ${H}`, s, r, fs, color, mine, others, related, lifts, start, end, joints, pills };
}
