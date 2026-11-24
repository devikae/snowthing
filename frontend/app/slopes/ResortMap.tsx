"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import styles from "./slopeSpecs.module.css";
import { colorVar, indexWays, path, useSlopeMap, wayColor, type Pt } from "./SlopeMapViews";
import { slopePath, type Difficulty, type MapWay, type SlopeItem, type SlopeMap } from "./specs";

// 리조트 전체 코스 맵. 난이도 칩으로 걸러 보기, 슬로프를 눌러 강조하고 타는 리프트 보기, 화면 가득 크게 보기를 한다.
// 리프트는 OSM이 아래에서 위로 그리므로 선의 첫 점이 타는 곳이다.

type View = [number, number, number, number];

type Props = {
  resortCode: string;
  slopes: SlopeItem[];
  difficulty: Difficulty | null;
};

export function ResortMap({ resortCode, slopes, difficulty }: Props) {
  const state = useSlopeMap(resortCode);
  const [focus, setFocus] = useState<{ key: string; id: number } | null>(null);
  const [zoomed, setZoomed] = useState(false);
  // 난이도를 바꾸거나 리조트가 바뀌면 누른 슬로프를 푼다.
  const focusKey = `${resortCode}:${difficulty ?? ""}`;
  const focusId = focus && focus.key === focusKey ? focus.id : null;
  const focused = slopes.find((slope) => slope.id === focusId) ?? null;
  const toggle = useCallback(
    (slope: SlopeItem | null) => setFocus((current) => (!slope || (current?.key === focusKey && current.id === slope.id) ? null : { key: focusKey, id: slope.id })),
    [focusKey],
  );

  if (state.status === "loading") return <div className={styles.mapEmpty}>코스 맵을 불러오는 중입니다.</div>;
  if (state.status === "missing") return <div className={styles.mapEmpty}>이 리조트는 아직 코스 맵이 없습니다.</div>;
  const { map } = state;

  return (
    <figure className={styles.mapFrame}>
      <div className={styles.mapStage}>
        <MapCanvas map={map} slopes={slopes} difficulty={difficulty} focused={focused} onSlope={toggle} />
        <button type="button" className={styles.expandButton} onClick={() => setZoomed(true)}>
          <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">open_in_full</span>
          크게 보기
        </button>
      </div>
      <FocusBar resortCode={resortCode} slope={focused} />
      <figcaption className={styles.attribution}>{map.attribution}</figcaption>
      {zoomed && (
        <ZoomOverlay map={map} slopes={slopes} difficulty={difficulty} focused={focused} onSlope={toggle} onClose={() => setZoomed(false)}>
          <FocusBar resortCode={resortCode} slope={focused} />
        </ZoomOverlay>
      )}
    </figure>
  );
}

function FocusBar({ resortCode, slope }: { resortCode: string; slope: SlopeItem | null }) {
  return (
    <div className={styles.focusBar} aria-live="polite">
      {slope ? (
        <>
          <span>
            <strong>
              <i className={styles.dot} data-difficulty={slope.difficulty} aria-hidden="true" /> {slope.name}
            </strong>
            <small>{slope.liftName ? `타는 리프트: ${slope.liftName}` : "리프트 정보가 없습니다"}</small>
          </span>
          <Link href={slopePath(resortCode, slope.id)}>코스 보기</Link>
        </>
      ) : (
        <small>슬로프를 누르면 타는 리프트와 타는 곳(▲)이 표시됩니다.</small>
      )}
    </div>
  );
}

// ---------- 크게 보기 ----------

function fullView(map: SlopeMap): View {
  const pad = Math.max(map.width, map.height) * 0.04;
  return [-pad, -pad, map.width + pad * 2, map.height + pad * 2];
}

type OverlayProps = {
  map: SlopeMap;
  slopes: SlopeItem[];
  difficulty: Difficulty | null;
  focused: SlopeItem | null;
  onSlope: (slope: SlopeItem | null) => void;
  onClose: () => void;
  children: React.ReactNode;
};

