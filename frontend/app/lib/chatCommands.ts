export type LocalChatCommand = "help" | "season-pass" | "deck-size";

/** Exact local commands never enter the public stream or an AI request. */
export function localChatCommand(content: string): LocalChatCommand | null {
  switch (content.trim()) {
    case "/도움말": case "/help": return "help";
    case "/시즌권": return "season-pass";
    case "/데크": return "deck-size";
    default: return null;
  }
}
