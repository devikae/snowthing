"use client";

import { useEffect, useRef, forwardRef, useImperativeHandle } from "react";
// @ts-expect-error -- Toast UI Editor 3.2.2의 타입 선언이 package.json exports를 통해 노출되지 않습니다.
import Editor from "@toast-ui/editor";

export interface ToastEditorHandle {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getInstance: () => any;
}

interface ToastEditorProps {
  initialValue?: string;
  height?: string;
  onChange?: () => void;
}

export const ToastEditor = forwardRef<ToastEditorHandle, ToastEditorProps>(
  ({ initialValue = "", height = "500px", onChange }, ref) => {
    const containerRef = useRef<HTMLDivElement>(null);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const editorRef = useRef<any>(null);

    useEffect(() => {
      if (!containerRef.current) return;

      const editor = new Editor({
        el: containerRef.current,
        initialValue: initialValue,
        previewStyle: "vertical",
        height: height,
        initialEditType: "wysiwyg",
        useCommandShortcut: true,
        toolbarItems: [
          ["heading", "bold", "italic", "strike"],
          ["hr", "quote"],
          ["ul", "ol", "task"],
          ["table", "image", "link"],
          ["code", "codeblock"],
        ],
      });

      if (onChange) {
        editor.on("change", onChange);
      }

      editorRef.current = editor;

      return () => {
        editor.destroy();
      };
    }, [height, initialValue, onChange]);

    useImperativeHandle(ref, () => ({
      getInstance: () => editorRef.current!,
    }));

    return <div ref={containerRef} className="toast-editor-wrapper text-black" />;
  }
);

ToastEditor.displayName = "ToastEditor";
export default ToastEditor;
