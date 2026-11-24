"""IKA-98 슬로프 제원과 코스 맵 데이터를 만든다.

입력
- specs/kr_specs.json, specs/jp_specs.json: 리조트 공식 사이트에서 정리한 제원. 공식 값이 없으면 null.
- osm/{RESORT_CODE}.json: Overpass API 응답(piste:type=downhill, aerialway 선, out geom tags).

출력
- frontend/public/slope-maps/{resort-code}.json: 코스 맵 선. 위도와 경도를 미터 평면으로 바꾸고, 정상이 위(작은 y)로 오게 회전했다.
- database/production/055_migration_slope_data.sql: 슬로프 제원, OSM 선 연결, 슬로프 사이 관계.

관계는 OSM 선으로 자동 계산한 초안이다. 결과를 눈으로 검수한 뒤 커밋한다.
실행: python -X utf8 scripts/slope-data/build_slope_data.py
"""

from __future__ import annotations

import json
import math
import re
import unicodedata
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MAP_DIR = ROOT / "frontend" / "public" / "slope-maps"
SQL_PATH = ROOT / "database" / "production" / "055_migration_slope_data.sql"
CHECKED_AT = "2026-11-20"

# 붙어 있다고 보는 거리, 나란히로 보는 거리 (m)
TOUCH_M = 25
NEAR_M = 90
# 연결된 선의 중심에서 이보다 먼 같은 이름 선은 이웃 리조트 것으로 본다. 맵은 남은 선 둘레만 그린다.
# 니세코는 한 산을 네 리조트가 나눠 써서 좁게 잡고, 산이 여러 개인 루스츠 같은 곳은 넓게 둔다.
CLUSTER_RADIUS_M = {"NISEKO_HIRAFU": 2500}
DEFAULT_CLUSTER_RADIUS_M = 6000
MAP_MARGIN_M = 300
# 이름에 있으면 본 코스와 다른 코스로 보는 말(괄호 안에 붙는 경우가 많다).
SEPARATE_COURSE_WORDS = ("ショートカット", "short cut", "shortcut", "迂回", "bypass", "숏컷", "우회")
# 직접 그린 선의 ID. OSM ID(양수)와 면 가운데 선(음수 OSM ID)과 겹치지 않게 큰 음수를 쓴다.
DRAWN_ID_BASE = 10**12

# 같은 리조트 이름으로 표기됐지만 다른 리조트(니세코 하나조노) 쪽 코스, 코스가 아닌 연결 통로
EXCLUDE = {
    "NISEKO_HIRAFU": {
        "Juicy Fruit",
        "Mango Salad",
        "Setsuzoku",
        "Colly's Folly",
        "Stomping Ground",
        "Crystal Garden",
        "Silver Dream",
        "Stairway to Heaven",
        "Legend of Shinya",
        "King area access pass",
    }
}

# 공식 이름과 OSM 이름이 글자만 다른 경우. 공식 이름 -> OSM 이름.
ALIASES = {
    ("PHOENIX", "챔피온"): "챔피언",
    ("PHOENIX", "호크"): "호크1",
    ("YONGPYONG", "뉴옐로우"): "뉴엘로우",
    ("YONGPYONG", "골드밸리"): "골드계곡",
    # 엘리시안 강촌은 OSM에 영어 이름만 있다(오타 포함).
    ("ELYSIAN", "팬더"): "Penda",
    ("ELYSIAN", "래빗"): "Rabbit",
    ("ELYSIAN", "제브라"): "Zebra",
    ("ELYSIAN", "드래곤"): "Dragon",
    ("ELYSIAN", "호스"): "Horse",
    ("ELYSIAN", "페가수스"): "Pegasus",
    ("ELYSIAN", "디어"): "Deer",
    ("ELYSIAN", "퓨마"): "Puma",
    ("ELYSIAN", "래퍼드"): "Leopard",
    ("ELYSIAN", "재규어"): "Jeguar",
}

