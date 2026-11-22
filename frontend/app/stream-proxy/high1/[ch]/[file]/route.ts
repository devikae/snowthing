import { NextRequest, NextResponse } from "next/server";
import { normalizeLegacyAvcManifest } from "../../../../resort-cam/hlsManifest";
import { fetchWowzaFile, rememberWowzaSession, toPublicWowzaManifest, type WowzaSource } from "../../../wowzaSession";

const HEADERS = { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" };

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ ch: string; file: string }> }
) {
  try {
    const { ch, file } = await params;
    if (!/^[1-9]\d?$/.test(ch) || Number(ch) > 18 || file.length > 120 || !/^[A-Za-z0-9_-]+\.(m3u8|ts|m4s|mp4|aac)$/.test(file)) {
      return new NextResponse("Invalid camera path", { status: 400 });
    }
    const base = `http://59.30.12.195:1935/live/_definst_/ch${ch}.stream`;
    // 번호 발급과 403 재요청까지 한 요청 안에서 끝나도록 시간 제한을 함께 쓴다.
    const init: RequestInit = { headers: HEADERS, signal: AbortSignal.timeout(10000) };
    const source: WowzaSource = {
      key: `high1:${ch}`,
      playlistUrl: async () => `${base}/playlist.m3u8`,
      fileUrl: async (upstreamFile) => `${base}/${upstreamFile}`,
      init,
    };

    const res = file === "playlist.m3u8"
      ? await fetch(`${base}/playlist.m3u8`, { ...init, cache: "no-store" })
      : await fetchWowzaFile(source, file);

    if (!res.ok) {
      return new NextResponse(`High1 upstream error: ${res.status}`, { status: res.status });
    }

    const contentType = file.endsWith(".m3u8")
      ? "application/vnd.apple.mpegurl"
      : file.endsWith(".ts")
      ? "video/MP2T"
      : "application/octet-stream";

    let body: BodyInit | null = res.body;
    if (file.endsWith(".m3u8")) {
      const manifest = await res.text();
      if (file === "playlist.m3u8") rememberWowzaSession(source.key, manifest);
      body = normalizeLegacyAvcManifest(toPublicWowzaManifest(manifest));
    }
    return new NextResponse(body, {
      status: 200,
      headers: {
        "Content-Type": contentType,
        "Cache-Control": file.endsWith(".m3u8") ? "no-cache, no-store, must-revalidate" : "public, max-age=60",
      },
    });
  } catch {
    return new NextResponse(
      JSON.stringify({ error: "High1 stream unavailable" }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}