function ZoomOverlay({ map, slopes, difficulty, focused, onSlope, onClose, children }: OverlayProps) {
  const stage = useRef<HTMLDivElement>(null);
  const [view, setView] = useState<View | null>(null);
  const pointers = useRef(new Map<number, Pt>());
  const pinch = useRef<number | null>(null);
  const moved = useRef(false);

  // 화면 비율에 맞춘 처음 보기. 맵 전체가 들어가게 한다.
  useEffect(() => {
    const box = stage.current?.getBoundingClientRect();
    if (!box) return;
    const [x, y, w, h] = fullView(map);
    const ratio = box.width / box.height;
    const width = w / h > ratio ? w : h * ratio;
    const height = width / ratio;
    setView([x + w / 2 - width / 2, y + h / 2 - height / 2, width, height]);
  }, [map]);

  useEffect(() => {
    const key = (event: KeyboardEvent) => event.key === "Escape" && onClose();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", key);
    return () => {
      document.body.style.overflow = overflow;
      window.removeEventListener("keydown", key);
    };
  }, [onClose]);

  const limit = fullView(map)[2] * 1.2;
  const zoomAt = (factor: number, cx: number, cy: number) =>
    setView((v) => {
      if (!v) return v;
      const width = Math.min(limit, Math.max(limit / 12, v[2] * factor));
      const k = width / v[2];
      return [cx - (cx - v[0]) * k, cy - (cy - v[1]) * k, width, v[3] * k];
    });
  const toMap = (clientX: number, clientY: number): Pt => {
    const box = stage.current!.getBoundingClientRect();
    const v = view!;
    return [v[0] + ((clientX - box.left) / box.width) * v[2], v[1] + ((clientY - box.top) / box.height) * v[3]];
  };

  // 휠은 passive가 아닌 리스너로 받아야 페이지가 같이 스크롤되지 않는다.
  useEffect(() => {
    const node = stage.current;
    if (!node || !view) return;
    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      const [x, y] = toMap(event.clientX, event.clientY);
      zoomAt(event.deltaY > 0 ? 1.15 : 0.87, x, y);
    };
    node.addEventListener("wheel", wheel, { passive: false });
    return () => node.removeEventListener("wheel", wheel);
  });

  const onPointerDown = (event: React.PointerEvent) => {
    pointers.current.set(event.pointerId, [event.clientX, event.clientY]);
    moved.current = false;
    pinch.current = null;
  };
  const onPointerMove = (event: React.PointerEvent) => {
    const previous = pointers.current.get(event.pointerId);
    if (!previous || !view) return;
    pointers.current.set(event.pointerId, [event.clientX, event.clientY]);
    const box = stage.current!.getBoundingClientRect();
    if (pointers.current.size === 1) {
      const dx = event.clientX - previous[0];
      const dy = event.clientY - previous[1];
      if (!moved.current && Math.hypot(dx, dy) < 3) return;
      if (!moved.current) (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
      moved.current = true;
      setView((v) => (v ? [v[0] - (dx / box.width) * v[2], v[1] - (dy / box.height) * v[3], v[2], v[3]] : v));
    } else if (pointers.current.size === 2) {
      moved.current = true;
      const [a, b] = [...pointers.current.values()];
      const distance = Math.hypot(a[0] - b[0], a[1] - b[1]);
      if (pinch.current) {
        const [x, y] = toMap((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
        zoomAt(pinch.current / distance, x, y);
      }
      pinch.current = distance;
    }
  };
  const onPointerUp = (event: React.PointerEvent) => {
    pointers.current.delete(event.pointerId);
    pinch.current = null;
  };
  // 끌어서 옮긴 뒤 손을 뗄 때는 누른 것으로 보지 않는다.
  const onClickCapture = (event: React.MouseEvent) => {
    if (moved.current) {
      event.stopPropagation();
      moved.current = false;
    }
  };

  const center = () => (view ? ([view[0] + view[2] / 2, view[1] + view[3] / 2] as Pt) : ([0, 0] as Pt));

  return (
    <div className={styles.zoomOverlay} role="dialog" aria-modal="true" aria-label="코스 맵 크게 보기">
      <div
        ref={stage}
        className={styles.zoomStage}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onClickCapture={onClickCapture}
      >
        {view && <MapCanvas map={map} slopes={slopes} difficulty={difficulty} focused={focused} onSlope={onSlope} view={view} zoomed />}
      </div>
      <button type="button" className={styles.zoomClose} onClick={onClose} aria-label="크게 보기 닫기">
        <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">close</span>
      </button>
      <div className={styles.zoomButtons}>
        <button type="button" aria-label="확대" onClick={() => zoomAt(0.7, ...center())}>
          <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">add</span>
        </button>
        <button type="button" aria-label="축소" onClick={() => zoomAt(1.4, ...center())}>
          <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">remove</span>
        </button>
      </div>
      <div className={styles.zoomBottom}>{children}</div>
    </div>
  );
}

// ---------- 맵 그리기 ----------

type CanvasProps = {
  map: SlopeMap;
  slopes: SlopeItem[];
  difficulty: Difficulty | null;
  focused: SlopeItem | null;
  onSlope: (slope: SlopeItem | null) => void;
  view?: View;
  zoomed?: boolean;
};

// 리프트 이름 맞추기: 괄호 설명, "리프트", 띄어쓰기를 빼고 비교한다. 공식 표기가 "곤도라"인 곳이 있어 "곤돌라"로 맞춘다.
function liftKey(value: string | null) {
  return (value ?? "").replace(/\(.*?\)/g, "").replace(/리프트|[\s\-_.]/g, "").replace("곤도라", "곤돌라").toLowerCase();
}

function servedLifts(slope: SlopeItem | null, lifts: MapWay[]) {
  if (!slope?.liftName) return new Set<number>();
  const names = new Set(slope.liftName.split(/[,/]/).map(liftKey).filter(Boolean));
  return new Set(lifts.filter((lift) => lift.name && names.has(liftKey(lift.name))).map((lift) => lift.id));
}

const liftLabel = (name: string) => name.replace(/\(.*?\)/g, "").trim();

function MapCanvas({ map, slopes, difficulty, focused, onSlope, view, zoomed = false }: CanvasProps) {
  const [box, setBox] = useState<{ width: number; height: number } | null>(null);
  const svgRef = useCallback((node: SVGSVGElement | null) => {
    if (!node) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      setBox((current) => (current && current.width === width && current.height === height ? current : { width, height }));
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const shownView = view ?? fullView(map);
  const slopeByWay = useMemo(() => indexWays(slopes), [slopes]);
  const lifts = useMemo(() => map.ways.filter((way) => way.kind === "lift"), [map]);
  const served = useMemo(() => servedLifts(focused, lifts), [focused, lifts]);
  const boardLifts = useMemo(() => lifts.filter((lift) => served.has(lift.id) && lift.pts.length > 0), [lifts, served]);
  const active = useCallback(
    (slope: SlopeItem) => (!difficulty || slope.difficulty === difficulty) && (!focused || slope.id === focused.id),
    [difficulty, focused],
  );

  // 화면 1px이 맵 좌표로 얼마인지. 글자와 표시는 이 값으로 화면 크기를 고정한다.
  const px = box && box.width > 0 ? Math.max(shownView[2] / box.width, shownView[3] / box.height) : null;
  const labels = useMemo(() => {
    if (!px) return { slopes: [] as Label[], lifts: [] as Label[] };
    const byId = new Map(map.ways.map((way) => [way.id, way]));
    const bounds: View = [shownView[0], shownView[1], shownView[0] + shownView[2], shownView[1] + shownView[3]];
    const slopeItems: LabelItem[] = slopes
      .filter(active)
      .map((slope) => {
        const lines = slope.osmWayIds.map((id) => byId.get(id)?.pts).filter((pts): pts is Pt[] => !!pts && pts.length > 0);
        const main = lines.reduce<Pt[] | null>((best, line) => (!best || lineLength(line) > lineLength(best) ? line : best), null);
        const item: LabelItem | null = main ? { key: `s-${slope.id}`, text: slope.name, main, color: colorVar(slope.difficulty), slope } : null;
        return item;
      })
      .filter((item): item is LabelItem => item !== null);
    // 눌렀을 때 표시되는 리프트 타는 곳(▲) 자리는 이름표가 피한다.
    const marks: Label[] = boardLifts.map((lift) => {
      const [x, y] = lift.pts[0];
      const r = 8 * px;
      return { key: `m-${lift.id}`, text: "", main: lift.pts, color: "", anchor: [x, y], leader: null, x: x - r, y: y - r, w: r * 2, h: r * 2, fs: 0, crowded: false };
    });
    const slopeLabels = placeShrinking(slopeItems, px, bounds, marks);
    // 리프트 이름은 누른 슬로프의 리프트, 크게 보기에서는 모든 리프트. 슬로프 이름표를 피해 놓는다.
    const liftItems: LabelItem[] = lifts
      .filter((lift) => lift.name && (served.has(lift.id) || zoomed))
      .map((lift) => ({ key: `l-${lift.id}`, text: liftLabel(lift.name!), main: lift.pts, color: "", ts: [0], liftId: lift.id }));
    const liftLabels = placeShrinking(liftItems, px, bounds, [...marks, ...slopeLabels]);
    return { slopes: slopeLabels, lifts: liftLabels };
  }, [px, map, slopes, lifts, served, boardLifts, zoomed, active, shownView[0], shownView[1], shownView[2], shownView[3]]); // eslint-disable-line react-hooks/exhaustive-deps

  const unit = Math.max(map.width, map.height) / 900;
  const k = px ?? unit;
  const dimmed = (slope: SlopeItem) => (difficulty !== null || focused !== null) && !active(slope);
  const pistes = map.ways.filter((way) => way.kind === "piste");
  const named = pistes.filter((way) => slopeByWay.has(way.id));
  // 흐리게 그린 슬로프를 먼저 그려 강조한 선이 위에 오게 한다.
  const ordered = [...named].sort((a, b) => Number(!dimmed(slopeByWay.get(a.id)!)) - Number(!dimmed(slopeByWay.get(b.id)!)));
  const pick = (slope: SlopeItem) => (event: React.MouseEvent | React.KeyboardEvent) => {
    event.stopPropagation();
    onSlope(slope);
  };
  const keyPick = (slope: SlopeItem) => (event: React.KeyboardEvent) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      pick(slope)(event);
    }
  };

  return (
    <svg
      ref={svgRef}
      className={zoomed ? styles.zoomSvg : styles.mapSvg}
      viewBox={shownView.join(" ")}
      role="group"
      aria-label="리조트 전체 코스 맵"
      onClick={() => focused && onSlope(null)}
    >
      {lifts.map((lift) => {
        const on = served.has(lift.id);
        return (
          <path
            key={lift.id}
            d={path(lift.pts)}
            fill="none"
            stroke={on ? "var(--slope-lift-strong)" : "var(--slope-lift)"}
            strokeWidth={(on ? 2.6 : 1.4) * unit}
            strokeDasharray={`${6 * unit} ${5 * unit}`}
          />
        );
      })}
      {pistes
        .filter((way) => !slopeByWay.has(way.id))
        .map((way) => (
          <path key={way.id} d={path(way.pts)} fill="none" stroke={wayColor(way, slopeByWay)} strokeWidth={2.5 * unit} strokeLinecap="round" opacity={0.55} />
        ))}
      {ordered.map((way) => {
        const slope = slopeByWay.get(way.id)!;
        const dim = dimmed(slope);
        const strong = focused?.id === slope.id;
        return (
          <g key={way.id} className={styles.mapSlope} role="button" tabIndex={dim ? -1 : 0} aria-label={`${slope.name} 강조`} aria-pressed={strong} onClick={pick(slope)} onKeyDown={keyPick(slope)}>
            <path
              d={path(way.pts)}
              fill="none"
              stroke={dim ? "var(--slope-context)" : colorVar(slope.difficulty)}
              strokeWidth={(strong ? 6.5 : 4.5) * unit}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <path d={path(way.pts)} fill="none" stroke="transparent" strokeWidth={18 * k} />
          </g>
        );
      })}
      {[...labels.slopes, ...labels.lifts].map((label) =>
        label.leader ? (
          <line
            key={`leader-${label.key}`}
            x1={label.anchor[0]}
            y1={label.anchor[1]}
            x2={label.leader[0]}
            y2={label.leader[1]}
            stroke={label.liftId ? "var(--snow-muted, #64748b)" : label.color}
            strokeWidth={1.2 * k}
            strokeDasharray={label.liftId ? `${3 * k} ${2 * k}` : undefined}
          />
        ) : null,
      )}
      {labels.slopes.map((label) => (
        <circle key={`dot-${label.key}`} cx={label.anchor[0]} cy={label.anchor[1]} r={3.2 * k} fill={label.color} stroke="var(--snow-canvas, #fff)" strokeWidth={1.2 * k} />
      ))}
      {boardLifts.map((lift) => {
        const [x, y] = lift.pts[0];
        const r = 7.5 * k;
        return (
          <g key={`board-${lift.id}`} className={styles.liftBoard} aria-hidden="true">
            <circle cx={x} cy={y} r={r} fill="var(--snow-ink, #1e293b)" stroke="var(--snow-ink, #1e293b)" strokeWidth={1.5 * k} />
            <path d={`M${x - r * 0.45},${y + r * 0.3}L${x},${y - r * 0.4}L${x + r * 0.45},${y + r * 0.3}Z`} fill="var(--snow-canvas, #fff)" />
          </g>
        );
      })}
      {labels.slopes.map((label) => (
        <text
          key={label.key}
          x={label.x + label.w / 2}
          y={label.y + label.h * 0.74}
          textAnchor="middle"
          className={styles.mapLabel}
          fill={label.color}
          fontSize={label.fs}
          strokeWidth={3.2 * k}
          onClick={pick(label.slope!)}
        >
          {label.text}
        </text>
      ))}
      {labels.lifts.map((label) => {
        const on = served.has(label.liftId!);
        return (
          <text
            key={label.key}
            x={label.x + label.w / 2}
            y={label.y + label.h * 0.74}
            textAnchor="middle"
            className={on ? styles.liftLabelStrong : styles.liftLabel}
            fontSize={label.fs * 0.92}
            strokeWidth={3 * k}
          >
            {on ? `${label.text} 타는 곳` : label.text}
          </text>
        );
      })}
    </svg>
  );
}

// ---------- 이름표 자리 ----------

type LabelItem = { key: string; text: string; main: Pt[]; color: string; slope?: SlopeItem; liftId?: number; ts?: number[] };
type Label = LabelItem & { anchor: Pt; leader: Pt | null; x: number; y: number; w: number; h: number; fs: number; crowded: boolean };

function lineLength(pts: Pt[]) {
  let sum = 0;
  for (let i = 0; i < pts.length - 1; i += 1) sum += Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]);
  return sum;
}

