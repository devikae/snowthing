"""IKA-112 휘구리 칼럼 수집기. 로컬에서 한 번만 실행한다.

디시 스노보드 마이너 갤러리 칼럼 탭(말머리 130)에서 고정닉 ID로 글을 골라, 본문을 눈팅 에디터 형식(마크다운)으로 바꾸고
예약 게시 대기열(column_repost_queue)에 넣을 SQL을 만든다.

- 닉네임은 누구나 같게 쓸 수 있으므로 고정닉 ID(data-uid)로 고른다.
- 디시는 연달아 15번쯤 요청하면 빈 응답을 준다. 글 사이를 --delay초(기본 25초) 쉬고, 받은 원문 HTML은 --work-dir에 남겨
  막혀서 멈춰도 다시 실행하면 이어서 받는다.
- 사진은 디시 주소 그대로 둔다. 백엔드가 게시하는 순간 받아서 눈팅 사진으로 올린다(올린 사진은 1시간 안에 글에 붙어야 하므로).
- 유튜브는 주소 한 줄로 둔다(눈팅 화면이 플레이어로 바꾼다). 디시 자체 영상은 원문 링크로 바꾼다.
- 결과물(SQL, 미리보기)은 원작자 글이라 공개 저장소에 두지 않는다. --work-dir은 저장소 밖을 쓴다.

사용:
    python -I scripts/column_repost/collect.py --work-dir C:/somewhere/column-repost
"""

from __future__ import annotations

import argparse
import html
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse

