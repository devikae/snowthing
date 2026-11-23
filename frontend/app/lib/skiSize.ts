import { deckLengthBands, type DeckLengthBand } from "./deckSize";

export const SKI_SOURCE = "https://www.nordica.com/assets/downloads/ski-sizer_en.pdf";
export const SKI_SOURCE_CHECKED = "2026-11-24";

export interface SkiInput { height: number; weight: number; }
export type SkiLengthBand = DeckLengthBand;
export type SkiBuild = "light" | "average" | "heavy";

// Nordica adult ski sizer: [skier height cm, suggested ski length min, max].
// The last row is printed as 6'2"> and covers every taller skier.
const HEIGHT_ROWS: readonly [number, number, number][] = [
  [122, 100, 110], [127, 110, 120], [132, 120, 130], [137, 125, 135],
  [142, 130, 140], [147, 135, 145], [152, 140, 155], [158, 145, 165],
  [163, 150, 170], [168, 155, 175], [173, 160, 180], [178, 165, 185],
  [183, 170, 185], [188, 175, 193],
];

// The chart says lighter than average goes shorter and heavier goes longer, without a number.
// BMI cutoffs stand in for "average": 18.5 (underweight) and 25 (Korean obesity criterion).
const BMI_LIGHT = 18.5;
const BMI_HEAVY = 25;

export function validateSkiInput(input: SkiInput): string | null {
  if (!Number.isInteger(input.height) || input.height < 122 || input.height > 210) return "키를 122~210cm 범위에서 정수로 입력해 주세요.";
  if (!Number.isFinite(input.weight) || input.weight < 30 || input.weight > 200) return "체중을 30~200kg 범위로 입력해 주세요.";
  return null;
}

// Closest printed height; a tie between two rows uses the shorter one.
function chartRow(height: number) {
  if (height >= 188) return HEIGHT_ROWS[HEIGHT_ROWS.length - 1];
  return HEIGHT_ROWS.reduce((best, row) => Math.abs(row[0] - height) < Math.abs(best[0] - height) ? row : best);
}

export function skiBuild(height: number, weight: number) {
  // Judge on the same one-decimal value the screen shows, so "BMI 25" never reads as average.
  const bmi = Math.round((weight / (height / 100) ** 2) * 10) / 10;
  const build: SkiBuild = bmi < BMI_LIGHT ? "light" : bmi >= BMI_HEAVY ? "heavy" : "average";
  return { bmi, build };
}

export function calculateSkiSize(input: SkiInput) {
  const error = validateSkiInput(input);
  if (error) return { valid: false as const, error };
  const [chartHeight, minimum, maximum] = chartRow(input.height);
  const { bmi, build } = skiBuild(input.height, input.weight);
  const highlight: SkiLengthBand["position"] = build === "light" ? "short" : build === "heavy" ? "long" : "middle";
  return { valid: true as const, chartHeight, openEnded: chartHeight === 188, minimum, maximum, bands: deckLengthBands(minimum, maximum), bmi, build, highlight };
}

export function skiSearch(input: SkiInput) {
  return new URLSearchParams({ height: String(input.height), weight: String(input.weight) }).toString();
}

export function parseSkiSearch(search: string): SkiInput | null {
  const params = new URLSearchParams(search);
  if (params.getAll("height").length !== 1 || params.getAll("weight").length !== 1) return null;
  const height = params.get("height") ?? "";
  const weight = params.get("weight") ?? "";
  if (!/^\d{3}$/.test(height) || !/^\d{2,3}(\.\d)?$/.test(weight)) return null;
  const input = { height: Number(height), weight: Number(weight) };
  return validateSkiInput(input) ? null : input;
}
