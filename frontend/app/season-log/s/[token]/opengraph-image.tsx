import { ImageResponse } from "next/og";
import { cardHeadline, topResortLine } from "../../types";
import { fetchSharedSeason } from "./sharedSeason";

export const alt = "눈팅 시즌 결산";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const FONT_TIMEOUT_MS = 3000;
const NAVY = "#0f2942";
const SKY = "#38bdf8";

// ImageResponse의 기본 글꼴에는 한글이 없다. Google Fonts에서 카드에 쓰는 글자만 담은 부분 글꼴(text=)을 받는다.
// User-Agent 없이 요청하면 ImageResponse가 읽을 수 있는 TTF 주소를 준다. 실패하면 null을 돌려 한글 없는 카드로 그린다.
async function loadKoreanFont(text: string): Promise<ArrayBuffer | null> {
  try {
    const cssUrl = `https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@700&text=${encodeURIComponent(text)}`;
    const css = await (await fetch(cssUrl, { signal: AbortSignal.timeout(FONT_TIMEOUT_MS) })).text();
    const fontUrl = css.match(/src: url\((.+?)\) format\('(opentype|truetype)'\)/)?.[1];
    if (!fontUrl) return null;
    const font = await fetch(fontUrl, { signal: AbortSignal.timeout(FONT_TIMEOUT_MS) });
    return font.ok ? await font.arrayBuffer() : null;
  } catch {
    return null;
  }
}

export default async function Image({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const shared = await fetchSharedSeason(token);
  const seasonLabel = shared?.seasonLabel ?? "";
  const headline = shared ? cardHeadline(shared.nickname) : "공유가 꺼진 결산이에요";
  const topLine = shared ? topResortLine(shared.topResort) : "";
  const resorts = (shared?.resorts ?? []).slice(0, 3);
  const resortLines = resorts.map((resort) => `${resort.resortName} ${resort.days}일`);
  const days = shared ? `${shared.totalDays}` : "-";
  const brand = "눈팅 SnowThing 시즌 기록";

  const font = await loadKoreanFont([seasonLabel, headline, topLine, ...resortLines, brand, "일"].join(""));
  // 한글 글꼴이 없으면 한글 줄을 빼고 숫자와 영문만 그린다. 네모 글자가 찍힌 카드보다 낫다.
  const korean = font != null;

  return new ImageResponse(
    (
      <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column", justifyContent: "space-between", background: NAVY, color: "#fff", padding: "64px 72px", fontFamily: korean ? "NotoSansKR" : undefined }}>
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ display: "flex", fontSize: 32, color: "#cbd5e1" }}>{korean ? seasonLabel : `${shared?.seasonStartYear ?? ""} SEASON`}</div>
          {korean && <div style={{ display: "flex", marginTop: 16, fontSize: 52, lineHeight: 1.25 }}>{headline}</div>}
        </div>
        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "flex-end" }}>
            <div style={{ display: "flex", fontSize: 180, lineHeight: 1, color: SKY }}>{days}</div>
            <div style={{ display: "flex", fontSize: 56, marginLeft: 12, marginBottom: 22 }}>{korean ? "일" : "DAYS"}</div>
          </div>
          {korean && resortLines.length > 0 && <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", fontSize: 30, color: "#e2e8f0" }}>
            {resortLines.map((line) => <div key={line} style={{ display: "flex", marginTop: 8 }}>{line}</div>)}
          </div>}
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 26, color: "#94a3b8" }}>
          <div style={{ display: "flex" }}>{korean ? topLine : ""}</div>
          <div style={{ display: "flex" }}>{korean ? brand : "snowthing.org"}</div>
        </div>
      </div>
    ),
    { ...size, fonts: font ? [{ name: "NotoSansKR", data: font, weight: 700, style: "normal" }] : undefined },
  );
}