// 선 길이의 t 지점. 꼭짓점 번호로 고르면 점이 2개뿐인 선은 모든 후보가 한 점에 모인다.
function pointAt(pts: Pt[], t: number): Pt {
  let left = lineLength(pts) * t;
  for (let i = 0; i < pts.length - 1; i += 1) {
    const a = pts[i];
    const b = pts[i + 1];
    const step = Math.hypot(b[0] - a[0], b[1] - a[1]);
    if (step > 0 && left <= step) return [a[0] + ((b[0] - a[0]) * left) / step, a[1] + ((b[1] - a[1]) * left) / step];
    left -= step;
  }
  return pts[pts.length - 1];
}

const ANCHOR_TS = [0.5, 0.35, 0.65, 0.2, 0.8, 0.1, 0.9];
// 이름표를 놓아 보는 방향. 오른쪽, 왼쪽, 위, 아래, 대각선 순이다.
const DIRECTIONS: Pt[] = [
  [1, 0],
  [-1, 0],
  [0, -1],
  [0, 1],
  [1, -1],
  [-1, -1],
  [1, 1],
  [-1, 1],
];

// 겹치는 이름표가 생기면 글자를 1px씩 줄여 다시 놓는다(12px에서 9px까지). 9px에서도 겹치면 그대로 둔다.
function placeShrinking(items: LabelItem[], px: number, bounds: View, obstacles: Label[]) {
  let placed: Label[] = [];
  for (const size of [12, 11, 10, 9]) {
    placed = placeLabels(items, px, bounds, size, obstacles);
    if (!placed.some((label) => label.crowded)) break;
  }
  return placed;
}

