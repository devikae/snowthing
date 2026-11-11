import { MAP_SNOW, RING_WHITE, faceImage, type CharacterLabel, type JobCode } from "./data";
import { HEAD_CROP, HEAD_WIDTH, ME_HEAD_WIDTH, ME_RADIUS, layoutMap, treePaths, type MapInputLevel } from "./mapLayout";
import { canvasToBlob, loadImage, pageFontFamily } from "./shareFile";

const IMAGE_WIDTH = 1080;
const HEADER = 62;
const LEGEND = 34;
const FOOTER = 76;

interface MapImageInput {
  title: string;
  levels: MapInputLevel[];
  me: { character: CharacterLabel };
  hideNames: boolean;
}

/** 화면과 같은 배치와 설원 색으로 지도를 그린다. 테마와 상관없이 저장 이미지는 항상 같은 색이다. */
export async function renderMapImage({ title, levels, me, hideNames }: MapImageInput): Promise<Blob> {
  const layout = layoutMap(levels, hideNames);
  const { size, center } = layout;
  const totalHeight = HEADER + size + LEGEND + FOOTER;
  const scale = IMAGE_WIDTH / size;
  const canvas = document.createElement("canvas");
  canvas.width = IMAGE_WIDTH;
  canvas.height = Math.round(totalHeight * scale);
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("이 브라우저에서는 이미지를 만들 수 없어요.");
  const family = await pageFontFamily();
  const jobs = new Set<JobCode>([me.character.job]);
  layout.people.forEach((node) => jobs.add(node.jobLabel.job));
  const sprites = new Map(await Promise.all([...jobs].map(async (job) => [job, await loadImage(faceImage(job))] as const)));

  ctx.scale(scale, scale);
  ctx.imageSmoothingEnabled = false;
  ctx.fillStyle = MAP_SNOW.sky;
  ctx.fillRect(0, 0, size, totalHeight);
  const font = (weight: number, px: number) => `${weight} ${px}px ${family}`;

  ctx.textAlign = "center";
  ctx.fillStyle = MAP_SNOW.ink;
  ctx.font = font(800, 18);
  ctx.fillText(title, center, 26);
  ctx.fillStyle = MAP_SNOW.muted;
  ctx.font = font(500, 11);
  ctx.fillText("나를 중심으로, 가까울수록 잘 맞는 사람이에요", center, 46);

  const drawHead = (job: JobCode, x: number, y: number, width: number) => {
    const sprite = sprites.get(job)!;
    const height = (width * HEAD_CROP.height) / HEAD_CROP.width;
    ctx.drawImage(
      sprite,
      sprite.naturalWidth * HEAD_CROP.x,
      sprite.naturalHeight * HEAD_CROP.y,
      sprite.naturalWidth * HEAD_CROP.width,
      sprite.naturalHeight * HEAD_CROP.height,
      x - width / 2,
      y - height / 2,
      width,
      height,
    );
  };
  const haloText = (text: string, x: number, y: number) => {
    ctx.save();
    ctx.lineWidth = 3;
    ctx.lineJoin = "round";
    ctx.strokeStyle = "#ffffff";
    ctx.strokeText(text, x, y);
    ctx.restore();
    ctx.fillText(text, x, y);
  };

  ctx.translate(0, HEADER);
  const trees = treePaths(layout.trees);
  ctx.fillStyle = MAP_SNOW.trunk;
  ctx.fill(new Path2D(trees.trunk));
  ctx.fillStyle = MAP_SNOW.pine;
  ctx.fill(new Path2D(trees.pine));
  ctx.fillStyle = "#ffffff";
  ctx.fill(new Path2D(trees.cap));
  [...layout.bands].reverse().forEach((band) => {
    ctx.fillStyle = `rgba(255, 255, 255, ${RING_WHITE[band.code]})`;
    ctx.beginPath();
    ctx.arc(center, center, band.outer, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = MAP_SNOW.ring;
    ctx.lineWidth = 1.2;
    ctx.stroke();
  });
  ctx.fillStyle = MAP_SNOW.muted;
  ctx.font = font(700, 9);
  layout.labels.forEach((label) => ctx.fillText(label.text, label.x, label.y + 3));

  layout.empties.forEach((node) => {
    ctx.setLineDash([3, 3]);
    ctx.strokeStyle = MAP_SNOW.muted;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(node.x, node.y, 16, 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = MAP_SNOW.muted;
    ctx.font = font(500, 10);
    ctx.fillText("빈자리", node.x, node.y + 30);
  });

  layout.people.forEach((node) => {
    drawHead(node.jobLabel.job, node.x, node.y - 10, HEAD_WIDTH);
    ctx.fillStyle = MAP_SNOW.ink;
    ctx.font = font(700, 10);
    haloText(node.name, node.x, node.y + 12);
    ctx.fillStyle = "#d2461f";
    ctx.font = font(800, 12);
    haloText(String(node.score), node.x, node.y + 24);
  });

  ctx.fillStyle = "#ffffff";
  ctx.strokeStyle = MAP_SNOW.accent;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.arc(center, center, ME_RADIUS, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
  drawHead(me.character.job, center, center - 6, ME_HEAD_WIDTH);
  ctx.fillStyle = MAP_SNOW.accent;
  ctx.font = font(800, 12);
  ctx.fillText("나", center, center + 25);

  // 단계별 인원수. 다섯 칸을 같은 폭으로 나눈다.
  ctx.translate(0, size);
  const cell = size / layout.bands.length;
  ctx.fillStyle = MAP_SNOW.ink;
  ctx.font = font(700, 10);
  layout.bands.forEach((band, index) => ctx.fillText(`${band.label} ${band.count}`, cell * index + cell / 2, 20));

  ctx.translate(0, LEGEND);
  ctx.fillStyle = MAP_SNOW.muted;
  ctx.font = font(500, 10);
  ctx.fillText("윈터스포츠 커뮤니티", center, 12);
  ctx.fillStyle = MAP_SNOW.ink;
  ctx.font = font(800, 13);
  ctx.fillText("눈팅 snowthing.org", center, 30);
  ctx.fillStyle = MAP_SNOW.muted;
  ctx.font = font(400, 10);
  ctx.fillText("나는 어떤 사이일까? 스키장 캐릭터 테스트", center, 47);
  ctx.font = font(400, 9);
  ctx.fillText("재미로 보는 궁합이에요", center, 62);

  return canvasToBlob(canvas);
}
