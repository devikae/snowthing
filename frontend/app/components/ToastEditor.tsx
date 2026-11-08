"use client";

import { useEffect, useRef, useState, forwardRef, useImperativeHandle } from "react";
// @ts-expect-error -- Toast UI Editor 3.2.2의 타입 선언이 package.json exports를 통해 노출되지 않습니다.
import Editor from "@toast-ui/editor";
import { API_ENDPOINTS } from "../lib/api";
import { csrfFetch } from "../lib/csrfFetch";
import { sanitizeHtml } from "../lib/sanitizeHtml";
import { readErrorMessage } from "../lib/messageApi";
import { useImageUploadLease } from "../lib/useImageUploadLease";
import { prepareImageForUpload } from "../lib/imageFile";

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
    const [uploadedUrls, setUploadedUrls] = useState<string[]>([]);
    const [imageStatus, setImageStatus] = useState<{ uploading: number; error: string }>({ uploading: 0, error: "" });
    useImageUploadLease(uploadedUrls);

    useEffect(() => {
      if (!containerRef.current) return;

      const editor = new Editor({
        el: containerRef.current,
        initialValue: initialValue,
        previewStyle: "vertical",
        height: height,
        initialEditType: "wysiwyg",
        autofocus: false,
        useCommandShortcut: true,
        customHTMLSanitizer: sanitizeHtml,
        toolbarItems: [
          ["heading", "bold", "italic", "strike"],
          ["hr", "quote"],
          ["ul", "ol", "task"],
          ["table", "image", "link"],
          ["code", "codeblock"],
        ],
        hooks: {
          // 이 훅이 없으면 에디터가 사진을 base64 글자로 바꿔 본문에 넣어 DB에 쌓인다.
          // 복붙, 드래그, 이미지 버튼의 사진만 여기로 오고 글자 복붙은 영향이 없다.
          addImageBlobHook: async (blob: Blob, insertImage: (url: string, altText?: string) => void) => {
            setImageStatus((current) => ({ uploading: current.uploading + 1, error: "" }));
            let error = "";
            try {
              const formData = new FormData();
              formData.append("file", await prepareImageForUpload(blob));
              const response = await csrfFetch(API_ENDPOINTS.images.upload, { method: "POST", body: formData });
              if (response.status === 401) {
                error = "사진은 로그인 후 올릴 수 있습니다. 글은 그대로 작성할 수 있습니다.";
              } else if (!response.ok) {
                error = await readErrorMessage(response, "사진을 올리지 못했습니다.");
              } else {
                const uploaded: { imageUrl: string } = await response.json();
                setUploadedUrls((current) => [...current, uploaded.imageUrl]);
                insertImage(uploaded.imageUrl);
              }
            } catch {
              error = "사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
            setImageStatus((current) => ({ uploading: current.uploading - 1, error }));
          },
        },
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

    return (
      <>
        <div ref={containerRef} className="toast-editor-wrapper text-[var(--snow-ink)]" />
        {imageStatus.uploading > 0 && <p className="toast-editor-image-status" role="status">사진을 올리는 중입니다.</p>}
        {imageStatus.uploading === 0 && imageStatus.error && <p className="toast-editor-image-status error" role="alert">{imageStatus.error}</p>}
      </>
    );
  }
);

ToastEditor.displayName = "ToastEditor";
export default ToastEditor;
