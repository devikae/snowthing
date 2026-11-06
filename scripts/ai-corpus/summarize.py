#!/usr/bin/env python3
"""AI 멤버 글감 요약표를 만든다 (IKA-35). 수집 기능은 없다. 이용 허락을 받은 코퍼스가 있을 때만 쓴다.

입력: 한 줄에 {"board", "title", "body"} 하나인 jsonl.gz
출력: 게시판별 글 길이 분포, 말끝 비율, 자주 나온 낱말(JSON). 원문 문장은 내보내지 않는다.
정치 글은 집계 전에 뺀다. 금칙어는 백엔드 필터와 같은 파일을 읽는다.

    python summarize.py summary corpus.jsonl.gz > summary.json
    python summarize.py check corpus.jsonl.gz generated.txt   # 생성 글과 원문의 유사도 검사
"""
import collections
import gzip
import json
import pathlib
import re
import statistics
import sys
import unicodedata

TERMS = pathlib.Path(__file__).resolve().parents[2] / "backend/src/main/resources/ai/political-terms.txt"
AFTER_WORD = r"(?=$|[^가-힣a-z0-9]|은|는|이|가|을|를|의|에|도|만|로|으로|와|과|랑|들|놈|년|충|하|적|철|때|쪽|측|권|질|인)"
SIMILAR = 0.5
TOP_WORDS = 80
# 낱말 하나가 이 수만큼의 글에 나와야 표에 넣는다. 한 글에만 있는 표현을 옮기지 않기 위해서다.
MIN_DOCS = 3


def load_terms():
    section, anywhere, patterns = "", [], []
    for raw in TERMS.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line in ("[any]", "[word]", "[regex]"):
            section = line
        elif section == "[any]":
            anywhere.append(line.lower())
        elif section == "[word]":
            patterns.append(re.compile(r"(?<![가-힣a-z0-9])" + re.escape(line.lower()) + AFTER_WORD))
        else:
            patterns.append(re.compile(line))
    return anywhere, patterns


ANYWHERE, PATTERNS = load_terms()


def is_political(text):
    normalized = "".join(c for c in unicodedata.normalize("NFKC", text) if unicodedata.category(c) != "Cf").lower()
    compact = re.sub(r"[\W_]+", "", normalized)
    return any(term in compact for term in ANYWHERE) or any(p.search(normalized) for p in PATTERNS)


def read(path):
    with gzip.open(path, "rt", encoding="utf-8") as lines:
        for line in lines:
            yield json.loads(line)


def percentiles(values):
    if len(values) < 2:
        return {"count": len(values)}
    q = statistics.quantiles(values, n=10)
    return {"count": len(values), "p10": q[0], "p50": q[4], "p90": q[8]}


def summary(path):
    boards = collections.defaultdict(lambda: {"title": [], "body": [], "ends": collections.Counter(), "docs": collections.Counter()})
    excluded = 0
    for post in read(path):
        text = f"{post['title']}\n{post['body']}"
        if is_political(text):
            excluded += 1
            continue
        board = boards[post["board"]]
        board["title"].append(len(post["title"]))
        board["body"].append(len(post["body"]))
        for sentence in re.split(r"[.!?\n]+", post["body"]):
            tail = re.sub(r"\W+$", "", sentence)[-2:]
            if tail:
                board["ends"][tail] += 1
        board["docs"].update(set(re.findall(r"[가-힣]{2,6}", text)))
    return {
        "excludedPolitical": excluded,
        "boards": {
            name: {
                "titleLength": percentiles(b["title"]),
                "bodyLength": percentiles(b["body"]),
                "sentenceEnds": b["ends"].most_common(20),
                "words": [w for w, n in b["docs"].most_common(TOP_WORDS) if n >= MIN_DOCS],
            }
            for name, b in boards.items()
        },
    }


def grams(text):
    compact = re.sub(r"[\W_]+", "", text.lower())
    return {compact[i : i + 3] for i in range(len(compact) - 2)}


def check(path, generated):
    """생성 글 한 줄마다 코퍼스 전체와 견주어 너무 비슷한 줄을 찾는다. 찾으면 종료 코드 1."""
    corpus = [grams(f"{p['title']} {p['body']}") for p in read(path)]
    corpus += [grams(p["title"]) for p in read(path)]
    failed = 0
    for line in pathlib.Path(generated).read_text(encoding="utf-8").splitlines():
        mine = grams(line)
        if not mine:
            continue
        if any(len(mine & other) / len(mine | other) >= SIMILAR for other in corpus if other):
            failed += 1
            print(f"too similar: {line[:40]}")
    return failed


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) == 3 and sys.argv[1] == "summary":
        json.dump(summary(sys.argv[2]), sys.stdout, ensure_ascii=False, indent=2)
    elif len(sys.argv) == 4 and sys.argv[1] == "check":
        sys.exit(1 if check(sys.argv[2], sys.argv[3]) else 0)
    else:
        sys.exit(__doc__)
