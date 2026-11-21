# ==========================================
# 1단계: 빌드 스테이지 (Node.js 22 Alpine)
# ==========================================
# 태그가 다른 이미지로 바뀌어도 같은 빌드가 나오도록 digest로 고정한다(백엔드 Dockerfile과 같은 방식).
FROM node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a AS builder
WORKDIR /app/frontend

# 패키지 의존성 파일 복사 및 설치 (npm ci로 정확한 잠금 버전 설치)
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --legacy-peer-deps

# 소스코드 전체 복사
COPY frontend .
COPY shared /app/shared

# 빌드 타임에 실제 도메인 주소(HTTPS) 주입 (예: https://snowthing.shop)
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_KAKAO_MAP_APP_KEY
ENV NEXT_PUBLIC_KAKAO_MAP_APP_KEY=$NEXT_PUBLIC_KAKAO_MAP_APP_KEY
ARG GOOGLE_SITE_VERIFICATION
ARG NAVER_SITE_VERIFICATION
ENV GOOGLE_SITE_VERIFICATION=$GOOGLE_SITE_VERIFICATION
ENV NAVER_SITE_VERIFICATION=$NAVER_SITE_VERIFICATION

# Next.js 프로덕션 빌드 실행
RUN npm run build

# ==========================================
# 2단계: 실행 스테이지 (초경량 런타임)
# ==========================================
FROM node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
# standalone 서버는 HOSTNAME으로 듣는 주소를 정한다. Docker가 HOSTNAME을 컨테이너 ID로 채우므로 모든 주소로 듣게 덮어쓴다.
ENV HOSTNAME=0.0.0.0
ARG GOOGLE_SITE_VERIFICATION
ARG NAVER_SITE_VERIFICATION
ENV GOOGLE_SITE_VERIFICATION=$GOOGLE_SITE_VERIFICATION
ENV NAVER_SITE_VERIFICATION=$NAVER_SITE_VERIFICATION

# 보안을 위한 비root 유저 생성
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

# next.config.ts의 output: "standalone" 결과만 복사한다(IKA-105).
# standalone에는 서버가 실제로 쓰는 파일과 런타임 의존성만 들어 있고, dev 의존성과 빌드 도구는 빠진다.
# next.config의 설정(poweredByHeader 등)은 standalone 서버 파일에 함께 들어가므로 설정 파일을 따로 두지 않아도 된다(IKA-58 재발 확인).
# outputFileTracingRoot가 저장소 루트라 서버 파일은 standalone/frontend/ 아래에 생긴다.
COPY --from=builder --chown=nextjs:nodejs /app/frontend/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/frontend/.next/static ./frontend/.next/static
COPY --from=builder --chown=nextjs:nodejs /app/frontend/public ./frontend/public

USER nextjs

EXPOSE 3000

# npm을 거치지 않고 node가 직접 서버를 띄워 종료 신호(SIGTERM)를 바로 받는다.
WORKDIR /app/frontend
CMD ["node", "server.js"]
