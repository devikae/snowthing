"use client";

import { createContext, useContext } from "react";
import type { ResortCameraGroup } from "./types";

const ResortCamContext = createContext<ResortCameraGroup[] | null>(null);

export function ResortCamProvider({
  resorts,
  children,
}: {
  resorts: ResortCameraGroup[];
  children: React.ReactNode;
}) {
  return <ResortCamContext.Provider value={resorts}>{children}</ResortCamContext.Provider>;
}

export function useResortCameras() {
  const resorts = useContext(ResortCamContext);
  if (resorts === null) throw new Error("ResortCamProvider is required.");
  return resorts;
}
