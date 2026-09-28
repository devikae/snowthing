import { NextRequest, NextResponse } from "next/server";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ ch: string; file: string }> }
) {
  try {
    const { ch, file } = await params;
    const targetUrl = `http://59.30.12.195:1935/live/_definst_/ch${ch}.stream/${file}`;

    const res = await fetch(targetUrl, {
      cache: "no-store",
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
      },
      signal: AbortSignal.timeout(8000),
    });

    if (!res.ok) {
      return new NextResponse(`High1 upstream error: ${res.status}`, { status: res.status });
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
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": file.endsWith(".m3u8") ? "no-cache, no-store, must-revalidate" : "public, max-age=60",
      },
    });
  } catch (error) {
    return new NextResponse(
      JSON.stringify({ error: "High1 stream unavailable", detail: String(error) }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}
