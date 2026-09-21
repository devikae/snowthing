# ==========================================
# 1단계: 빌드 스테이지 (Node.js 20 Alpine)
# ==========================================
FROM node:20-alpine AS builder
WORKDIR /app

# 패키지 의존성 파일 복사 및 설치 (npm ci로 정확한 잠금 버전 설치)
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

# 소스코드 전체 복사
COPY . .

# 빌드 타임에 실제 도메인 주소(HTTPS) 주입 (예: https://snowthing.shop)
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

# Next.js 프로덕션 빌드 실행
RUN npm run build

# ==========================================
# 2단계: 실행 스테이지 (초경량 런타임)
# ==========================================
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

# 보안을 위한 비root 유저 생성
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

# 프로덕션 실행에 필요한 결과물만 복사
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/package-lock.json ./package-lock.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next

USER nextjs

EXPOSE 3000

# 프로덕션 서버 구동 (npm run start)
CMD ["npm", "run", "start"]
