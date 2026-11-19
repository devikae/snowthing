"""OSM 선에 way ID를 붙여 그린 확인용 HTML을 만든다. 공식 슬로프맵과 나란히 보고 manual/{CODE}.json을 채울 때 쓴다.

실행: python -X utf8 scripts/slope-data/render_osm.py HIGH1 out.html
- 실선: 선으로 그린 슬로프 (난이도 색)
- 보라 점선: 면으로 그린 슬로프의 가운데 선 (ID는 음수로 적는다)
- 회색 점선: 리프트
화면 위가 정상, 아래가 베이스다. build_slope_data.py와 같은 회전을 쓴다.
"""

import html
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_slope_data as b  # noqa: E402

COLORS = {"novice": "#16a34a", "easy": "#16a34a", "intermediate": "#2563eb", "advanced": "#111827", "expert": "#dc2626"}


def main(code, out):
    raw = [e for e in json.loads((b.HERE / "osm" / f"{code}.json").read_text(encoding="utf-8"))["elements"] if e.get("geometry")]
    is_piste = lambda e: e["tags"].get("piste:type") == "downhill"  # noqa: E731
    proj = b.project(raw)
    lines = {e["id"]: [proj(p) for p in e["geometry"]] for e in raw}
    pistes = [e for e in raw if is_piste(e) and not b.is_area(e)]
    lifts = [e for e in raw if not is_piste(e)]
    vx = sum(lines[e["id"]][-1][0] - lines[e["id"]][0][0] for e in pistes) + sum(lines[e["id"]][0][0] - lines[e["id"]][-1][0] for e in lifts)
    vy = sum(lines[e["id"]][-1][1] - lines[e["id"]][0][1] for e in pistes) + sum(lines[e["id"]][0][1] - lines[e["id"]][-1][1] for e in lifts)
    angle = math.pi / 2 - math.atan2(vy, vx)
    c, s = math.cos(angle), math.sin(angle)
    rot = {i: [(x * c - y * s, x * s + y * c) for x, y in pts] for i, pts in lines.items()}
    items = []
    for e in raw:
        pts = rot[e["id"]]
        if is_piste(e) and b.is_area(e):
            center = b.centerline(pts[:-1] if pts[0] == pts[-1] else pts)
            if not center:
                continue
            items.append((-e["id"], "area", e["tags"], center))
        else:
            items.append((e["id"], "piste" if is_piste(e) else "lift", e["tags"], pts))
    xs = [p[0] for _, _, _, pts in items for p in pts]
    ys = [p[1] for _, _, _, pts in items for p in pts]
    x0, y0, w, h = min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)
    unit = max(w, h) / 1400
    svg = []
    for way_id, kind, tags, pts in items:
        d = "M" + "L".join(f"{x - x0:.1f},{y - y0:.1f}" for x, y in pts)
        if kind == "lift":
            svg.append(f'<path d="{d}" fill="none" stroke="#9ca3af" stroke-width="{1.2 * unit}" stroke-dasharray="{6 * unit} {5 * unit}"/>')
        elif kind == "area":
            svg.append(f'<path d="{d}" fill="none" stroke="#7c3aed" stroke-width="{2.5 * unit}" stroke-dasharray="{4 * unit} {3 * unit}"/>')
        else:
            color = COLORS.get(tags.get("piste:difficulty"), "#9ca3af")
            svg.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{2.5 * unit}"/>')
    for way_id, kind, tags, pts in items:
        mid = pts[len(pts) // 2]
        name = tags.get("name") or ""
        # OSM 이름은 외부 데이터라 HTML에 넣기 전에 이스케이프한다.
        label = html.escape(f"{way_id} {name}" if kind != "lift" else f"[lift] {name}")
        color = "#6b7280" if kind == "lift" else ("#7c3aed" if kind == "area" else "#b91c1c")
        svg.append(
            f'<text x="{mid[0] - x0 + 4 * unit:.1f}" y="{mid[1] - y0:.1f}" font-size="{10 * unit:.1f}" fill="{color}" '
            f'paint-order="stroke" stroke="#fff" stroke-width="{3 * unit:.1f}">{label}</text>'
        )
    pad = 20 * unit
    html = (
        '<!doctype html><meta charset="utf-8"><body style="margin:0;background:#fff">'
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{-pad} {-pad} {w + 2 * pad} {h + 2 * pad}" '
        'width="1600" height="1600" style="font-family:sans-serif">' + "".join(svg) + "</svg></body>"
    )
    Path(out).write_text(html, encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