# 리프트가 공식으로 명시되지 않고 이름으로만 추정한 리조트는 리프트를 비운다.
# 곤지암은 2026-11-24에 공식 리프트 안내(liFt.dev)와 슬로프맵으로 확인해 뺐다.
LIFT_NOT_STATED: set[str] = set()

DIFFICULTY = [
    ("최상급", "EXPERT"),
    ("익스트림", "ADVANCED"),
    ("파크", "ADVANCED"),
    ("부대시설", "ADVANCED"),
    ("중상급", "ADVANCED"),
    ("중/상급", "ADVANCED"),
    ("상급", "ADVANCED"),
    ("초중급", "INTERMEDIATE"),
    ("초/중급", "INTERMEDIATE"),
    ("중급", "INTERMEDIATE"),
    ("초급", "BEGINNER"),
    ("最上級", "EXPERT"),
    ("上級", "ADVANCED"),
    ("初中級", "INTERMEDIATE"),
    ("中級", "INTERMEDIATE"),
    ("初級", "BEGINNER"),
    ("初心者", "BEGINNER"),
]

ROMAN = {"Ⅰ": "1", "Ⅱ": "2", "Ⅲ": "3", "Ⅳ": "4", "Ⅴ": "5", "Ⅵ": "6", "Ⅶ": "7", "Ⅷ": "8"}


def difficulty_of(official: str | None) -> str:
    text = official or ""
    for key, value in DIFFICULTY:
        if key in text:
            return value
    raise ValueError(f"난이도 묶음을 정할 수 없다: {official}")


def official_label(official: str | None) -> str | None:
    """니세코처럼 색 설명이 붙은 표기는 앞의 공식 등급만 남긴다."""
    if official is None:
        return None
    return re.sub(r"\s*\(.*\)$", "", official).strip()


def norm(name: str | None) -> str:
    if not name:
        return ""
    # 로마 숫자(Ⅰ)를 먼저 숫자로 바꾼다. NFKC가 Ⅰ을 영문 I로 바꾸기 때문이다.
    text = name
    for roman, digit in ROMAN.items():
        text = text.replace(roman, digit)
    # 전각 숫자와 영문(第４, Ｆ７)을 반각으로 맞추고, 나에바처럼 앞에 붙은 코스 번호(D1, DG1)를 뗀다.
    text = unicodedata.normalize("NFKC", text)
    text = re.sub(r"^[A-Z]{1,3}\d+\s+", "", text)
    text = re.sub(r"\(.*?\)", "", text).lower()
    for word in ("コース", "course", "코스", "slope", "슬로프"):
        text = text.replace(word, "")
    return re.sub(r"[\s\-_\u00b7.'\u2019()]", "", text)


def percent_to_degree(percent):
    return round(math.degrees(math.atan(float(percent) / 100)), 1)


def to_decimal(value, digits=1):
    if value is None:
        return None
    return round(float(value), digits)


# ---------- 기하 ----------


def project(elements):
    lat0 = sum(p["lat"] for e in elements for p in e["geometry"]) / sum(
        len(e["geometry"]) for e in elements
    )
    k = math.cos(math.radians(lat0))
    return lambda p: (p["lon"] * 111320 * k, -p["lat"] * 110540)


def seg_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = dx * dx + dy * dy
    t = 0 if length == 0 else max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length))
    return math.dist(p, (a[0] + t * dx, a[1] + t * dy))


def point_line_dist(p, lines):
    return min(seg_dist(p, line[i], line[i + 1]) for line in lines for i in range(len(line) - 1))


def segments_cross(a, b, c, d):
    """선분 ab와 cd가 만나는지. 꼭짓점이 멀어도 가운데에서 교차하는 경우를 잡는다."""

    def orient(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])

    return orient(a, b, c) * orient(a, b, d) < 0 and orient(c, d, a) * orient(c, d, b) < 0


