import { STAT_MAX, STATS, characterImage, characterName, descriptionSentences, gearBox, gearImage, type JobCode, type Sport } from "./data";
import type { CharacterResult } from "./api";
import { canvasToBlob, loadImage, pageFontFamily } from "./shareFile";

const WIDTH = 1080;
const HEIGHT = 1920;
const CONTENT_WIDTH = 960;
// 아래쪽 글자에 눈송이가 걸치지 않도록 이 높이까지만 뿌린다.
const SNOW_BOTTOM = 1630;
const SPRITE_SIZE = 400;
const DESCRIPTION_MAX_SIZE = 34;
const DESCRIPTION_WIDTH = 900;
const STAT_COLORS = ["#ec4899", "#f97316", "#10b981", "#0284c7"];
// 타입마다 배경색이 다르다. 캐릭터 옷과 겹치지 않는 색을 골랐고, 맨 아래 글자가 읽히도록 끝 색을 너무 밝게 두지 않는다.
const THEMES: Record<JobCode, { top: string; middle: string; bottom: string; accent: string; box: string }> = {
  CAPTAIN: { top: "#101c4f", middle: "#2747b8", bottom: "#6f92ea", accent: "#bcd0ff", box: "rgba(16,28,79,.76)" },
  RESORT_INSIDER: { top: "#07343f", middle: "#0e8a9a", bottom: "#4cbcc4", accent: "#a5eef0", box: "rgba(7,52,63,.76)" },
  HYPE: { top: "#2a1150", middle: "#6d28c9", bottom: "#a57cf0", accent: "#dcc8ff", box: "rgba(42,17,80,.76)" },
  MOOD_MAKER: { top: "#4a1430", middle: "#c93a6b", bottom: "#ee7f9b", accent: "#ffc9d6", box: "rgba(74,20,48,.76)" },
  GRINDER: { top: "#3f2a05", middle: "#b7791f", bottom: "#dca63c", accent: "#ffe3a3", box: "rgba(63,42,5,.78)" },
  HOT_PACK: { top: "#4a0f18", middle: "#b3262f", bottom: "#e0645c", accent: "#ffc4bb", box: "rgba(74,15,24,.76)" },
  GEAR_GEEK: { top: "#0a3a2e", middle: "#128a66", bottom: "#46b98f", accent: "#aef0d4", box: "rgba(10,58,46,.76)" },
  RESORT_OUTSIDER: { top: "#131a2e", middle: "#34406b", bottom: "#7482b3", accent: "#c5cff0", box: "rgba(19,26,46,.8)" },
};

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const lines: string[] = [];
  let line = "";
  for (const word of text.split(" ")) {
    const next = line ? `${line} ${word}` : word;
    if (line && ctx.measureText(next).width > maxWidth) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  return lines;
}

/** 위치가 매번 달라지지 않도록 직업 코드로 고정한 눈송이. */
function drawSnow(ctx: CanvasRenderingContext2D, seedText: string) {
  let seed = [...seedText].reduce((sum, char) => (sum * 31 + char.charCodeAt(0)) >>> 0, 7);
  const next = (limit: number) => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed % limit;
  };
  ctx.fillStyle = "rgba(255,255,255,.45)";
  for (let index = 0; index < 60; index += 1) {
    const size = 8 + next(3) * 6;
    ctx.fillRect(next(WIDTH), next(SNOW_BOTTOM), size, size);
  }
}

