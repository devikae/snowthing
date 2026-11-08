import { NextRequest, NextResponse } from "next/server";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ file: string }> }
) {
  try {
    const { file } = await params;
    if (file.length > 120 || !/^[A-Za-z0-9_-]+\.(m3u8|ts|m4s|mp4|aac)$/.test(file)) {
      return new NextResponse("Invalid camera path", { status: 400 });
    }
    const targetUrl = `http://118.46.149.144:8080/ramdisk/${file}`;

    const res = await fetch(targetUrl, {
      cache: "no-store",
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
      },
      signal: AbortSignal.timeout(6000),
    });

    if (!res.ok) {
      return new NextResponse(`O2 upstream error: ${res.status}`, { status: res.status });
    }

    const contentType = file.endsWith(".m3u8")
      ? "application/vnd.apple.mpegurl"
      : file.endsWith(".ts")
      ? "video/MP2T"
      : "application/octet-stream";

    return new NextResponse(res.body, {
      status: 200,
      headers: {
        "Content-Type": contentType,
        "Cache-Control": file.endsWith(".m3u8") ? "no-cache, no-store, must-revalidate" : "public, max-age=60",
      },
    });
  } catch {
    return new NextResponse(
      JSON.stringify({ error: "O2 stream unavailable" }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}