// 이름표마다 겹치지 않는 자리를 선 가까이부터 점점 멀리 찾고, 멀어지면 선과 이름표를 잇는 선을 긋는다.
// 끝까지 빈 자리가 없으면 가장 덜 겹치는 자리에 둔다. 맵에 선이 있으면 이름을 생략하지 않는다.
function placeLabels(items: LabelItem[], px: number, bounds: View, size: number, obstacles: Label[]): Label[] {
  const fs = size * px;
  const h = fs * 1.3;
  const gap = 4 * px;
  const textWidth = (text: string) => Array.from(text).reduce((sum, ch) => sum + (/[\u0000-ÿ]/.test(ch) ? 0.62 : 1) * fs, 0) + fs * 0.3;
  const placed: Label[] = [];
  const overlap = (x: number, y: number, w: number) => {
    let area = 0;
    for (const p of [...obstacles, ...placed]) {
      const ox = Math.min(x + w, p.x + p.w + gap) - Math.max(x, p.x - gap);
      const oy = Math.min(y + h, p.y + p.h + gap) - Math.max(y, p.y - gap);
      if (ox > 0 && oy > 0) area += ox * oy;
    }
    return area;
  };
  const inside = (x: number, y: number, w: number) => x >= bounds[0] && y >= bounds[1] && x + w <= bounds[2] && y + h <= bounds[3];
  // 짧은 선은 놓을 자리가 적으니 먼저 자리를 잡는다.
  const ordered = [...items].sort((a, b) => lineLength(a.main) - lineLength(b.main) || a.key.localeCompare(b.key));

  for (const item of ordered) {
    const w = textWidth(item.liftId ? `${item.text} 타는 곳` : item.text);
    const anchors = (item.ts ?? ANCHOR_TS).map((t) => pointAt(item.main, t));
    let best: { x: number; y: number; anchor: Pt; far: boolean; score: number } | null = null;
    search: for (const reach of [0, 2, 4, 7, 11, 16]) {
      const offset = gap + (item.liftId ? 7 * px : 0) + reach * fs;
      for (const anchor of anchors) {
        for (const [dx, dy] of DIRECTIONS) {
          const x = dx > 0 ? anchor[0] + offset : dx < 0 ? anchor[0] - offset - w : anchor[0] - w / 2;
          const y = dy > 0 ? anchor[1] + offset : dy < 0 ? anchor[1] - offset - h : anchor[1] - h / 2;
          if (!inside(x, y, w)) continue;
          const score = overlap(x, y, w);
          if (!best || score < best.score) best = { x, y, anchor, far: reach > 0, score };
          if (score === 0) break search;
        }
      }
    }
    const spot = best ?? { x: anchors[0][0] + gap, y: anchors[0][1] - h / 2, anchor: anchors[0], far: false, score: 1 };
    const cx = Math.max(spot.x, Math.min(spot.anchor[0], spot.x + w));
    const cy = Math.max(spot.y, Math.min(spot.anchor[1], spot.y + h));
    placed.push({ ...item, anchor: spot.anchor, leader: spot.far ? [cx, cy] : null, x: spot.x, y: spot.y, w, h, fs, crowded: spot.score > 0 });
  }
  return placed;
}