LIST_URL = "https://gall.dcinside.com/mgallery/board/lists/"
VIEW_URL = "https://gall.dcinside.com/mgallery/board/view/"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
    " Chrome/130.0 Safari/537.36"
)
VIDEO_LINK_TEXT = "▶ 영상은 원문에서 보기"
DC_SIGNATURES = ("- dc official App", "- dc App")
BLOCK_TAGS = {"p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "table", "tr"}
SKIP_TAGS = {"script", "style", "noscript"}
YOUTUBE_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


@dataclass(frozen=True)
class ListedPost:
    no: int
    title: str
    date: str


def source_url(gallery: str, no: int) -> str:
    return f"{VIEW_URL}?{urlencode({'id': gallery, 'no': no})}"


# ---------------------------------------------------------------- 목록


class _ListParser(HTMLParser):
    """목록 HTML에서 (글 번호, 작성자 uid, 제목, 날짜)를 뽑는다."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.rows: list[dict] = []
        self._row: dict | None = None
        self._in_title_link = False
        self._title_depth = 0

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        classes = (a.get("class") or "").split()
        if tag == "tr" and "ub-content" in classes:
            self._row = {"no": a.get("data-no"), "uid": None, "title": "", "date": ""}
            self.rows.append(self._row)
        if self._row is None:
            return
        if tag == "td" and "gall_writer" in classes:
            self._row["uid"] = a.get("data-uid") or None
        if tag == "td" and "gall_date" in classes:
            self._row["date"] = a.get("title") or ""
        if tag == "td" and "gall_tit" in classes:
            self._title_depth = 1
        elif self._title_depth and tag == "a" and not self._row["title"] and not a.get("class"):
            self._in_title_link = True

    def handle_endtag(self, tag):
        if tag == "a":
            self._in_title_link = False
        if tag == "td":
            self._title_depth = 0
        if tag == "tr":
            self._row = None

    def handle_data(self, data):
        if self._row is not None and self._in_title_link:
            self._row["title"] += data


def parse_list(page_html: str, uid: str) -> list[ListedPost]:
    parser = _ListParser()
    parser.feed(page_html)
    posts = []
    for row in parser.rows:
        if row["uid"] != uid or not (row["no"] or "").isdigit():
            continue
        posts.append(ListedPost(int(row["no"]), " ".join(row["title"].split()), row["date"][:10]))
    return posts


# ---------------------------------------------------------------- 본문


def youtube_watch_url(src: str) -> str | None:
    """유튜브 임베드나 링크 주소를 watch 주소로 바꾼다. 유튜브가 아니면 None."""
    if src.startswith("//"):
        src = "https:" + src
    parsed = urlparse(src)
    host = (parsed.hostname or "").lower()
    video_id = None
    if host == "youtu.be":
        video_id = parsed.path.strip("/").split("/")[0]
    elif host in {"youtube.com", "www.youtube.com", "m.youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com"}:
        parts = parsed.path.strip("/").split("/")
        if parts and parts[0] == "watch":
            video_id = (parse_qs(parsed.query).get("v") or [None])[0]
        elif len(parts) >= 2 and parts[0] in {"embed", "shorts", "v", "live"}:
            video_id = parts[1]
    if not video_id or not YOUTUBE_ID.match(video_id):
        return None
    start = (parse_qs(parsed.query).get("start") or parse_qs(parsed.query).get("t") or [None])[0]
    url = f"https://www.youtube.com/watch?v={video_id}"
    if start and re.fullmatch(r"\d+s?", start):
        url += f"&t={start.rstrip('s')}s"
    return url


def https_url(src: str) -> str | None:
    if src.startswith("//"):
        src = "https:" + src
    if src.startswith("http://"):
        src = "https://" + src[len("http://"):]
    return src if src.startswith("https://") else None


def dc_movie_page_url(src: str) -> str | None:
    """디시 동영상 플레이어 주소를 한 가지 모양으로 맞춘다. 아니면 None."""
    src = https_url(src) or ""
    parsed = urlparse(src)
    if parsed.hostname != "gall.dcinside.com" or parsed.path != "/board/movie/movie_view":
        return None
    no = (parse_qs(parsed.query).get("no") or [""])[0]
    return f"https://gall.dcinside.com/board/movie/movie_view?no={no}" if no.isdigit() else None


def escape_markdown(text: str) -> str:
    """글자 안의 마크다운 기호를 글자로 둔다. 줄 맨 앞 기호(제목, 인용, 목록)는 줄을 만들 때 따로 막는다."""
    return re.sub(r"([\\`*_\[\]<~|])", r"\\\1", text)


def escape_line_start(line: str) -> str:
    # "2. 제목"을 그대로 두면 줄마다 번호 목록이 새로 시작돼 모두 "1."로 보인다. 번호는 글자로 남긴다.
    numbered = re.match(r"^(\d+)([.)])(\s)", line)
    if numbered:
        return f"{numbered.group(1)}\\{numbered.group(2)}{line[numbered.end(2):]}"
    return "\\" + line if line[:1] in {"#", ">", "+", "-"} else line


class _BodyConverter(HTMLParser):
    """디시 본문(.write_div 안) HTML을 줄 목록으로 바꾼다. 한 줄이 마크다운 문단 하나가 된다."""

    def __init__(self, video_link: str) -> None:
        super().__init__(convert_charrefs=True)
        self.lines: list[str] = []
        self._current: list[str] = []
        self._skip = 0
        self._link: dict | None = None
        self._video_link = video_link
        self._in_video = False
        self._video_source_taken = False

    def _flush(self) -> None:
        line = re.sub(r" {2,}", " ", "".join(self._current)).strip()
        self._current = []
        if line:
            self.lines.append(escape_line_start(line))

    def _block(self, markdown: str) -> None:
        self._flush()
        self.lines.append(markdown)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in SKIP_TAGS:
            self._skip += 1
            return
        if self._skip:
            return
        if tag in BLOCK_TAGS or tag == "br":
            self._flush()
        elif tag == "img":
            src = https_url(a.get("data-original") or a.get("src") or "")
            if src:
                self._block(f"![]({src})")
        elif tag == "video":
            # 디시가 GIF를 바꿔 둔 짧은 반복 mp4. 주소는 안쪽 <source>에 있다. data-src는 GIF 원본이라 쓰지 않는다.
            src = https_url(a.get("src") or "")
            if src:
                self._block(f"![]({src})")
            self._in_video = True
        elif tag == "source" and self._in_video:
            src = https_url(a.get("src") or "")
            if src and not self._video_source_taken:
                self._block(f"![]({src})")
                self._video_source_taken = True
        elif tag in {"iframe", "embed"}:
            src = a.get("src") or a.get("data-src") or ""
            youtube = youtube_watch_url(src)
            movie = dc_movie_page_url(src)
            if youtube:
                self._block(youtube)
            elif movie:
                # 디시 동영상 플레이어. 게시할 때 백엔드가 이 페이지에서 실제 mp4를 찾아 옮긴다.
                self._block(f"![]({movie})")
            elif "movie" in src or "dcinside" in src:
                self._block(f"[{VIDEO_LINK_TEXT}]({self._video_link})")
        elif tag == "a":
            self._link = {"href": a.get("href") or "", "text": []}

    def handle_endtag(self, tag):
        if tag == "video":
            self._in_video = False
            self._video_source_taken = False
            return
        if tag in SKIP_TAGS:
            self._skip = max(0, self._skip - 1)
            return
        if self._skip:
            return
        if tag == "a" and self._link is not None:
            link, self._link = self._link, None
            text = "".join(link["text"]).strip()
            href = link["href"]
            youtube = youtube_watch_url(href) if href else None
            if youtube:
                if text and youtube_watch_url(text) is None and text != href:
                    self._current.append(escape_markdown(text) + " ")
                self._block(youtube)
            elif href.startswith(("https://", "http://")):
                self._current.append(href if not text or text == href else f"[{escape_markdown(text)}]({href})")
            else:
                self._current.append(escape_markdown(text))
        elif tag in BLOCK_TAGS:
            self._flush()

    def handle_data(self, data):
        if self._skip:
            return
        # HTML처럼 소스 안의 줄바꿈과 연속 공백은 공백 하나로 본다. 줄은 p, div, br로만 나눈다.
        text = re.sub(r"\s+", " ", data.replace("\xa0", " "))
        if self._link is not None:
            self._link["text"].append(text)
            return
        bare = youtube_watch_url(text.strip()) if text.strip().startswith(("http", "//")) else None
        if bare:
            self._block(bare)
        else:
            self._current.append(escape_markdown(text))

    def close(self):
        super().close()
        self._flush()


def extract_write_div(view_html: str) -> str | None:
    """보기 화면에서 본문 영역 HTML만 잘라낸다. 디시 본문은 div.write_div다."""
    start = re.search(r'<div[^>]*class="[^"]*\bwrite_div\b[^"]*"[^>]*>', view_html)
    if not start:
        return None
    depth, pos = 1, start.end()
    tag = re.compile(r"<(/?)div\b[^>]*>", re.IGNORECASE)
    while depth:
        match = tag.search(view_html, pos)
        if not match:
            return view_html[start.end():]
        depth += -1 if match.group(1) else 1
        pos = match.end()
    return view_html[start.end(): match.start()]


def extract_title(view_html: str) -> str | None:
    match = re.search(r'<span class="title_subject">(.*?)</span>', view_html, re.S)
    return " ".join(html.unescape(re.sub(r"<[^>]+>", "", match.group(1))).split()) if match else None


def to_markdown(body_html: str, video_link: str) -> str:
    converter = _BodyConverter(video_link)
    converter.feed(body_html)
    converter.close()
    signatures = set(DC_SIGNATURES) | {escape_line_start(escape_markdown(s)) for s in DC_SIGNATURES}
    lines = [line for line in converter.lines if line.strip() not in signatures]
    deduped: list[str] = []
    for line in lines:
        # 영상 하나가 임베드와 링크로 두 번 잡히는 경우가 있어 바로 앞 줄과 같으면 하나만 남긴다.
        if deduped and line == deduped[-1]:
            continue
        deduped.append(line)
    return "\n\n".join(deduped).strip() + "\n"


# ---------------------------------------------------------------- SQL


def sql_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''").replace("\0", "") + "'"


def build_sql(items: list[tuple[int, str, str]]) -> str:
    """items: (원문 번호, 제목, 본문). 오래된 글(번호가 작은 글)부터 순번을 매긴다."""
    lines = [
        "-- IKA-112 휘구리 칼럼 예약 게시 대기열. 원작자 글이라 저장소에 커밋하지 않는다.",
        "-- 065_migration_column_repost.sql을 적용한 뒤 실행한다. 같은 원문이 이미 있으면 건너뛴다(INSERT IGNORE).",
        "SET NAMES utf8mb4;",
    ]
    for sequence, (no, title, content) in enumerate(sorted(items), start=1):
        lines.append(
            "INSERT IGNORE INTO `column_repost_queue`"
            " (`sequence_no`, `source_no`, `title`, `content`, `status`, `attempts`, `created_at`) VALUES"
            f" ({sequence}, {no}, {sql_string(title[:200])}, {sql_string(content)}, 'PENDING', 0, NOW(6));"
        )
    return "\n".join(lines) + "\n"


def build_update_sql(items: list[tuple[int, str, str]]) -> str:
    """이미 넣은 대기열 줄의 본문만 새 변환 결과로 바꾼다. 게시된 줄은 표만 바뀌고 글은 관리자 갱신 API로 바꾼다."""
    lines = [
        "-- IKA-112 휘구리 칼럼 대기열 본문 갱신. 원작자 글이라 저장소에 커밋하지 않는다.",
        "SET NAMES utf8mb4;",
    ]
    for no, _title, content in sorted(items):
        lines.append(f"UPDATE `column_repost_queue` SET `content` = {sql_string(content)} WHERE `source_no` = {no};")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------- 네트워크


def fetch(url: str, referer: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Referer": referer})
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read().decode("utf-8", errors="replace")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--gallery", default="snowboard")
    parser.add_argument("--head", default="130", help="말머리 번호(칼럼 130)")
    parser.add_argument("--uid", default="wwwmskim97", help="고정닉 ID")
    parser.add_argument("--max-pages", type=int, default=20)
    parser.add_argument("--delay", type=float, default=25.0)
    args = parser.parse_args(argv)

    repo_root = Path(__file__).resolve().parents[2]
    work = args.work_dir.resolve()
    if work == repo_root or repo_root in work.parents:
        print("--work-dir은 저장소 밖이어야 한다(원작자 글을 커밋하지 않기 위해).", file=sys.stderr)
        return 2
    raw = work / "raw"
    raw.mkdir(parents=True, exist_ok=True)

    referer = f"{LIST_URL}?{urlencode({'id': args.gallery})}"
    listed: dict[int, ListedPost] = {}
    seen_rows: set[int] = set()
    for page in range(1, args.max_pages + 1):
        url = f"{LIST_URL}?{urlencode({'id': args.gallery, 'search_head': args.head, 'page': page})}"
        page_html = fetch(url, referer)
        rows = {int(no) for no in re.findall(r'<tr class="ub-content[^"]*"[^>]*data-no="(\d+)"', page_html)}
        # 마지막 쪽을 넘기면 디시는 빈 목록이나 앞쪽 목록을 다시 준다.
        if not rows or rows <= seen_rows:
            break
        seen_rows |= rows
        found = parse_list(page_html, args.uid)
        for post in found:
            listed[post.no] = post
        print(f"목록 {page}쪽: 전체 {len(rows)}개 중 {len(found)}개", flush=True)
        time.sleep(args.delay)

    print(f"고정닉 {args.uid} 칼럼 {len(listed)}개", flush=True)
    items = []
    for no in sorted(listed):
        cache = raw / f"{no}.html"
        if not cache.exists():
            time.sleep(args.delay)
            view_html = fetch(source_url(args.gallery, no), referer)
            if extract_write_div(view_html) is None:
                print(f"{no}: 본문을 받지 못함(디시가 막았을 수 있음). 잠시 뒤 다시 실행하면 이어서 받는다.", file=sys.stderr)
                return 1
            cache.write_text(view_html, encoding="utf-8")
            print(f"{no}: 받음", flush=True)
        view_html = cache.read_text(encoding="utf-8")
        body = extract_write_div(view_html)
        title = extract_title(view_html) or listed[no].title
        items.append((no, title, to_markdown(body or "", source_url(args.gallery, no))))

    (work / "queue.sql").write_text(build_sql(items), encoding="utf-8")
    (work / "queue-update.sql").write_text(build_update_sql(items), encoding="utf-8")
    preview = [f"# {seq}. {title} (원문 {no}, {listed[no].date})\n\n{content}" for seq, (no, title, content) in enumerate(items, 1)]
    (work / "preview.md").write_text("\n---\n\n".join(preview), encoding="utf-8")
    print(f"완료: {work / 'queue.sql'}, {work / 'preview.md'}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
