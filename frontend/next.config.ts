import type { NextConfig } from "next";
import path from "node:path";

const repositoryRoot = path.resolve(process.cwd(), "..");

// API 응답은 Spring Security가 같은 헤더를 붙인다. 화면 응답에도 기본 보안 헤더를 붙인다.
// 비발디 캠 플레이어가 같은 출처 iframe이라 frame-ancestors는 'self'를 허용한다.
const securityHeaders = [
  { key: "Content-Security-Policy", value: "frame-ancestors 'self'" },
  { key: "X-Frame-Options", value: "SAMEORIGIN" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Strict-Transport-Security", value: "max-age=31536000" },
];

const nextConfig: NextConfig = {
  turbopack: { root: repositoryRoot },
  outputFileTracingRoot: repositoryRoot,
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  // www로 들어온 요청은 경로와 쿼리를 유지한 채 대표 주소로 301 이동한다.
  // Host가 www.snowthing.org일 때만 걸리므로 localhost 헬스 체크와 API 도메인에는 영향이 없다.
  async redirects() {
    return [
      {
        source: "/:path*",
        has: [{ type: "host", value: "www.snowthing.org" }],
        destination: "https://snowthing.org/:path*",
        statusCode: 301,
      },
    ];
  },
};

export default nextConfig;
