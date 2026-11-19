"""만들어진 코스 맵 파일(frontend/public/slope-maps)을 슬로프 이름과 함께 그려 확인한다.

실행: python -X utf8 scripts/slope-data/verify_map.py KONJIAM 출력.png
- 굵은 선: 공식 슬로프와 짝지어진 선 (주황은 직접 그린 선)
- 얇은 회색: 짝없는 OSM 선, 회색 점선: 리프트
055 SQL의 osm_way_ids로 슬로프 이름을 붙인다. build_slope_data.py를 먼저 실행한다.
"""

import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]


def main(code, out, size=1600):
    data = json.loads((ROOT / "frontend" / "public" / "slope-maps" / f"{code.lower().replace('_', '-')}.json").read_text(encoding="utf-8"))
    sql = (ROOT / "database" / "production" / "055_migration_slope_data.sql").read_text(encoding="utf-8")
    owner = {}
    for m in re.finditer(
        r"SELECT r.`resort_id`, '((?:[^']|'')*)', .*?, '([-0-9,]+)', '(?:[^']|'')*', '[0-9-]+', \d+, NOW\(6\), NOW\(6\) FROM `resort` r WHERE r.`code` = '" + code + "';",
        sql,
    ):
        for way in m.group(2).split(","):
            owner[int(way)] = m.group(1).replace("''", "'")
    scale = (size - 80) / max(data["width"], data["height"])
    img = Image.new("RGB", (size, size), "white")
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("malgun.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    pt = lambda p: (40 + p[0] * scale, 40 + p[1] * scale)  # noqa: E731
    labels = []
    for way in data["ways"]:
        pts = [pt(p) for p in way["pts"]]
        if way["kind"] == "lift":
            for a, b in zip(pts[::2], pts[1::2]):
                draw.line([a, b], fill=(170, 170, 170), width=1)
        elif way["id"] in owner:
            draw.line(pts, fill=(230, 120, 0) if way.get("drawn") else (37, 99, 235), width=4)
            labels.append((pts[len(pts) // 2], owner[way["id"]]))
        else:
            draw.line(pts, fill=(200, 200, 200), width=2)
    for (x, y), name in labels:
        draw.text((x + 4, y - 8), name, fill=(0, 0, 0), font=font, stroke_width=2, stroke_fill="white")
    img.save(out)
    print(out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