def lines_dist(a_lines, b_lines):
    for la in a_lines:
        for lb in b_lines:
            for i in range(len(la) - 1):
                for j in range(len(lb) - 1):
                    if segments_cross(la[i], la[i + 1], lb[j], lb[j + 1]):
                        return 0.0
    best = min(point_line_dist(p, b_lines) for line in a_lines for p in line)
    return min(best, min(point_line_dist(p, a_lines) for line in b_lines for p in line))


def ends(lines):
    """여러 선으로 된 슬로프의 출발점(가장 위)과 도착점(가장 아래)."""
    points = [line[0] for line in lines] + [line[-1] for line in lines]
    return min(points, key=lambda p: p[1]), max(points, key=lambda p: p[1])


def classify(a, b):
    """두 슬로프의 관계를 (a에서 본 관계, b에서 본 관계, 거리)로 준다. 멀면 None."""
    d = lines_dist(a, b)
    if d > NEAR_M:
        return None
    if d > TOUCH_M:
        distance = int(round(d / 10.0) * 10)
        return "PARALLEL", "PARALLEL", distance
    a_start, a_end = ends(a)
    b_start, b_end = ends(b)
    near = lambda p, q: math.dist(p, q) <= TOUCH_M  # noqa: E731
    if near(a_start, b_start):
        return "SHARE_START", "SHARE_START", None
    if near(a_end, b_end):
        return "SHARE_END", "SHARE_END", None
    if near(a_end, b_start):
        return "CONTINUE", "MERGE", None
    if near(b_end, a_start):
        return "MERGE", "CONTINUE", None
    if point_line_dist(b_end, a) <= TOUCH_M:
        return "MERGE", "CONTINUE", None
    if point_line_dist(a_end, b) <= TOUCH_M:
        return "CONTINUE", "MERGE", None
    if point_line_dist(b_start, a) <= TOUCH_M:
        return "BRANCH", "STARTS_FROM", None
    if point_line_dist(a_start, b) <= TOUCH_M:
        return "STARTS_FROM", "BRANCH", None
    return "CROSS", "CROSS", None


# ---------- 리조트 처리 ----------


def load_specs():
    """specs/의 모든 JSON을 읽는다. 국내 파일은 슬로프 배열, 일본 파일은 {resorts, slopes} 객체다."""
    by_resort: dict[str, list[dict]] = {}
    jp_codes: set[str] = set()
    for path in sorted((HERE / "specs").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        slopes = data if isinstance(data, list) else data["slopes"]
        for slope in slopes:
            code = slope["resortCode"]
            if path.name.startswith("jp_"):
                jp_codes.add(code)
            if slope["name"] in EXCLUDE.get(code, set()):
                continue
            by_resort.setdefault(code, []).append(slope)
    return by_resort, jp_codes


def is_area(e):
    g = e["geometry"]
    return e["tags"].get("area") == "yes" or (g[0] == g[-1] and len(g) > 3)


def centerline(poly):
    """면으로 그린 슬로프를 위에서 아래로 가로로 잘라 각 높이의 가운데를 이은 선으로 바꾼다."""
    ys = [p[1] for p in poly]
    top, bottom = min(ys), max(ys)
    steps = max(3, min(60, int((bottom - top) / 15)))
    points = []
    for k in range(steps + 1):
        y = top + (bottom - top) * (k + 0.5) / (steps + 1)
        xs = []
        for a, b in zip(poly, poly[1:] + poly[:1]):
            if a[1] == b[1] or (a[1] - y) * (b[1] - y) > 0:
                continue
            xs.append(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]))
        # 교차점을 짝지어 면 안쪽 구간을 만든다. U자 모양처럼 구간이 여러 개면 직전 점과 이어지는 구간만 쓴다.
        xs.sort()
        intervals = [(xs[i], xs[i + 1]) for i in range(0, len(xs) - 1, 2)]
        if not intervals:
            continue
        if points:
            prev = points[-1][0]
            inside = [iv for iv in intervals if iv[0] <= prev <= iv[1]]
            if len(inside) != 1:
                if len(intervals) == 1:
                    inside = intervals
                else:
                    # 어느 구간으로 이어지는지 알 수 없으면 거기서 멈춘다. 틈을 가로질러 잇지 않는다.
                    break
            lo, hi = inside[0]
        elif len(intervals) == 1:
            lo, hi = intervals[0]
        else:
            continue
        points.append(((lo + hi) / 2, y))
    return points if len(points) >= 2 else None


