# [Notion] Snowthing 전체 시스템 아키텍처 구성도 (TIL)

> 📌 **작성 일자**: 2026년 8월 10일  
> 🏷️ **문서 목적**: 노션(Notion) 및 마크다운에 복사하여 전체 시스템 구성도, 계층별 역할, 데이터 흐름을 한눈에 파악하고 공부하기 위한 종합 가이드

---

## 📊 1. 시스템 구성도 다이어그램 (Mermaid System Architecture)

Below is the system architecture diagram rendered directly in GitHub Markdown and Notion.

```mermaid
flowchart TB
    %% 클라이언트 레이어
    subgraph Client_Layer [👤 Client / Browser Layer]
        Browser[🌐 Web Browser / Mobile Client]
    end

    %% 프론트엔드 레이어
    subgraph Frontend_Layer [💻 Frontend Layer]
        NextJS[⚡ Next.js 14+ Application<br/>App Router / React Query / Optimistic UI]
    end

    %% 네트워크 & 프록시 레이어
    subgraph Proxy_Layer [🛡️ Network & Proxy Layer]
        Nginx[🔒 Nginx Reverse Proxy<br/>SSL/HTTPS Termination<br/>Route /api/* -> Spring Boot]
    end

    %% 백엔드 애플리케이션 레이어
    subgraph Backend_Layer [⚙️ Backend Application Layer - Spring Boot 3.2+ / Java 21]
        SecFilter[🔒 Spring Security Filter<br/>JSESSIONID / HttpOnly / changeSessionId]

        
        subgraph Core_App [App Core Services]
            MemberSvc[👤 Member Service<br/>Profile & Crew Management]
            PostSvc[📝 Post Service<br/>Single Query + In-Memory Tree]
            CommentSvc[💬 Comment Service<br/>Single Query + Flat List]
            ReactionSvc[👍 Reaction Service<br/>Async Event Publisher]
        end
        
        BatchJob[⏰ Scheduled Batch Sync Job<br/>10s Write-Behind DB Flush]
    end

    %% 인메모리 캐시 & 분산 락 레이어
    subgraph Cache_Layer [🚀 In-Memory Cache & Buffer Layer]
        Redis[(🧠 Redis 7.x In-Memory<br/>SADD Voters / INCR Like Counter<br/>AOF Persistence)]
    end

    %% 릴레이셔널 데이터베이스 레이어
    subgraph Database_Layer [🗄️ Relational Database Layer]
        MySQL[(🐬 MySQL 8.0 Container<br/>InnoDB Engine / 11 Tables<br/>BIGINT id + UUID v7 Secondary Index)]
    end

    %% 데이터 흐름 연결
    Browser <-->|HTTP/HTTPS Page Request| NextJS
    Browser <-->|HTTPS API Request / Cookies| Nginx
    NextJS <-->|Server Component Fetch| Nginx
    
    Nginx <-->|Forward /api/*| SecFilter
    SecFilter <-->|Session Check / Context| Core_App
    
    ReactionSvc -->|0.0001s In-Memory INCR & SADD| Redis
    BatchJob <-->|Read Counter & Flush| Redis
    
    Core_App <-->|JPA / JPQL / Single Query| MySQL
    BatchJob -->|Write-Behind Batch UPDATE| MySQL

    %% 스타일링
    style Browser fill:#333,stroke:#fff,color:#fff
    style NextJS fill:#000,stroke:#61dafb,color:#61dafb
    style Nginx fill:#009639,stroke:#fff,color:#fff
    style SecFilter fill:#d4ac0d,stroke:#fff,color:#000
    style Core_App fill:#2e4053,stroke:#fff,color:#fff
    style Redis fill:#dc382d,stroke:#fff,color:#fff
    style MySQL fill:#00758f,stroke:#fff,color:#fff
    style BatchJob fill:#8e44ad,stroke:#fff,color:#fff
```

---

## 🔍 2. 5대 계층 구조 및 핵심 기술 요약

1. **`Client & Frontend`**: Next.js 14+, React Query의 **낙관적 UI 업데이트**를 통해 추천 0.001초 미친 반응성 제공.
2. **`Proxy & Network`**: Nginx를 통해 `/api/*` 라우팅 분리 및 **6대 쿠키 보안 속성** 적용.
3. **`Backend Core`**: Spring Boot 3.x, **`Single Query + In-Memory Tree`**로 N+1 문제 완파, 10초 주기 **`Write-Behind` 배치 스케줄러** 구동.
4. **`In-Memory Cache`**: Redis 7.x `INCR`/`SADD`로 DB 락 병목 제거, AOF로 데이터 유실 방지.
5. **`Relational Database`**: MySQL 8.0 (11개 테이블), `BIGINT id` + **`UUID v7` 세컨더리 인덱스** 적용.
