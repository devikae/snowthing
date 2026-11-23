"use client";

import { createContext, useContext, type ReactNode } from "react";
import { DEFAULT_BOARDS, type Board } from "../lib/boards";

// 루트 레이아웃이 서버에서 받은 게시판 목록을 화면 어디서나 쓰게 한다 (IKA-114).
const BoardsContext = createContext<Board[]>(DEFAULT_BOARDS);

export function BoardsProvider({ boards, children }: { boards: Board[]; children: ReactNode }) {
  return <BoardsContext.Provider value={boards}>{children}</BoardsContext.Provider>;
}

export function useBoards(): Board[] {
  return useContext(BoardsContext);
}
