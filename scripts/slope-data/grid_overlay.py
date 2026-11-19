"""공식 슬로프맵 위에 픽셀 격자를 겹친 PNG를 만든다. manual/{CODE}.json의 drawPx와 _anchors 좌표를 읽을 때 쓴다.

실행: python -X utf8 scripts/slope-data/grid_overlay.py 맵이미지(또는 PDF) 출력.png [간격=100] [자르기 x0,y0,x1,y1]
- PDF면 첫 쪽을 이미지로 바꾼다(pymupdf 필요).
- 좌표는 항상 원본 이미지 픽셀 기준이다. 자르기를 줘도 격자 숫자는 원본 좌표로 적는다.
- 원본 이미지는 저장소에 넣지 않는다(리조트 저작물). 좌표만 manual JSON에 남긴다.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw


def load(path):
    if str(path).lower().endswith(".pdf"):
        import pymupdf

        page = pymupdf.open(path)[0]
        pix = page.get_pixmap(dpi=150)
        return Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    return Image.open(path).convert("RGB")


def main(src, out, step=100, crop=None):
    image = load(src)
    if src.lower().endswith(".pdf"):
        # PDF는 매번 같은 해상도로 바꿔 쓰도록 변환본을 옆에 저장한다. 좌표는 이 변환본 기준이다.
        image.save(Path(out).with_suffix(".base.png"))
    x0, y0, x1, y1 = crop or (0, 0, image.width, image.height)
    view = image.crop((x0, y0, x1, y1))
    draw = ImageDraw.Draw(view)
    for x in range((x0 // step + 1) * step, x1, step):
        major = x % (step * 5) == 0
        draw.line([(x - x0, 0), (x - x0, view.height)], fill=(255, 0, 0) if major else (255, 140, 140), width=2 if major else 1)
        draw.text((x - x0 + 2, 2), str(x), fill=(200, 0, 0))
        draw.text((x - x0 + 2, view.height // 2), str(x), fill=(200, 0, 0))
    for y in range((y0 // step + 1) * step, y1, step):
        major = y % (step * 5) == 0
        draw.line([(0, y - y0), (view.width, y - y0)], fill=(0, 0, 255) if major else (140, 140, 255), width=2 if major else 1)
        draw.text((2, y - y0 + 2), str(y), fill=(0, 0, 200))
        draw.text((view.width // 2, y - y0 + 2), str(y), fill=(0, 0, 200))
    view.save(out)
    print(f"원본 {image.width}x{image.height}, 저장 {out}")


if __name__ == "__main__":
    args = sys.argv[1:]
    step = int(args[2]) if len(args) > 2 else 100
    crop = tuple(int(v) for v in args[3].split(",")) if len(args) > 3 else None
    main(args[0], args[1], step, crop)
