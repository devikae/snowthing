"use client";

import { useEffect } from "react";

const MESSAGE = "작성 중인 내용이 있습니다. 페이지를 나가면 내용이 사라집니다. 나갈까요?";

// 작성 화면에서 무언가 입력한 뒤 새로고침, 탭 닫기, 사이트 안 링크 이동을 하면 확인을 받는다.
// 입력 여부는 화면 안의 input 이벤트로 판단한다(Toast UI 에디터 포함).
// 저장 후 router.push로 옮기는 것은 링크 클릭도 문서 종료도 아니라서 막히지 않는다.
export function useLeaveWarning() {
  useEffect(() => {
    let dirty = false;
    const markDirty = () => { dirty = true; };
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = "";
    };
    const beforeLink = (event: MouseEvent) => {
      if (!dirty || event.defaultPrevented) return;
      const link = (event.target as HTMLElement | null)?.closest?.("a[href]");
      // 새 탭으로 여는 링크와 에디터 본문 안의 링크는 이 화면을 떠나지 않는다.
      if (!link || link.getAttribute("target") === "_blank" || link.closest(".toastui-editor-defaultUI")) return;
      if (!window.confirm(MESSAGE)) {
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      // 이미 나가기로 했으니 전체 이동 링크에서 beforeunload 확인이 한 번 더 뜨지 않게 한다.
      dirty = false;
    };
    document.addEventListener("input", markDirty, true);
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", beforeLink, true);
    return () => {
      document.removeEventListener("input", markDirty, true);
      window.removeEventListener("beforeunload", beforeUnload);
      document.removeEventListener("click", beforeLink, true);
    };
  }, []);
}
