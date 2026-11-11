export interface SeasonPassInput {
  passPrice: number;
  ticketPrice: number;
  passTripCost: number;
  ticketTripCost: number;
  visited: number;
  planned: number;
}

export function validateSeasonPass(input: SeasonPassInput): string | null {
  for (const amount of [input.passPrice, input.ticketPrice, input.passTripCost, input.ticketTripCost]) {
    if (!Number.isSafeInteger(amount) || amount < 0 || amount > 100_000_000) return "비용은 0~1억원 범위의 정수로 입력해 주세요.";
  }
  for (const visits of [input.visited, input.planned]) {
    if (!Number.isSafeInteger(visits) || visits < 0 || visits > 730) return "방문 횟수는 0~730회로 입력해 주세요.";
  }
  if (input.visited > input.planned) return "예상 총 방문 횟수는 방문한 횟수 이상이어야 해요.";
  return null;
}

export function calculateSeasonPass(input: SeasonPassInput) {
  const error = validateSeasonPass(input);
  if (error) return { valid: false as const, error };
  const savingPerVisit = input.ticketPrice + input.ticketTripCost - input.passTripCost;
  const breakEvenVisits = input.passPrice === 0 ? 0 : savingPerVisit > 0 ? Math.ceil(input.passPrice / savingPerVisit) : null;
  const costsAt = (visits: number) => {
    const passTotal = input.passPrice + visits * input.passTripCost;
    const ticketsTotal = visits * (input.ticketPrice + input.ticketTripCost);
    return { visits, passTotal, ticketsTotal, saving: ticketsTotal - passTotal, costPerVisit: visits ? passTotal / visits : null };
  };
  const milestones = new Set([0, input.visited, input.planned, 5, 10, 15, 20, 30]);
  if (breakEvenVisits !== null && breakEvenVisits <= 730) milestones.add(breakEvenVisits);
  return {
    valid: true as const,
    savingPerVisit,
    breakEvenVisits,
    remainingVisits: breakEvenVisits === null ? null : Math.max(0, breakEvenVisits - input.visited),
    current: costsAt(input.visited),
    planned: costsAt(input.planned),
    rows: [...milestones].sort((a, b) => a - b).map(costsAt),
    staysBeneficial: savingPerVisit >= 0,
  };
}

export const SEASON_PASS_FIELDS = ["passPrice", "ticketPrice", "passTripCost", "ticketTripCost", "visited", "planned"] as const;

export function seasonPassSearch(input: SeasonPassInput): string {
  return new URLSearchParams(SEASON_PASS_FIELDS.map(key => [key, String(input[key])])).toString();
}

export function parseSeasonPassSearch(search: string): SeasonPassInput | null {
  const params = new URLSearchParams(search);
  const entries = SEASON_PASS_FIELDS.map(key => {
    const raw = params.get(key);
    return [key, params.getAll(key).length === 1 && raw !== null && /^\d{1,9}$/.test(raw) ? Number(raw) : NaN];
  });
  const input = Object.fromEntries(entries) as unknown as SeasonPassInput;
  return validateSeasonPass(input) ? null : input;
}
