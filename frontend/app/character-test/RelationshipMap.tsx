"use client";

import { useMemo, useState } from "react";
import { LEVELS, MAP_BAND_DOT, MAP_BAND_FILL, MAP_SNOW, SPORT_LABEL, faceImage, characterName, habitNote, type CharacterLabel, type JobCode } from "./data";
import { HEAD_CROP, HEAD_WIDTH, ME_HEAD_WIDTH, ME_RADIUS, layoutMap, treePaths, type MapInputLevel } from "./mapLayout";

// 캐릭터 그림에서 얼굴만 잘라 그린다.
function Head({ x, y, width, job }: { x: number; y: number; width: number; job: JobCode }) {
  const height = (width * HEAD_CROP.height) / HEAD_CROP.width;
  return (
    <svg x={x - width / 2} y={y - height / 2} width={width} height={height} viewBox={`${HEAD_CROP.x * 40} ${HEAD_CROP.y * 40} ${HEAD_CROP.width * 40} ${HEAD_CROP.height * 40}`}>
      <image href={faceImage(job)} width={40} height={40} />
    </svg>
  );
}

export function RelationshipMap({ levels, me, hideNames }: { levels: MapInputLevel[]; me: { character: CharacterLabel }; hideNames: boolean }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const layout = useMemo(() => layoutMap(levels, hideNames), [levels, hideNames]);
  const trees = useMemo(() => treePaths(layout.trees), [layout.trees]);
  const selected = layout.people.find((person) => person.id === selectedId) ?? null;
  const selectedLevel = LEVELS.find((level) => level.code === selected?.level);
  const summary = layout.bands.map((band) => `${band.label} ${band.count}명`).join(", ");
  const toggle = (id: string) => setSelectedId((current) => (current === id ? null : id));
  const { center, size } = layout;
  return (
    <div className="ct-map-wrap">
      <div className="ct-map-body">
        <svg className="ct-map" viewBox={`0 0 ${size} ${size}`} role="group" aria-label={`내가 가운데 있는 관계 지도. ${summary}`}>
          <g aria-hidden="true">
            <path d={trees.trunk} fill={MAP_SNOW.trunk} />
            <path d={trees.pine} fill={MAP_SNOW.pine} />
            <path d={trees.cap} fill="#fff" />
            {[...layout.bands].reverse().map((band) => <circle key={band.code} cx={center} cy={center} r={band.outer} fill={MAP_BAND_FILL[band.code]} stroke="#fff" strokeWidth={2} />)}
            {layout.labels.map((label) => (
              <g key={label.code} className="ct-map-ring-label">
                <rect x={label.x - label.width / 2 - 5} y={label.y - 7} width={label.width + 10} height={14} rx={7} />
                <text x={label.x} y={label.y + 3.5} textAnchor="middle">{label.text}</text>
              </g>
            ))}
          </g>
          {selected && <line className="ct-map-line" x1={center} y1={center} x2={selected.x} y2={selected.y - 10} />}
          {layout.empties.map((node) => <g key={node.id} className="ct-map-empty" aria-hidden="true"><circle cx={node.x} cy={node.y} r={15} /><path d={`M${node.x - 5} ${node.y}h10M${node.x} ${node.y - 5}v10`} /></g>)}
          {layout.people.map((node) => (
            <g
              key={node.id}
              className={node.id === selectedId ? "ct-map-person is-selected" : "ct-map-person"}
              role="button"
              tabIndex={0}
              aria-pressed={node.id === selectedId}
              aria-label={`${node.name} ${SPORT_LABEL[node.sport]} ${node.score}점`}
              onClick={() => toggle(node.id)}
              onKeyDown={(event) => {
                if (event.key !== "Enter" && event.key !== " ") return;
                event.preventDefault();
                toggle(node.id);
              }}
            >
              <rect className="ct-map-hit" x={node.x - 23} y={node.y - 26} width={46} height={50} rx={10} />
              <Head x={node.x} y={node.y - 10} width={HEAD_WIDTH} job={node.jobLabel.job} />
              <text className="ct-map-name" x={node.x} y={node.y + 12} textAnchor="middle">{node.name}</text>
              <text className="ct-map-score" x={node.x} y={node.y + 24} textAnchor="middle">{node.score}</text>
            </g>
          ))}
          <g className="ct-map-me">
            <circle className="ct-map-me-pulse" cx={center} cy={center} r={ME_RADIUS} />
            <circle className="ct-map-me-disc" cx={center} cy={center} r={ME_RADIUS} />
            <Head x={center} y={center - 6} width={ME_HEAD_WIDTH} job={me.character.job} />
            <text className="ct-map-me-name" x={center} y={center + 25} textAnchor="middle">나</text>
          </g>
        </svg>
        <ul className="ct-map-legend" aria-label="단계별 인원수">
          {layout.bands.map((band) => (
            <li key={band.code}>
              <i style={{ background: MAP_BAND_DOT[band.code] }} aria-hidden="true" />
              <span>{band.label}</span>
              <strong>{band.count}명</strong>
            </li>
          ))}
        </ul>
      </div>
      <div className="ct-map-detail" role="status">
        {selected && selectedLevel ? (
          <>
            <p><strong>{selected.name}</strong> <b>{selected.score}점</b> {selectedLevel.label}</p>
            <p>{characterName(selected.jobLabel)}, {SPORT_LABEL[selected.sport]}</p>
            <p>{habitNote(selected.habitGaps)}</p>
            <p className="ct-fineprint">{selectedLevel.note}</p>
          </>
        ) : <p className="ct-fineprint">{layout.people.length > 0 ? "가운데가 나예요. 안쪽 원일수록 잘 맞아요. 얼굴을 누르면 어떤 사이인지 보여 줘요." : "가운데가 나예요. 안쪽 원일수록 잘 맞는 사이예요."}</p>}
      </div>
      <p className="ct-fineprint">재미로 보는 궁합이에요. 널리 도는 경향과 타는 습관으로 매긴 점수이고, 정확한 궁합은 아닙니다.</p>
    </div>
  );
}