def load_manual(code):
    """공식 슬로프맵을 보고 OSM 선과 공식 슬로프를 직접 짝지은 표. 이름이 없거나 다른 선에 쓴다.

    {"공식 슬로프 이름": [OSM way ID, ...]}. 면으로 그린 슬로프는 음수 ID(가운데 선)로 적는다.
    OSM에 선이 없는 슬로프는 {"draw": [[위도, 경도], ...]}로 공식 슬로프맵을 보고 직접 그린 선을 적는다(정상에서 베이스 방향).
    """
    path = HERE / "manual" / f"{code}.json"
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    to_latlon = fit_anchors(data.get("_anchors"))
    result = {}
    for name, value in data.items():
        if name.startswith("_"):
            continue
        # 공식 슬로프맵 픽셀 좌표로 그린 선은 기준점으로 맞춘 변환으로 위도, 경도로 바꾼다.
        if isinstance(value, dict) and value.get("drawPx"):
            if to_latlon is None:
                raise ValueError(f"{code}: drawPx를 쓰려면 _anchors가 3개 이상 필요하다")
            value = {"draw": [to_latlon(x, y) for x, y in value["drawPx"]]}
        result[name] = value
    return result


def load_lift_names(code):
    """manual/{CODE}.json의 "_lifts": {"OSM 리프트 way ID": "공식 리프트 이름" 또는 null}.

    OSM 리프트 이름이 없거나 공식 이름과 다를 때 공식 슬로프맵, 리프트 안내를 보고 적는다.
    null은 리프트가 아닌 선(탑승장 면, 역 건물)이라 맵에 넣지 않는다.
    """
    path = HERE / "manual" / f"{code}.json"
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return {int(k): v for k, v in data.get("_lifts", {}).items()}


def fit_anchors(anchors):
    """공식 슬로프맵 픽셀과 위도, 경도를 잇는 1차 변환(어파인)을 최소제곱으로 맞춘다.

    anchors: [{"px": [x, y], "lat": 위도, "lon": 경도}, ...] 3개 이상. 리프트 끝처럼 맵과 OSM 양쪽에 있는 지점을 쓴다.
    조감도 맵은 원근 때문에 위치가 조금 어긋나지만, 코스 카드는 간략 맵이라 감수한다.
    """
    if not anchors or len(anchors) < 3:
        return None

    def solve(values):
        # 정규방정식 (A^T A) c = A^T v, A = [x, y, 1]
        rows = [(a["px"][0], a["px"][1], 1.0) for a in anchors]
        ata = [[sum(r[i] * r[j] for r in rows) for j in range(3)] for i in range(3)]
        atv = [sum(r[i] * v for r, v in zip(rows, values)) for i in range(3)]
        m = [row[:] + [atv[i]] for i, row in enumerate(ata)]
        for col in range(3):
            pivot = max(range(col, 3), key=lambda r: abs(m[r][col]))
            if abs(m[pivot][col]) < 1e-9:
                raise ValueError("_anchors가 한 직선 위에 있다. 직선 밖의 기준점을 하나 이상 넣는다")
            m[col], m[pivot] = m[pivot], m[col]
            for r in range(3):
                if r != col:
                    f = m[r][col] / m[col][col]
                    m[r] = [a - f * b for a, b in zip(m[r], m[col])]
        return [m[i][3] / m[i][i] for i in range(3)]

    la = solve([a["lat"] for a in anchors])
    lo = solve([a["lon"] for a in anchors])
    return lambda x, y: (la[0] * x + la[1] * y + la[2], lo[0] * x + lo[1] * y + lo[2])


