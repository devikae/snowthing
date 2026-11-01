export const DECK_SOURCE = "https://ca.jonessnowboards.com/pages/a-guide-on-how-to-find-your-perfect-snowboard-size";
export const DECK_SOURCE_CHECKED = "2026-11-01";
export const BOOT_SIZE_SOURCE = "https://www.burton.com/en-us/products/mens-burton-swath-boa-snowboard-boots-2031816-o";
export interface DeckInput { weight: number; bootMm: number; }
export interface DeckLengthBand { position: "short" | "middle" | "long"; minimum: number; maximum: number; }

// A display guide within the returned reference range, not manufacturer cutoffs.
// Keep the middle half for balanced use and the outer quarters for either bias.
export function deckLengthBands(minimum: number, maximum: number | null): DeckLengthBand[] {
  if (!Number.isInteger(minimum) || maximum === null || !Number.isInteger(maximum) || maximum - minimum < 2) return [];
  const edgeCount = Math.max(1, Math.floor((maximum - minimum + 1) / 4));
  return [
    { position: "short", minimum, maximum: minimum + edgeCount - 1 },
    { position: "middle", minimum: minimum + edgeCount, maximum: maximum - edgeCount },
    { position: "long", minimum: maximum - edgeCount + 1, maximum },
  ];
}

// Factual adult reference rows from the linked manufacturer's chart, checked above.
// These are not model-specific recommendations and are not a universal sizing formula.
const WEIGHT_ROWS: readonly [number, number, number, number | null][] = [
  [34,45,137,143], [43,50,140,145], [47,54,142,147], [52,58,144,149],
  [56,63,146,151], [61,68,148,153], [65,72,150,155], [70,77,152,157],
  [74,81,154,159], [79,86,156,161], [83,91,158,163], [88,95,160,165],
  [92,99,162,167], [97,104,164,169], [105,Infinity,165,null],
];
// Convert the published full/half-size reference using Burton's Mondo correspondence.
// Half sizes absent from that product's chart use the next published size, without
// inventing a brand-specific conversion or an exact boot-shell measurement.
const WIDTH_ROWS: readonly [number, number][] = [[280,259],[285,261],[290,263],[295,265],[300,267],[310,271],[320,275],[330,278]];

export function validateDeckInput(input: DeckInput): string | null {
  if (!Number.isFinite(input.weight) || input.weight < 34 || input.weight > 200) return "성인 참고표에 맞춰 체중을 34~200kg 범위로 입력해 주세요.";
  if (!Number.isInteger(input.bootMm) || input.bootMm < 210 || input.bootMm > 340 || input.bootMm % 5 !== 0) return "부츠 사이즈를 210~340mm 범위에서 5mm 단위로 입력해 주세요. 예: 270, 275";
  return null;
}

export function calculateDeckSize(input: DeckInput) {
  const error = validateDeckInput(input);
  if (error) return { valid: false as const, error };
  // The reference chart is in whole kilograms; show this rounded lookup value in UI.
  const lookupWeight = Math.round(input.weight);
  const matches = WEIGHT_ROWS.filter(row => lookupWeight >= row[0] && lookupWeight <= row[1]);
  const minimum = Math.min(...matches.map(row => row[2]));
  const maximum = matches.some(row => row[3] === null) ? null : Math.max(...matches.map(row => row[3] as number));
  const width = input.bootMm < 280 ? null : WIDTH_ROWS.find(row => row[0] >= input.bootMm)?.[1] ?? 278;
  return { valid: true as const, lookupWeight, minimum, maximum, bands: deckLengthBands(minimum, maximum), minimumWaistMm: width, widthIsOpenEnded: input.bootMm >= 330, rowsMatched: matches.length };
}

export function checkModelWeight(weight: number, minimum: number, maximum: number) {
  if (![weight, minimum, maximum].every(Number.isFinite) || weight <= 0 || minimum <= 0 || maximum < minimum || maximum > 300) return { valid: false as const };
  return { valid: true as const, withinRange: weight >= minimum && weight <= maximum, position: weight < minimum ? "below" : weight > maximum ? "above" : "inside" };
}

export function deckSearch(input: DeckInput) {
  return new URLSearchParams({ weight: String(input.weight), bootMm: String(input.bootMm) }).toString();
}

export function parseDeckSearch(search: string): DeckInput | null {
  const params = new URLSearchParams(search);
  if (params.getAll("weight").length !== 1) return null;
  const hasMm = params.has("bootMm");
  const bootKey = hasMm ? "bootMm" : "bootUS";
  if (params.getAll(bootKey).length !== 1 || (hasMm && params.has("bootUS"))) return null;
  const weight = params.get("weight") ?? "";
  const boot = params.get(bootKey) ?? "";
  if (!/^\d{1,3}(\.\d)?$/.test(weight) || !(hasMm ? /^\d{3}$/ : /^\d{1,2}(\.[05])?$/).test(boot)) return null;
  const bootMm = hasMm ? Number(boot) : Number(boot) * 10 + 180;
  const input = { weight: Number(weight), bootMm };
  return validateDeckInput(input) ? null : input;
}

export function deckQuestionDraft(input: DeckInput, range: string) {
  return `데크 사이즈를 고민하고 있습니다.\n\n체중: ${input.weight}kg\n부츠: ${input.bootMm}mm\n계산기 참고 범위: ${range}\n\n주로 타려는 용도:\n고민 중인 모델과 연식:\n현재 사용하는 데크와 불편한 점:\n\n이 조건에서 어떤 사이즈와 폭을 먼저 확인하면 좋을까요?`;
}
