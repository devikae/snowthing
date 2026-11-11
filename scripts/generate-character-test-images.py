"""캐릭터 테스트 임시 픽셀 그림을 코드로 그린다. 캐릭터 8장, 스키 1장, 직업별 보드 8장이다.

40칸 높이 격자에 그린 뒤 최근접 보간으로 12배 키운다. 외부 그림 없이 도형만 쓰고, 특정 게임의 캐릭터나 이름을 쓰지 않는다.
장비는 캐릭터와 따로 그려 화면에서 겹친다(frontend/app/character-test/data.ts의 gearBox).
최종 그림이 정해지면 frontend/public/character-test/ 아래 파일만 바꾸면 된다.

실행: python scripts/generate-character-test-images.py [확인용 모음 그림 경로]
필요: Pillow
"""

import math
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

GRID = 40
GEAR_WIDTH = 16
SCALE = 12
OUT_DIR = Path(__file__).resolve().parent.parent / "frontend" / "public" / "character-test"

OUTLINE = (38, 42, 58, 255)
SKIN = (255, 224, 196, 255)
SKIN_SHADE = (244, 200, 170, 255)
BLUSH = (248, 158, 150, 255)
EYE = (38, 42, 58, 255)
WHITE = (255, 255, 255, 255)
MOUTH = (176, 66, 66, 255)
BOOT = (66, 70, 90, 255)
BOOT_LIGHT = (104, 110, 134, 255)
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, width=GRID, height=GRID):
        self.width, self.height = width, height
        self.image = Image.new("RGBA", (width, height), CLEAR)
        self.pixels = self.image.load()

    def rect(self, x, y, w, h, color):
        for py in range(y, y + h):
            for px in range(x, x + w):
                if 0 <= px < self.width and 0 <= py < self.height:
                    self.pixels[px, py] = color

    def dot(self, x, y, color):
        self.rect(x, y, 1, 1, color)

    def blob(self, x, y, w, h, color, radius=1):
        """모서리를 계단식으로 깎은 사각형."""
        for row in range(h):
            inset = max(0, radius - min(row, h - 1 - row))
            self.rect(x + inset, y + row, w - inset * 2, 1, color)

    def outline(self):
        """불투명한 칸 둘레의 빈 칸을 어두운 색으로 채운다."""
        source = self.image.copy().load()
        for y in range(self.height):
            for x in range(self.width):
                if source[x, y][3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.width and 0 <= ny < self.height and source[nx, ny][3] != 0:
                        self.pixels[x, y] = OUTLINE
                        break

    def save(self, name):
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        size = (self.width * SCALE, self.height * SCALE)
        self.image.resize(size, Image.NEAREST).save(OUT_DIR / f"{name}.png", optimize=True)


def body(c, jacket, dark, light, pants, pants_dark, mitten):
    """큰 머리 아래 짧은 스키복. 지퍼, 밑단, 벙어리장갑, 큰 부츠를 공통으로 둔다."""
    c.blob(13, 25, 14, 8, jacket)
    c.rect(24, 26, 2, 6, dark)
    c.rect(14, 26, 2, 3, light)
    c.rect(19, 25, 2, 7, dark)
    c.rect(13, 31, 14, 2, dark)
    c.rect(15, 33, 4, 2, pants)
    c.rect(21, 33, 4, 2, pants)
    c.rect(18, 33, 1, 2, pants_dark)
    c.rect(24, 33, 1, 2, pants_dark)
    c.blob(14, 35, 6, 3, BOOT)
    c.blob(20, 35, 6, 3, BOOT)
    c.rect(15, 35, 3, 1, BOOT_LIGHT)
    c.rect(21, 35, 3, 1, BOOT_LIGHT)
    # 왼팔은 내린다. 오른팔은 소품마다 따로 그린다.
    c.rect(10, 26, 3, 4, jacket)
    c.blob(9, 29, 4, 3, mitten)


def right_arm(c, jacket, mitten, raised=False):
    if raised:
        c.rect(27, 22, 3, 5, jacket)
        c.blob(27, 19, 4, 3, mitten)
    else:
        c.rect(27, 26, 3, 4, jacket)
        c.blob(27, 29, 4, 3, mitten)


def head(c, eyes="round"):
    c.blob(9, 7, 22, 18, SKIN, radius=3)
    c.rect(11, 23, 18, 1, SKIN_SHADE)
    if eyes == "round":
        for x in (13, 24):
            c.rect(x, 14, 3, 4, EYE)
            c.dot(x, 14, WHITE)
            c.dot(x + 2, 17, (92, 110, 150, 255))
    elif eyes == "smile":
        for x in (12, 23):
            c.dot(x, 16, EYE)
            c.rect(x + 1, 15, 3, 1, EYE)
            c.dot(x + 4, 16, EYE)
    elif eyes == "focus":
        for x in (13, 24):
            c.rect(x, 15, 3, 3, EYE)
            c.dot(x, 15, WHITE)
        c.rect(12, 13, 5, 1, EYE)
        c.rect(23, 13, 5, 1, EYE)
    elif eyes == "sleepy":
        for x in (13, 24):
            c.rect(x, 16, 3, 1, EYE)
            c.rect(x, 17, 3, 1, EYE)
            c.dot(x, 17, SKIN)
    c.rect(11, 19, 3, 2, BLUSH)
    c.rect(26, 19, 3, 2, BLUSH)
    c.rect(19, 20, 2, 1, MOUTH)


def hair(c, color, shade, light):
    c.blob(8, 4, 24, 10, color, radius=3)
    # 둥근 머리 아랫단이 옆머리와 만나는 칸을 메워 눈 옆이 파여 보이지 않게 한다
    c.rect(8, 10, 3, 6, color)
    c.rect(29, 10, 3, 6, color)
    c.rect(11, 12, 18, 1, shade)
    c.rect(12, 6, 5, 1, light)
    c.rect(11, 7, 2, 1, light)


def captain():
    """대장님: 챙 넓은 조교 모자와 왼팔 완장."""
    c = Canvas()
    jacket, dark, light = (222, 66, 72, 255), (172, 44, 54, 255), (246, 118, 118, 255)
    mitten = (58, 62, 82, 255)
    body(c, jacket, dark, light, (58, 66, 104, 255), (42, 48, 82, 255), mitten)
    head(c, "round")
    # 모자 아래로 옆머리만 살짝 보인다.
    c.rect(9, 13, 2, 3, (102, 68, 46, 255))
    c.rect(29, 13, 2, 3, (102, 68, 46, 255))
    # 조교 모자: 머리를 덮는 몸통을 눈썹 바로 위까지 눌러쓰고, 넓은 챙을 두른다.
    hat, hat_dark, hat_light = (104, 120, 70, 255), (76, 90, 50, 255), (136, 152, 96, 255)
    c.blob(11, 1, 18, 3, hat, radius=1)
    c.blob(9, 3, 22, 8, hat, radius=1)
    c.rect(12, 4, 5, 1, hat_light)
    c.rect(19, 1, 2, 3, hat_dark)
    c.rect(9, 9, 22, 2, (58, 62, 82, 255))
    c.rect(3, 11, 34, 2, hat)
    c.rect(3, 12, 34, 1, hat_dark)
    c.blob(17, 4, 6, 5, (246, 198, 66, 255))
    c.rect(19, 5, 2, 3, (222, 66, 72, 255))
    # 왼팔 완장
    c.rect(9, 26, 5, 3, (255, 214, 64, 255))
    c.rect(9, 27, 5, 1, (58, 62, 82, 255))
    right_arm(c, jacket, mitten)
    c.outline()
    return c


def resort_insider():
    """리조트 인싸: 털 달린 분홍 패딩, 귀마개, 선글라스, 컵."""
    c = Canvas()
    jacket, dark, light = (242, 124, 176, 255), (208, 86, 140, 255), (252, 170, 206, 255)
    mitten = (255, 255, 255, 255)
    body(c, jacket, dark, light, (76, 64, 120, 255), (56, 46, 96, 255), mitten)
    head(c, "round")
    gold, gold_dark, gold_light = (252, 208, 92, 255), (226, 174, 58, 255), (255, 232, 150, 255)
    hair(c, gold, gold_dark, gold_light)
    c.blob(10, 1, 20, 6, gold, radius=2)
    c.rect(13, 3, 6, 1, gold_light)
    # 귀마개
    muff, muff_dark = (255, 255, 255, 255), (232, 226, 240, 255)
    c.rect(10, 2, 20, 1, (236, 96, 150, 255))
    c.blob(4, 13, 6, 8, muff, radius=2)
    c.blob(30, 13, 6, 8, muff, radius=2)
    c.rect(5, 18, 4, 1, muff_dark)
    c.rect(31, 18, 4, 1, muff_dark)
    # 털 칼라
    c.blob(11, 23, 18, 3, WHITE)
    shades = (40, 44, 62, 255)
    c.blob(11, 13, 8, 5, shades)
    c.blob(21, 13, 8, 5, shades)
    c.rect(19, 14, 2, 1, shades)
    c.rect(13, 14, 2, 1, (130, 210, 245, 255))
    c.rect(23, 14, 2, 1, (130, 210, 245, 255))
    c.rect(18, 20, 4, 1, MOUTH)
    right_arm(c, jacket, mitten, raised=True)
    c.blob(30, 22, 7, 8, (255, 250, 240, 255))
    c.rect(31, 22, 5, 2, (150, 96, 60, 255))
    c.rect(37, 24, 2, 3, (255, 250, 240, 255))
    c.outline()
    return c


def hype():
    """바람잡이: 형광 보드복, 골지 밑단의 검은 니트 비니, 메가폰."""
    c = Canvas()
    jacket, dark, light = (176, 232, 64, 255), (130, 186, 40, 255), (216, 248, 130, 255)
    mitten = (58, 62, 82, 255)
    body(c, jacket, dark, light, (58, 62, 82, 255), (40, 44, 60, 255), mitten)
    c.rect(10, 26, 3, 3, (58, 62, 82, 255))
    head(c, "smile")
    beanie, knit, rib = (44, 46, 58, 255), (66, 68, 82, 255), (70, 72, 86, 255)
    c.blob(9, 1, 22, 11, beanie, radius=5)
    # 몸통과 밑단 사이가 파이지 않게 메운다
    c.rect(9, 6, 22, 3, beanie)
    for y in range(2, 9):
        for x in range(10, 30):
            if (x + y) % 4 == 0 and c.pixels[x, y][3]:
                c.dot(x, y, knit)
    # 밑단은 모서리를 깎지 않은 꽉 찬 띠. 골지 줄은 위아래 한 칸씩 남겨 테두리가 고르게 보이게 한다.
    c.rect(8, 8, 24, 6, beanie)
    for x in range(9, 31, 2):
        c.rect(x, 9, 1, 4, rib)
    c.rect(25, 9, 3, 3, (182, 122, 72, 255))
    # 비니 위에 걸친 은색 테 스포츠 선글라스: 둥근 렌즈 두 개, 가운데 다리, 양옆으로 감기는 테
    silver, silver_dark = (214, 220, 230, 255), (156, 164, 178, 255)
    lens, lens_light, lens_shine = (72, 84, 108, 255), (104, 118, 146, 255), (220, 230, 244, 255)
    c.rect(9, 5, 22, 1, silver_dark)
    for x in (11, 21):
        c.blob(x, 3, 8, 5, silver, radius=2)
        c.blob(x + 1, 4, 6, 3, lens, radius=1)
        c.rect(x + 1, 6, 6, 1, lens_light)
        c.rect(x + 2, 4, 2, 1, lens_shine)
    c.rect(19, 5, 2, 1, silver)
    c.blob(17, 19, 6, 4, MOUTH)
    c.rect(18, 19, 4, 1, WHITE)
    right_arm(c, (58, 62, 82, 255), mitten, raised=True)
    c.rect(29, 17, 2, 4, (240, 240, 246, 255))
    c.rect(31, 16, 2, 6, (240, 240, 246, 255))
    c.rect(33, 15, 2, 8, (240, 240, 246, 255))
    c.rect(35, 14, 2, 10, (224, 62, 72, 255))
    c.outline()
    return c


def mood_maker():
    """분위기 메이커: 곰 귀 털모자, 별 무늬 패딩, 음표."""
    c = Canvas()
    jacket, dark, light = (156, 116, 236, 255), (122, 82, 200, 255), (192, 162, 250, 255)
    mitten = (252, 214, 84, 255)
    body(c, jacket, dark, light, (62, 62, 102, 255), (46, 46, 82, 255), mitten)
    # 옷에 별 무늬
    c.rect(15, 28, 3, 1, mitten)
    c.rect(16, 27, 1, 3, mitten)
    c.rect(22, 27, 3, 1, mitten)
    c.rect(23, 26, 1, 3, mitten)
    head(c, "smile")
    fur, fur_dark, fur_light = (178, 124, 84, 255), (146, 98, 62, 255), (214, 166, 122, 255)
    red = (240, 112, 114, 255)
    c.blob(3, 12, 6, 9, red, radius=2)
    c.blob(31, 12, 6, 9, red, radius=2)
    c.blob(8, 4, 24, 10, fur, radius=3)
    c.rect(8, 11, 24, 3, fur_dark)
    c.rect(12, 6, 5, 1, fur_light)
    # 곰 귀
    for x in (7, 27):
        c.blob(x, 0, 6, 6, fur, radius=2)
        c.blob(x + 2, 2, 2, 2, (250, 180, 170, 255), radius=0)
    c.blob(17, 20, 6, 3, MOUTH)
    right_arm(c, jacket, mitten, raised=True)
    note = (60, 196, 176, 255)
    c.rect(33, 1, 6, 2, note)
    c.rect(33, 1, 1, 7, note)
    c.rect(38, 1, 1, 6, note)
    c.rect(31, 7, 3, 2, note)
    c.rect(36, 6, 3, 2, note)
    c.outline()
    return c


def grinder():
    """연습벌레: 헬멧, 고글, 가슴 띠가 있는 훈련복, 땀."""
    c = Canvas()
    jacket, dark, light = (62, 156, 114, 255), (42, 122, 86, 255), (110, 200, 156, 255)
    mitten = (246, 246, 250, 255)
    body(c, jacket, dark, light, (50, 60, 84, 255), (36, 44, 66, 255), mitten)
    # 가슴 띠
    c.rect(13, 28, 14, 2, (248, 248, 252, 255))
    head(c, "focus")
    helmet, helmet_dark, helmet_light = (72, 176, 208, 255), (46, 138, 172, 255), (140, 214, 236, 255)
    c.blob(8, 2, 24, 11, helmet, radius=3)
    c.rect(8, 11, 24, 2, helmet_dark)
    c.rect(12, 3, 5, 1, helmet_light)
    strap = (42, 46, 64, 255)
    c.rect(8, 7, 24, 4, strap)
    c.blob(11, 7, 8, 4, (252, 192, 84, 255))
    c.blob(21, 7, 8, 4, (252, 192, 84, 255))
    c.rect(12, 8, 2, 1, WHITE)
    c.rect(22, 8, 2, 1, WHITE)
    # 땀방울: 헬멧 양옆에 같은 높이로 마주 보게 둔다(가운데 기준 대칭)
    c.rect(33, 7, 2, 3, (136, 206, 248, 255))
    c.dot(33, 6, (136, 206, 248, 255))
    c.rect(5, 7, 2, 3, (136, 206, 248, 255))
    c.dot(6, 6, (136, 206, 248, 255))
    right_arm(c, jacket, mitten)
    c.outline()
    return c


def hot_pack():
    """인간 핫팩: 귀달이 털모자, 귤색 패딩, 줄무늬 목도리, 발그레한 볼, 엄지를 세운 손."""
    c = Canvas()
    jacket, dark, light = (255, 166, 77, 255), (230, 128, 52, 255), (255, 204, 140, 255)
    mitten = (232, 84, 76, 255)
    body(c, jacket, dark, light, (96, 84, 110, 255), (74, 64, 88, 255), mitten)
    # 빵빵한 패딩: 누빔 줄 두 개와 밝은 면
    c.blob(12, 25, 16, 8, jacket, radius=2)
    c.rect(12, 28, 16, 1, dark)
    c.rect(13, 31, 14, 1, dark)
    c.rect(14, 26, 3, 1, light)
    head(c, "smile")
    # 귀달이 털모자: 갈색 모자 몸통, 크림색 털 안감이 이마와 양쪽 귀덮개에 보인다
    hat, hat_dark, hat_light = (150, 96, 64, 255), (118, 72, 46, 255), (182, 126, 88, 255)
    fur, fur_dark = (252, 240, 222, 255), (226, 208, 184, 255)
    c.blob(8, 2, 24, 10, hat, radius=5)
    c.rect(8, 7, 24, 3, hat)
    c.rect(12, 4, 5, 1, hat_light)
    c.rect(8, 9, 24, 3, fur)
    for x in range(9, 31, 3):
        c.dot(x, 10, fur_dark)
    c.blob(5, 11, 6, 9, hat, radius=2)
    c.blob(29, 11, 6, 9, hat, radius=2)
    c.blob(6, 12, 4, 7, fur, radius=1)
    c.blob(30, 12, 4, 7, fur, radius=1)
    c.rect(7, 19, 1, 3, hat_dark)
    c.rect(32, 19, 1, 3, hat_dark)
    # 발그레한 볼
    c.rect(11, 18, 4, 2, (250, 132, 120, 255))
    c.rect(25, 18, 4, 2, (250, 132, 120, 255))
    # 초록과 민트 줄무늬 목도리, 왼쪽으로 늘어진 끝
    scarf, scarf_light = (62, 160, 146, 255), (200, 240, 232, 255)
    c.blob(12, 23, 16, 3, scarf, radius=1)
    for x in range(13, 28, 3):
        c.rect(x, 23, 1, 3, scarf_light)
    c.rect(13, 26, 3, 5, scarf)
    c.rect(13, 27, 3, 1, scarf_light)
    c.rect(13, 29, 3, 1, scarf_light)
    # 왼팔은 내리고 오른팔은 얼굴에서 떨어지게 옆으로 뻗어 엄지를 세운다
    c.rect(27, 25, 3, 3, jacket)
    c.rect(29, 23, 3, 3, jacket)
    # 주먹은 가로로 넓게, 엄지는 주먹 왼쪽 끝에서 위로. 손가락 마디 선을 넣어 주먹으로 보이게 한다.
    mitten_dark = (192, 58, 56, 255)
    c.rect(31, 22, 3, 3, jacket)
    c.blob(33, 19, 6, 5, mitten, radius=1)
    c.rect(34, 17, 2, 2, mitten)
    for y in (20, 22):
        c.rect(36, y, 3, 1, mitten_dark)
    c.outline()
    return c


def gear_geek():
    """장비 덕후: 주머니 많은 조끼, 이마 위 고글, 큰 안경, 드라이버."""
    c = Canvas()
    jacket, dark, light = (72, 100, 156, 255), (52, 74, 124, 255), (112, 142, 198, 255)
    mitten = (246, 150, 60, 255)
    body(c, jacket, dark, light, (56, 60, 78, 255), (40, 44, 60, 255), mitten)
    vest, vest_dark = (96, 110, 92, 255), (70, 84, 68, 255)
    c.rect(14, 26, 12, 6, vest)
    c.rect(19, 26, 2, 6, vest_dark)
    c.rect(15, 28, 3, 3, vest_dark)
    c.rect(22, 28, 3, 3, vest_dark)
    head(c, "round")
    hair(c, (72, 114, 94, 255), (54, 88, 72, 255), (108, 152, 130, 255))
    # 고글 뒤 정수리를 둥글게 채워 고글 양옆이 파여 보이지 않게 한다
    c.blob(8, 3, 24, 8, (72, 114, 94, 255), radius=3)
    frame = (42, 46, 64, 255)
    c.rect(8, 6, 24, 2, (214, 92, 60, 255))
    c.blob(11, 2, 18, 9, frame, radius=2)
    # 렌즈는 한 색으로 채우고 왼쪽 위에 빛 반사만 둔다
    c.blob(12, 3, 16, 7, (252, 150, 60, 255), radius=1)
    c.rect(14, 4, 3, 1, WHITE)
    c.dot(14, 5, WHITE)
    glass = (40, 44, 62, 255)
    for x in (11, 22):
        c.blob(x, 12, 8, 8, glass)
        c.blob(x + 1, 13, 6, 6, (226, 244, 252, 255))
        c.rect(x + 2, 15, 3, 3, EYE)
        c.dot(x + 2, 15, WHITE)
    c.rect(19, 15, 3, 1, glass)
    c.rect(27, 25, 3, 3, jacket)
    c.rect(29, 23, 3, 3, jacket)
    c.rect(31, 21, 3, 3, jacket)
    c.blob(33, 18, 4, 4, mitten)
    metal, handle, handle_dark = (206, 212, 222, 255), (232, 70, 88, 255), (186, 46, 66, 255)
    c.blob(33, 12, 5, 6, handle)
    c.rect(34, 13, 1, 4, handle_dark)
    c.rect(36, 13, 1, 4, handle_dark)
    c.rect(35, 2, 1, 10, metal)
    c.outline()
    return c


def resort_outsider():
    """리조트 아싸: 무채색 후드를 깊이 쓰고 이어폰을 양쪽 귀에 꽂았다."""
    c = Canvas()
    jacket, dark, light = (66, 70, 88, 255), (46, 50, 66, 255), (98, 104, 124, 255)
    mitten = (40, 44, 60, 255)
    body(c, jacket, dark, light, (40, 44, 58, 255), (28, 32, 44, 255), mitten)
    c.blob(7, 4, 26, 22, jacket, radius=4)
    c.rect(11, 5, 6, 1, light)
    c.blob(11, 10, 18, 15, SKIN, radius=2)
    c.rect(13, 23, 14, 1, SKIN_SHADE)
    c.rect(11, 9, 18, 4, dark)
    for x in (14, 23):
        c.rect(x, 16, 3, 2, EYE)
        c.dot(x, 16, WHITE)
    c.rect(13, 13, 5, 1, dark)
    c.rect(22, 13, 5, 1, dark)
    c.rect(12, 19, 3, 2, BLUSH)
    c.rect(25, 19, 3, 2, BLUSH)
    c.rect(19, 21, 2, 1, MOUTH)
    wire = (245, 245, 250, 255)
    # 양쪽 귀의 이어폰. 줄은 가슴 앞에서 하나로 모인다.
    for x in (9, 29):
        c.rect(x, 15, 2, 3, wire)
    c.rect(10, 18, 1, 8, wire)
    c.rect(29, 18, 1, 8, wire)
    c.rect(10, 26, 9, 1, wire)
    c.rect(21, 26, 9, 1, wire)
    c.rect(19, 26, 2, 4, wire)
    right_arm(c, jacket, mitten)
    c.outline()
    return c


def ski_gear():
    """곧게 나란히 세운 스키 한 쌍과 폴. 앞코는 둥글게 뒤로 말리고, 폴에는 손잡이와 눈받이가 있다."""
    c = Canvas(GEAR_WIDTH, GRID)
    base, dark, stripe = (46, 120, 220, 255), (30, 88, 176, 255), (255, 214, 90, 255)
    binding, pole, grip = (58, 62, 82, 255), (214, 220, 230, 255), (232, 70, 88, 255)
    for x in (7, 11):
        c.rect(x, 5, 3, 33, base)
        c.rect(x + 2, 5, 1, 33, dark)
        c.rect(x + 1, 3, 2, 2, base)
        c.rect(x + 2, 2, 2, 1, base)
        c.dot(x + 3, 1, base)
        c.rect(x, 9, 3, 2, stripe)
        c.rect(x, 21, 3, 1, binding)
        c.rect(x, 26, 3, 1, binding)
    for x in (1, 4):
        c.rect(x, 15, 1, 23, pole)
        c.rect(x, 12, 1, 3, grip)
        c.dot(x - 1, 11, grip)
        c.dot(x + 1, 11, grip)
        c.rect(x - 1, 10, 3, 1, grip)
        c.rect(x - 1, 34, 3, 1, binding)
    c.outline()
    return c


BOARD_LENGTH = 37
BOARD_WIDTH = 7


def captain_board(x, y):
    """노란 판을 따라 굵은 검은 줄이 길게 지나고, 가운데 흰 선이 있다."""
    if x == 3:
        return (250, 250, 252, 255)
    return (40, 44, 60, 255) if x in (2, 4) else (250, 200, 60, 255)


def insider_board(x, y):
    """흰 판을 따라 흐르는 분홍 물결과 금색 선."""
    wave = 3 + round(2 * math.sin(y / 3.2))
    if abs(x - wave) <= 1:
        return (236, 88, 150, 255)
    return (252, 196, 72, 255) if x == 6 else (250, 250, 252, 255)


def hype_board(x, y):
    """짙은 회색 붓 자국 바탕에 청록 테두리, 가운데 검은 띠와 바인딩 나사 구멍."""
    last = BOARD_LENGTH - 1
    if x in (0, BOARD_WIDTH - 1) or y < 2 or y > last - 2:
        return (34, 150, 140, 255)
    if 2 <= x <= 4 and 9 <= y <= last - 9:
        if x in (2, 4) and (11 <= y <= 14 or last - 14 <= y <= last - 11) and y % 2 == 1:
            return (150, 156, 168, 255)
        if x == 3 and y == last // 2:
            return (34, 150, 140, 255)
        return (26, 28, 34, 255)
    if (x + y // 2) % 4 == 0:
        return (88, 96, 110, 255)
    return (50, 56, 66, 255)


def mood_board(x, y):
    """노란 판에 굵은 보라 줄 두 개, 가운데 별 하나."""
    if abs(x - 3) + abs(y - 18) <= 3:
        return (255, 255, 255, 255)
    return (122, 82, 200, 255) if x in (1, 5) else (252, 214, 84, 255)


def grinder_board(x, y):
    """번호 조끼 같은 흰 판에 초록 줄과 빨간 번호 칸."""
    if 16 <= y <= 20:
        return (224, 62, 72, 255)
    return (62, 156, 114, 255) if 2 <= x <= 4 else (246, 248, 252, 255)


def hot_pack_board(x, y):
    """크림색 판을 따라 주황 줄이 길게 지나고, 가운데 빨간 선이 있다."""
    if x == 3:
        return (226, 74, 58, 255)
    return (250, 150, 56, 255) if x in (2, 4) else (255, 242, 218, 255)


def gear_board(x, y):
    """조끼 색 판에 고글 주황 선과 눈금."""
    if x == 1:
        return (252, 160, 70, 255)
    if x == 5 and y % 4 == 0:
        return (226, 244, 252, 255)
    return (70, 84, 68, 255) if 13 <= y <= 23 else (96, 110, 92, 255)


def outsider_board(x, y):
    """후드와 같은 무채색. 밝은 테두리와 가는 줄 하나만 둔다."""
    if x in (0, 6):
        return (120, 126, 146, 255)
    return (178, 184, 200, 255) if x == 3 and 9 <= y <= 27 else (46, 50, 66, 255)


BOARD_PATTERNS = {
    "CAPTAIN": captain_board,
    "RESORT_INSIDER": insider_board,
    "HYPE": hype_board,
    "MOOD_MAKER": mood_board,
    "GRINDER": grinder_board,
    "HOT_PACK": hot_pack_board,
    "GEAR_GEEK": gear_board,
    "RESORT_OUTSIDER": outsider_board,
}


def board_gear(pattern):
    """길고 납작한 스노보드를 일자로 세운다. 양 끝이 둥글고, 그래픽은 직업 의상에 맞춘다."""
    c = Canvas(GEAR_WIDTH, GRID)
    left, top = GEAR_WIDTH - 2 - BOARD_WIDTH, GRID - 2 - BOARD_LENGTH
    for y in range(BOARD_LENGTH):
        end = min(y, BOARD_LENGTH - 1 - y)
        inset = (2, 1)[end] if end < 2 else 0
        for x in range(inset, BOARD_WIDTH - inset):
            c.dot(left + x, top + y, pattern(x, y))
    c.outline()
    return c


JOBS = {
    "CAPTAIN": captain,
    "RESORT_INSIDER": resort_insider,
    "HYPE": hype,
    "MOOD_MAKER": mood_maker,
    "GRINDER": grinder,
    "HOT_PACK": hot_pack,
    "GEAR_GEEK": gear_geek,
    "RESORT_OUTSIDER": resort_outsider,
}

GEARS = {
    "gear-SKI": ski_gear,
    **{f"gear-BOARD-{job}": (lambda pattern=pattern: board_gear(pattern)) for job, pattern in BOARD_PATTERNS.items()},
}


OG_WIDTH, OG_HEIGHT = 1200, 630
SKY = (224, 240, 250, 255)
INK = (40, 56, 74, 255)
MUTED = (92, 122, 146, 255)
ACCENT = (226, 96, 63, 255)
PINE = (127, 181, 172, 255)


# 링크 미리보기 글자에 쓸 한글 글꼴. CHARACTER_TEST_FONT와 CHARACTER_TEST_FONT_BOLD로 직접 지정할 수 있다.
FONT_CANDIDATES = {
    True: (
        os.environ.get("CHARACTER_TEST_FONT_BOLD", ""),
        "C:/Windows/Fonts/malgunbd.ttf",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
    ),
    False: (
        os.environ.get("CHARACTER_TEST_FONT", ""),
        "C:/Windows/Fonts/malgun.ttf",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    ),
}


def font_path(bold):
    for path in FONT_CANDIDATES[bold]:
        if path and Path(path).exists():
            return path
    raise SystemExit("한글 글꼴을 찾지 못했습니다. CHARACTER_TEST_FONT와 CHARACTER_TEST_FONT_BOLD에 경로를 지정해 주세요.")


def og_font(size, bold=True):
    return ImageFont.truetype(font_path(bold), size)


def character_art(name):
    canvas = JOBS[name]()
    return canvas.image


def og_base(title_lines, sub):
    """링크 미리보기 공통 바탕. 카톡이 양옆을 잘라도 읽히도록 글자를 왼쪽 안쪽에 둔다."""
    image = Image.new("RGBA", (OG_WIDTH, OG_HEIGHT), SKY)
    draw = ImageDraw.Draw(image)
    for x, y, s in ((40, 70, 26), (110, 40, 18), (30, 560, 30), (120, 590, 20), (1150, 60, 26), (1090, 30, 16), (1160, 570, 30), (1090, 600, 18), (620, 30, 18), (640, 600, 22)):
        draw.polygon([(x, y - s), (x + s * 0.7, y + s * 0.6), (x - s * 0.7, y + s * 0.6)], fill=PINE)
        draw.polygon([(x, y - s), (x + s * 0.28, y - s * 0.45), (x - s * 0.28, y - s * 0.45)], fill=WHITE)
    y = 170
    for line in title_lines:
        draw.text((70, y), line, font=og_font(54), fill=INK)
        y += 74
    draw.text((72, y + 12), sub, font=og_font(26, bold=False), fill=MUTED)
    draw.text((72, 520), "눈팅 snowthing.org", font=og_font(28), fill=INK)
    return image, draw


def paste_character(image, name, x, y, size):
    art = character_art(name).resize((size, size), Image.NEAREST)
    image.alpha_composite(art, (x, y))


def draw_share_images():
    """테스트 소개와 모임 초대 링크 미리보기 그림."""
    test, _ = og_base(["나는 어떤", "스키장 캐릭터일까?"], "15개만 고르면 캐릭터 카드가 나와요")
    for name, x, y, size in (("CAPTAIN", 660, 50, 250), ("MOOD_MAKER", 910, 40, 250), ("GEAR_GEEK", 670, 320, 250), ("HOT_PACK", 910, 320, 250)):
        paste_character(test, name, x, y, size)
    test.convert("RGB").save(OUT_DIR / "og-test.png", optimize=True)

    group, draw = og_base(["나랑 제일 잘 맞는", "사람은 누구?"], "링크 눌러서 모임 지도 보기")
    cx, cy = 900, 315
    for radius, alpha in ((270, 110), (200, 170), (130, 255)):
        layer = Image.new("RGBA", group.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(255, 255, 255, alpha), outline=(169, 203, 224, 255), width=3)
        group.alpha_composite(layer)
    draw = ImageDraw.Draw(group)
    for name, angle, radius in (("HOT_PACK", -2.4, 170), ("RESORT_INSIDER", -0.75, 170), ("GRINDER", 0.45, 170), ("HYPE", 2.55, 170), ("RESORT_OUTSIDER", -1.57, 215), ("GEAR_GEEK", 1.57, 210)):
        size = 112
        paste_character(group, name, round(cx + radius * math.cos(angle) - size / 2), round(cy + radius * math.sin(angle) - size / 2), size)
    draw.ellipse((cx - 78, cy - 78, cx + 78, cy + 78), fill=WHITE, outline=ACCENT, width=7)
    paste_character(group, "CAPTAIN", cx - 52, cy - 76, 104)
    draw.text((cx, cy + 50), "나", font=og_font(28), fill=ACCENT, anchor="mm")
    group.convert("RGB").save(OUT_DIR / "og-group.png", optimize=True)


def main():
    # 그림 일부만 새로 쓰고 멈추지 않도록 글꼴부터 확인한다.
    font_path(True)
    font_path(False)
    for name, draw in {**JOBS, **GEARS}.items():
        draw().save(name)
    # 확인용 모음 그림은 저장소 밖 경로를 인자로 줄 때만 만든다.
    if len(sys.argv) > 1:
        cell = GRID * 6
        sheet = Image.new("RGBA", (4 * cell, 2 * cell), (214, 226, 238, 255))
        for index, name in enumerate(JOBS):
            sprite = Image.open(OUT_DIR / f"{name}.png").resize((cell, cell), Image.NEAREST)
            sheet.alpha_composite(sprite, ((index % 4) * cell, (index // 4) * cell))
        sheet.save(sys.argv[1])
    draw_share_images()
    print(f"{len(JOBS) + len(GEARS) + 2}장 저장: {OUT_DIR}")


if __name__ == "__main__":
    main()
