"use client";

import { useEffect } from "react";
import { confirm } from "./feedback";

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
      const link = (event.target as HTMLElement | null)?.closest?.<HTMLAnchorElement>("a[href]");
      // 새 탭으로 여는 링크와 에디터 본문 안의 링크는 이 화면을 떠나지 않는다.
      if (!link || link.getAttribute("target") === "_blank" || link.closest(".toastui-editor-defaultUI")) return;
      // 확인 창은 답을 기다려야 해서 일단 이동을 막고, 나가기로 하면 같은 링크를 다시 누른다.
      event.preventDefault();
      event.stopPropagation();
      void confirm({ title: "이 화면에서 나갈까요?", description: "작성 중인 내용이 사라집니다.", confirmLabel: "나가기", danger: true }).then((ok) => {
        if (!ok) return;
        // 이미 나가기로 했으니 다시 누른 링크와 beforeunload에서 확인이 한 번 더 뜨지 않게 한다.
        dirty = false;
        link.click();
      });
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