/** 인스타 스토리 비율 카드를 브라우저에서 그린다. 위아래 250px은 스토리 화면 요소에 가려질 수 있어 핵심 내용을 안쪽에 둔다. */
export async function renderCard(result: CharacterResult, sport: Sport): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = WIDTH;
  canvas.height = HEIGHT;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("이 브라우저에서는 이미지를 만들 수 없습니다.");
  const theme = THEMES[result.character.job];
  const [family, sprite, gear, bestSprite] = await Promise.all([
    pageFontFamily(),
    loadImage(characterImage(result.character.job)),
    loadImage(gearImage(sport, result.character.job)),
    result.bestMatch ? loadImage(characterImage(result.bestMatch.job)) : Promise.resolve(null),
  ]);
  const font = (weight: number, size: number) => `${weight} ${size}px ${family}`;

  const sky = ctx.createLinearGradient(0, 0, 0, HEIGHT);
  sky.addColorStop(0, theme.top);
  sky.addColorStop(0.55, theme.middle);
  sky.addColorStop(1, theme.bottom);
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);
  drawSnow(ctx, result.character.job);

  ctx.textAlign = "center";
  ctx.fillStyle = "rgba(255,255,255,.82)";
  ctx.font = font(600, 40);
  ctx.fillText("스키장 캐릭터 테스트", WIDTH / 2, 190);

  const name = characterName(result.character);
  let nameSize = 88;
  ctx.font = font(800, nameSize);
  let lines = wrap(ctx, name, CONTENT_WIDTH);
  if (lines.length > 2) {
    nameSize = 72;
    ctx.font = font(800, nameSize);
    lines = wrap(ctx, name, CONTENT_WIDTH);
  }
  ctx.fillStyle = "#ffffff";
  lines.forEach((line, index) => ctx.fillText(line, WIDTH / 2, 300 + index * (nameSize + 14)));

  const spriteTop = lines.length > 1 ? 430 : 370;
  ctx.fillStyle = "rgba(255,255,255,.16)";
  ctx.beginPath();
  ctx.ellipse(WIDTH / 2, spriteTop + SPRITE_SIZE - 40, 230, 44, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.imageSmoothingEnabled = false;
  // 왼쪽에 장비가 붙으므로 둘을 합쳐 가운데에 오도록 조금 오른쪽으로 민다.
  const spriteX = (WIDTH - SPRITE_SIZE) / 2 + SPRITE_SIZE * 0.05;
  const box = gearBox(spriteX, spriteTop, SPRITE_SIZE);
  ctx.drawImage(gear, box.x, box.y, box.width, box.height);
  ctx.drawImage(sprite, spriteX, spriteTop, SPRITE_SIZE, SPRITE_SIZE);

  const panelX = (WIDTH - CONTENT_WIDTH) / 2;
  const cellGap = 16;
  const cellPadding = 24;
  const cellWidth = (CONTENT_WIDTH - cellPadding * 2 - cellGap) / 2;
  const cellHeight = 92;
  const statsY = spriteTop + SPRITE_SIZE + 20;
  const statsHeight = cellPadding * 2 + cellHeight * 2 + cellGap;
  ctx.fillStyle = "#f8fafc";
  roundRect(ctx, panelX, statsY, CONTENT_WIDTH, statsHeight, 32);
  ctx.fill();

  const pipSize = 38;
  const pipGap = 8;
  STATS.forEach((stat, index) => {
    const cellX = panelX + cellPadding + (index % 2) * (cellWidth + cellGap);
    const cellY = statsY + cellPadding + Math.floor(index / 2) * (cellHeight + cellGap);
    ctx.fillStyle = "#eaf1f7";
    roundRect(ctx, cellX, cellY, cellWidth, cellHeight, 20);
    ctx.fill();
    ctx.textAlign = "left";
    ctx.fillStyle = "#1e293b";
    ctx.font = font(800, 36);
    ctx.fillText(stat.label, cellX + 24, cellY + 59);
    for (let pip = 0; pip < STAT_MAX; pip += 1) {
      ctx.fillStyle = pip < result.stats[stat.key] ? STAT_COLORS[index] : "#d3dee8";
      ctx.fillRect(cellX + cellWidth - 22 - (STAT_MAX - pip) * (pipSize + pipGap) + pipGap, cellY + (cellHeight - pipSize) / 2, pipSize, pipSize);
    }
  });

  // 문장을 줄이지 않고 한 줄에 넣는다. 긴 문장이 있는 카드만 글자를 줄인다.
  const description = descriptionSentences(result.character);
  ctx.font = font(500, DESCRIPTION_MAX_SIZE);
  const widest = Math.max(...description.map((line) => ctx.measureText(line).width));
  const descriptionSize = Math.min(DESCRIPTION_MAX_SIZE, Math.floor((DESCRIPTION_MAX_SIZE * DESCRIPTION_WIDTH) / widest));
  const descriptionLine = descriptionSize + 20;
  const descriptionY = statsY + statsHeight + 20;
  const descriptionHeight = 108 + description.length * descriptionLine;
  ctx.fillStyle = theme.box;
  roundRect(ctx, panelX, descriptionY, CONTENT_WIDTH, descriptionHeight, 32);
  ctx.fill();
  ctx.textAlign = "center";
  ctx.fillStyle = theme.accent;
  ctx.font = font(800, 30);
  ctx.fillText("이런 타입이에요", WIDTH / 2, descriptionY + 56);
  ctx.fillStyle = "#ffffff";
  ctx.font = font(500, descriptionSize);
  description.forEach((line, index) => ctx.fillText(line, WIDTH / 2, descriptionY + 112 + index * descriptionLine));

  if (result.bestMatch && bestSprite) {
    const bestY = descriptionY + descriptionHeight + 20;
    const bestHeight = 148;
    ctx.fillStyle = "#f8fafc";
    roundRect(ctx, panelX, bestY, CONTENT_WIDTH, bestHeight, 32);
    ctx.fill();
    ctx.fillStyle = "#eaf1f7";
    roundRect(ctx, panelX + 24, bestY + 20, 108, 108, 20);
    ctx.fill();
    ctx.drawImage(bestSprite, panelX + 28, bestY + 24, 100, 100);
    ctx.textAlign = "left";
    ctx.fillStyle = "#64748b";
    ctx.font = font(700, 30);
    ctx.fillText("나와 찰떡인 타입", panelX + 160, bestY + 62);
    ctx.fillStyle = "#1e293b";
    ctx.font = font(800, 46);
    ctx.fillText(characterName(result.bestMatch), panelX + 160, bestY + 118);
  }

  ctx.textAlign = "center";
  ctx.shadowColor = "rgba(0,0,0,.35)";
  ctx.shadowBlur = 10;
  ctx.fillStyle = "#ffffff";
  ctx.font = font(600, 32);
  ctx.fillText("윈터스포츠 커뮤니티", WIDTH / 2, HEIGHT - 226);
  ctx.font = font(800, 44);
  ctx.fillText("눈팅 snowthing.org", WIDTH / 2, HEIGHT - 170);
  ctx.fillStyle = "#ffffff";
  ctx.font = font(500, 30);
  ctx.fillText("재미로 보는 결과예요", WIDTH / 2, HEIGHT - 118);

  return canvasToBlob(canvas);
}
