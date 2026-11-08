import { NextRequest, NextResponse } from "next/server";
import { toPublicWowzaManifest, toUpstreamWowzaFile } from "../../../wowzaSession";

interface CacheEntry {
  url: string;
  expiresAt: number;
}

const tokenCache = new Map<string, CacheEntry>();

async function getMujuM3u8Url(cam: string): Promise<string> {
  const cached = tokenCache.get(cam);
  if (cached && Date.now() < cached.expiresAt) {
    return cached.url;
  }

  const paddedCam = cam.padStart(2, "0");
  const popupUrl = `http://www.mdysresort.com/guide/webcam_popup_jh.asp?cam_num=${paddedCam}`;

  const res = await fetch(popupUrl, {
    cache: "no-store",
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    },
    signal: AbortSignal.timeout(5000),
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch Muju popup: HTTP ${res.status}`);
  }

  const html = await res.text();
  const match = html.match(/src=["'](http:\/\/muju\.cache\.cdn\.cloudn\.co\.kr\/[^"']+)["']/);
  if (!match || !match[1]) {
    throw new Error("Could not find Muju m3u8 source URL in popup HTML");
  }

  const url = match[1];
  // 60초 캐싱
  tokenCache.set(cam, { url, expiresAt: Date.now() + 60 * 1000 });
  return url;
}

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ cam: string; file: string }> }
) {
  try {
    const { cam, file } = await params;
    if (!/^\d{1,2}$/.test(cam) || file.length > 120 || !/^[A-Za-z0-9_-]+\.(m3u8|ts|m4s|mp4|aac)$/.test(file)) {
      return new NextResponse("Invalid camera path", { status: 400 });
    }
    const paddedCam = cam.padStart(2, "0");

    if (file === "playlist.m3u8") {
      const targetUrl = await getMujuM3u8Url(paddedCam);
      const res = await fetch(targetUrl, {
        cache: "no-store",
        signal: AbortSignal.timeout(5000),
      });

      if (!res.ok) {
        return new NextResponse(`Upstream error: ${res.status}`, { status: res.status });
      }

      const content = toPublicWowzaManifest(await res.text());
      return new NextResponse(content, {
        headers: {
          "Content-Type": "application/vnd.apple.mpegurl",
          "Cache-Control": "no-cache, no-store, must-revalidate",
        },
      });
    }

    // chunklist 또는 조각. 시청자 주소에는 토큰이 없으므로 서버가 가진 토큰을 붙인다.
    const token = new URL(await getMujuM3u8Url(paddedCam)).search;
    const targetUrl = `http://muju.cache.cdn.cloudn.co.kr/mujuresort/_definst_/cam${paddedCam}.stream/${toUpstreamWowzaFile(file)}${token}`;

    const res = await fetch(targetUrl, {
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok) {
      if (res.status === 400 || res.status === 403) {
        tokenCache.delete(paddedCam);
      }
      return new NextResponse(`Segment error: ${res.status}`, { status: res.status });
    }

    const contentType = file.endsWith(".m3u8")
      ? "application/vnd.apple.mpegurl"
      : file.endsWith(".ts")
      ? "video/MP2T"
      : "application/octet-stream";

    const body = file.endsWith(".m3u8") ? toPublicWowzaManifest(await res.text()) : res.body;
    return new NextResponse(body, {
      status: 200,
      headers: {
        "Content-Type": contentType,
        "Cache-Control": file.endsWith(".m3u8") ? "no-cache, no-store, must-revalidate" : "public, max-age=60",
      },
    });
  } catch (error) {
    console.error("[MujuStreamProxy] Error:", error);
    return new NextResponse(
      JSON.stringify({ error: "Stream unavailable" }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}