def build_map(code, slopes):
    path = HERE / "osm" / f"{code}.json"
    if not path.exists():
        return None, {}
    raw = [e for e in json.loads(path.read_text(encoding="utf-8"))["elements"] if e.get("geometry")]
    if not raw:
        return None, {}
    is_piste = lambda e: e["tags"].get("piste:type") == "downhill"  # noqa: E731
    # 면(area)으로 그린 슬로프는 테두리가 두꺼운 띠처럼 보이므로 그대로 그리지 않고 가운데 선으로 바꿔 쓴다.
    areas = [e for e in raw if is_piste(e) and is_area(e)]
    elements = [e for e in raw if not (is_piste(e) and is_area(e))]
    pistes = [e for e in elements if is_piste(e)]
    lifts = [e for e in elements if not is_piste(e)]
    proj = project(raw)
    lines = {e["id"]: [proj(p) for p in e["geometry"]] for e in raw}
    manual = load_manual(code)
    drawn = {}
    for name, value in manual.items():
        if isinstance(value, dict) and value.get("draw"):
            way_id = -(DRAWN_ID_BASE + zlib.crc32(f"{code}:{name}".encode()) % 10**9)
            lines[way_id] = [proj({"lat": lat, "lon": lon}) for lat, lon in value["draw"]]
            drawn[way_id] = name
            manual[name] = [way_id]

    # 슬로프는 위에서 아래로, 리프트는 아래에서 위로 그리는 것이 OSM 관례다.
    # 평균 내려가는 방향이 화면 아래를 향하게 돌린다. 선 슬로프가 없는 리조트는 리프트 방향만으로 정한다.
    vx = sum(lines[e["id"]][-1][0] - lines[e["id"]][0][0] for e in pistes)
    vy = sum(lines[e["id"]][-1][1] - lines[e["id"]][0][1] for e in pistes)
    vx += sum(lines[e["id"]][0][0] - lines[e["id"]][-1][0] for e in lifts)
    vy += sum(lines[e["id"]][0][1] - lines[e["id"]][-1][1] for e in lifts)
    angle = math.pi / 2 - math.atan2(vy, vx)
    c, s = math.cos(angle), math.sin(angle)
    rotated = {i: [(x * c - y * s, x * s + y * c) for x, y in pts] for i, pts in lines.items()}

    # 면 슬로프의 가운데 선. 음수 ID로 구분한다.
    synthetic = {}
    for e in areas:
        pts = centerline(rotated[e["id"]][:-1] if rotated[e["id"]][0] == rotated[e["id"]][-1] else rotated[e["id"]])
        if pts:
            synthetic[-e["id"]] = {"tags": e["tags"], "pts": pts}
            rotated[-e["id"]] = pts

    all_points = [p for e in elements for p in rotated[e["id"]]] + [
        p for item in synthetic.values() for p in item["pts"]
    ]
    all_points += [p for w in drawn for p in rotated[w]]
    min_x = min(p[0] for p in all_points)
    min_y = min(p[1] for p in all_points)
    shifted = {i: [(x - min_x, y - min_y) for x, y in pts] for i, pts in rotated.items()}

    # 공식 이름과 OSM 이름을 맞춘다. 한 슬로프가 여러 선으로 나뉜 경우가 있어 모두 모은다.
    def names_of(tags):
        keys = {
            norm(tags.get(k))
            for k in ("name", "name:ko", "name:en", "name:ja", "piste:name", "ref")
            if tags.get(k)
        }
        return keys - {""}

    line_names = {e["id"]: names_of(e["tags"]) for e in pistes}
    area_names = {i: names_of(item["tags"]) for i, item in synthetic.items()}
    matched: dict[str, list[int]] = {}
    for slope in slopes:
        if slope["name"] in manual:
            missing = [w for w in manual[slope["name"]] if w not in shifted]
            if missing:
                raise ValueError(f"{code} {slope['name']}: OSM 데이터에 없는 way ID {missing}")
            matched[slope["name"]] = list(manual[slope["name"]])
            continue
        keys = {
            norm(slope["name"]),
            norm(slope.get("nameLocal")),
            norm(ALIASES.get((code, slope["name"]))),
        } - {""}
        # 선으로 그린 슬로프를 먼저 쓰고, 없을 때만 면 슬로프의 가운데 선을 쓴다.
        official_raw = f"{slope['name']} {slope.get('nameLocal') or ''}".lower()

        def same_course(tags):
            # 괄호를 지우면 "林間コース（ショートカット）"가 본 코스 "林間コース"와 같아진다.
            # 숏컷, 우회처럼 다른 코스를 뜻하는 말이 OSM 이름에만 있으면 다른 코스로 본다.
            osm_raw = " ".join(str(v) for k, v in tags.items() if k.startswith("name")).lower()
            return not any(q in osm_raw and q not in official_raw for q in SEPARATE_COURSE_WORDS)

        tags_of = {e["id"]: e["tags"] for e in pistes} | {i: item["tags"] for i, item in synthetic.items()}
        ways = [w for w, names in line_names.items() if names & keys and same_course(tags_of[w])] or [
            w for w, names in area_names.items() if names & keys and same_course(tags_of[w])
        ]
        if ways:
            matched[slope["name"]] = ways
    matched = {name: ways for name, ways in matched.items() if ways}

    # 가운데 선은 슬로프와 짝지어진 것만 그린다. 짝없는 면까지 그리면 선 슬로프와 겹쳐 지저분해진다.
    used = {w for ways in matched.values() for w in ways}
    for i, item in synthetic.items():
        if i in used:
            elements.append({"id": i, "tags": item["tags"]})
    drawn_used = any(w in used for w in drawn)
    for way_id, name in drawn.items():
        if way_id in used:
            elements.append({"id": way_id, "tags": {"piste:type": "downhill", "name": name}, "drawn": True})

    # 이웃 리조트(니세코처럼 산 하나를 나눠 쓰는 곳)에 같은 이름의 코스가 있으면 엉뚱한 선이 붙는다.
    # 연결된 선들의 중심에서 멀리 떨어진 선은 버리고, 남은 선 주변만 맵에 그린다.
    if matched:
        mids = {
            w: shifted[w][len(shifted[w]) // 2] for ways in matched.values() for w in ways
        }
        xs = sorted(p[0] for p in mids.values())
        ys = sorted(p[1] for p in mids.values())
        center = (xs[len(xs) // 2], ys[len(ys) // 2])
        radius = CLUSTER_RADIUS_M.get(code, DEFAULT_CLUSTER_RADIUS_M)
        matched = {
            name: kept
            for name, ways in matched.items()
            if (kept := [w for w in ways if math.dist(mids[w], center) <= radius])
        }
        kept_points = [p for ways in matched.values() for w in ways for p in shifted[w]]
        if not kept_points:
            print(f"{code}: 연결된 선이 모두 중심에서 멀어 맵을 만들지 않는다")
            return None, {}
        box = (
            min(p[0] for p in kept_points) - MAP_MARGIN_M,
            min(p[1] for p in kept_points) - MAP_MARGIN_M,
            max(p[0] for p in kept_points) + MAP_MARGIN_M,
            max(p[1] for p in kept_points) + MAP_MARGIN_M,
        )
        inside = lambda pts: any(  # noqa: E731
            box[0] <= x <= box[2] and box[1] <= y <= box[3] for x, y in pts
        )
        elements = [e for e in elements if inside(shifted[e["id"]])]
        min_x = min(p[0] for e in elements for p in shifted[e["id"]])
        min_y = min(p[1] for e in elements for p in shifted[e["id"]])
        shifted = {
            e["id"]: [(x - min_x, y - min_y) for x, y in shifted[e["id"]]] for e in elements
        }

    lift_names = load_lift_names(code)
    unknown = [i for i in lift_names if i not in {e["id"] for e in lifts}]
    if unknown:
        raise ValueError(f"{code}: _lifts에 OSM 리프트가 아닌 way ID {unknown}")
    ways_out = []
    for e in elements:
        tags = e["tags"]
        is_lift = tags.get("piste:type") != "downhill"
        if is_lift and e["id"] in lift_names and lift_names[e["id"]] is None:
            continue
        name = tags.get("name") or tags.get("piste:name") or None
        if is_lift and e["id"] in lift_names:
            name = lift_names[e["id"]]
        ways_out.append(
            {
                "id": e["id"],
                "kind": "lift" if is_lift else "piste",
                "name": name,
                "difficulty": tags.get("piste:difficulty"),
                "liftType": tags.get("aerialway"),
                **({"drawn": True} if e.get("drawn") else {}),
                "pts": [[round(x, 1), round(y, 1)] for x, y in shifted[e["id"]]],
            }
        )
    width = max(p[0] for pts in shifted.values() for p in pts)  # noqa: E501
    height = max(p[1] for pts in shifted.values() for p in pts)
    map_json = {
        "resortCode": code,
        "attribution": "© OpenStreetMap contributors (ODbL)"
        + (", 일부 코스는 리조트 공식 슬로프맵을 보고 눈팅이 그림" if drawn_used else ""),
        "unit": "m",
        "width": round(width, 1),
        "height": round(height, 1),
        "ways": ways_out,
    }
    slope_lines = {name: [shifted[w] for w in ways] for name, ways in matched.items()}
    return map_json, (matched, slope_lines)


def sql_str(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    specs, _ = load_specs()
    # 모든 리조트를 다 만든 뒤에만 파일을 쓴다. 중간에 실패하면 기존 맵과 SQL을 그대로 둔다.
    staged_maps: dict[str, str] = {}
    slope_rows = []
    connection_rows = []
    report = []
    for code, slopes in specs.items():
        map_json, match = build_map(code, slopes)
        matched, slope_lines = match if match else ({}, {})
        if map_json:
            staged_maps[f"{code.lower().replace('_', '-')}.json"] = json.dumps(
                map_json, ensure_ascii=False, separators=(",", ":")
            )
        for order, slope in enumerate(slopes, start=1):
            lift = None if code in LIFT_NOT_STATED else slope.get("liftName")
            avg = to_decimal(slope.get("avgGradient"))
            mx = to_decimal(slope.get("maxGradient"))
            # 경사를 %로만 공개한 리조트(무주, 오투)는 같은 값을 도로 바꾼다. 각도 = atan(% / 100).
            # 공개 %가 자기 표고차, 길이와 맞지 않는 리조트(알펜시아)는 specs에 % 칸을 두지 않아 비워 둔다.
            if avg is None and slope.get("avgGradientPercent") is not None:
                avg = percent_to_degree(slope["avgGradientPercent"])
            if mx is None and slope.get("maxGradientPercent") is not None:
                mx = percent_to_degree(slope["maxGradientPercent"])
            # 공식 표기에서 평균이 최대보다 크면 둘 중 하나가 오타다. 어느 쪽인지 모르니 평균을 비운다.
            if avg is not None and mx is not None and avg > mx:
                avg = None
            slope_rows.append(
                (
                    code,
                    slope["name"],
                    slope.get("nameLocal"),
                    official_label(slope.get("officialDifficulty")),
                    difficulty_of(slope.get("officialDifficulty")),
                    slope.get("lengthM"),
                    avg,
                    mx,
                    None if slope.get("widthM") is None else int(round(slope["widthM"])),
                    lift,
                    ",".join(str(w) for w in matched.get(slope["name"], [])) or None,
                    slope["sourceUrl"],
                    order,
                )
            )
        names = list(slope_lines)
        for i, a in enumerate(names):
            for b in names[i + 1 :]:
                result = classify(slope_lines[a], slope_lines[b])
                if result:
                    rel_a, rel_b, distance = result
                    connection_rows.append((code, a, b, rel_a, distance))
                    connection_rows.append((code, b, a, rel_b, distance))
        report.append((code, len(slopes), len(matched), map_json is not None))

    lines = [
        "-- IKA-98 슬로프 제원과 관계 데이터. 설계: docs/conception/033_slope_compare/01_design.md",
        "-- scripts/slope-data/build_slope_data.py로 만든다. 직접 고치지 말고 스크립트나 specs를 고친 뒤 다시 만든다.",
        "-- 제원: 리조트 공식 사이트(출처는 source_url). 공식 값이 없으면 NULL이다.",
        "-- osm_way_ids: OpenStreetMap 선 ID. 코스 맵 파일은 frontend/public/slope-maps/에 있다.",
        "-- slope_connection: OSM 선과 그린 선으로 자동 계산한 관계. 한 쌍은 양쪽 줄이 서로 맞물린다. 검수는 후속 작업이다.",
        "-- 일본 리조트는 054에서 넣은 대로 비활성으로 둔다. 슬로프 제원 화면은 해외 리조트를 활성 여부와 상관없이 보여 준다.",
        "-- 실행 순서: 054 다음. 앱보다 먼저 적용해도 된다(새 표에 줄만 넣고 기존 표는 바꾸지 않는다).",
        "-- 여러 번 실행해도 안전하다 (INSERT IGNORE).",
        "SET NAMES utf8mb4;",
        "",
    ]
    for row in slope_rows:
        (code, name, local, official, diff, length, avg, mx, width, lift, ways, url, order) = row
        lines.append(
            "INSERT IGNORE INTO `slope` (`resort_id`, `name`, `name_local`, `official_difficulty`,"
            " `difficulty`, `length_m`, `avg_gradient`, `max_gradient`, `width_m`, `lift_name`,"
            " `osm_way_ids`, `source_url`, `checked_at`, `display_order`, `created_at`, `updated_at`)"
            f" SELECT r.`resort_id`, {sql_str(name)}, {sql_str(local)}, {sql_str(official)},"
            f" {sql_str(diff)}, {sql_str(length)}, {sql_str(avg)}, {sql_str(mx)}, {sql_str(width)},"
            f" {sql_str(lift)}, {sql_str(ways)}, {sql_str(url)}, '{CHECKED_AT}', {order}, NOW(6), NOW(6)"
            f" FROM `resort` r WHERE r.`code` = {sql_str(code)};"
        )
    lines.append("")
    for code, a, b, relation, distance in connection_rows:
        lines.append(
            "INSERT IGNORE INTO `slope_connection` (`slope_id`, `related_slope_id`, `relation`,"
            " `distance_m`, `created_at`, `updated_at`)"
            f" SELECT a.`slope_id`, b.`slope_id`, {sql_str(relation)}, {sql_str(distance)}, NOW(6), NOW(6)"
            " FROM `slope` a JOIN `slope` b ON b.`resort_id` = a.`resort_id`"
            " JOIN `resort` r ON r.`resort_id` = a.`resort_id`"
            f" WHERE r.`code` = {sql_str(code)} AND a.`name` = {sql_str(a)} AND b.`name` = {sql_str(b)};"
        )
    MAP_DIR.mkdir(parents=True, exist_ok=True)
    for name, body in staged_maps.items():
        (MAP_DIR / name).write_text(body, encoding="utf-8")
    # 이번에 맵을 만들지 않은 리조트의 예전 파일은 지운다. 화면이 낡은 선을 그리지 않게 한다.
    for stale in MAP_DIR.glob("*.json"):
        if stale.name not in staged_maps:
            stale.unlink()
            print(f"낡은 맵 파일 삭제: {stale.name}")
    SQL_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")

    for code, total, matched_count, has_map in report:
        print(f"{code}: 슬로프 {total}, OSM 연결 {matched_count}, 맵 {'있음' if has_map else '없음'}")
    print(f"관계 줄 {len(connection_rows)}")


if __name__ == "__main__":
    main()
