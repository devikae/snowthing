export const DECK_QUESTION_KEY = "snowthing-deck-question";
export interface ToolQuestionDraft { title: string; content: string; savedAt: number; }

export function parseToolQuestionDraft(value: string | null, now: number): ToolQuestionDraft | null {
  if (!value || value.length > 12000) return null;
  try {
    const draft = JSON.parse(value) as Partial<ToolQuestionDraft>;
    if (typeof draft.title !== "string" || !draft.title.trim() || draft.title.length > 200
      || typeof draft.content !== "string" || !draft.content.trim() || draft.content.length > 8000
      || typeof draft.savedAt !== "number" || !Number.isFinite(draft.savedAt)
      || draft.savedAt > now + 60000 || now - draft.savedAt > 3600000) return null;
    return { title: draft.title, content: draft.content, savedAt: draft.savedAt };
  } catch { return null; }
}

export function readDeckQuestionDraft(enabled: boolean): ToolQuestionDraft | null {
  if (!enabled || typeof window === "undefined") return null;
  try { return parseToolQuestionDraft(sessionStorage.getItem(DECK_QUESTION_KEY), Date.now()); }
  catch { return null; }
}
