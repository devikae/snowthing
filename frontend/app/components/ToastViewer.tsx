"use client";

import { useEffect, useRef } from "react";
// @ts-ignore
import Viewer from "@toast-ui/editor/dist/toastui-editor-viewer";

interface ToastViewerProps {
  content: string;
}

export function ToastViewer({ content }: ToastViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const viewer = new Viewer({
      el: containerRef.current,
      initialValue: content || "",
    });

    return () => {
      viewer.destroy();
    };
  }, [content]);

  return <div ref={containerRef} className="toast-viewer-wrapper text-base leading-8 text-[var(--snow-ink)]" />;
}

export default ToastViewer;
