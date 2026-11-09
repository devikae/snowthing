"use client";

import { useEffect, useRef } from "react";
// @ts-expect-error -- Toast UI Editor 3.2.2의 타입 선언이 package.json exports를 통해 노출되지 않습니다.
import Viewer from "@toast-ui/editor/dist/toastui-editor-viewer";
import { sanitizeHtml } from "../lib/sanitizeHtml";

interface ToastViewerProps {
  content: string;
  /** 뷰어가 본문을 그린 뒤 부른다. 서버가 넣어 둔 평문 본문을 이때 치운다. */
  onRendered?: () => void;
}

export function ToastViewer({ content, onRendered }: ToastViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const viewer = new Viewer({
      el: containerRef.current,
      initialValue: content || "",
      customHTMLSanitizer: sanitizeHtml,
    });
    onRendered?.();

    return () => {
      viewer.destroy();
    };
  }, [content, onRendered]);

  return <div ref={containerRef} className="toast-viewer-wrapper text-base leading-8 text-[var(--snow-ink)]" />;
}

export default ToastViewer;
