# 오늘의 설질(Resort Report) 도메인 모델 설계서

## 1. 도메인 다이어그램

```mermaid
classDiagram
    class Member {
        +Long memberId
        +String email
        +String nickname
    }

    class Resort {
        +Long resortId
        +String code
        +String name
        +String region
        +Boolean isActive
    }

    class ResortReport {
        +Long reportId
        +Member author
        +Resort resort
        +String content
        +LocalDateTime createdAt
        +validateContent()
    }

    ResortReport --> Member : author (N:1, FetchType.LAZY)
    ResortReport --> Resort : resort (N:1, FetchType.LAZY)
```

## 2. 도메인 엔티티 상세 명세

### ResortReport
- **책임**: 특정 리조트에 대해 회원이 남긴 당일 한 줄 설질 제보 상태를 표현하고 유효성을 검증한다.
- **주요 필드**:
  - `reportId` (Long): PK, 자동 증가(IDENTITY)
  - `author` (Member): 작성자 (ManyToOne, FetchType.LAZY, NOT NULL)
  - `resort` (Resort): 대상 리조트 (ManyToOne, FetchType.LAZY, NOT NULL)
  - `content` (String): 한 줄 설질 제보 내용 (VARCHAR(100), NOT NULL)
  - `createdAt` (LocalDateTime): 제보 등록 일시 (NOT NULL, 기본값 현재 시각)
- **도메인 규칙**:
  - `content`는 null이거나 공백만으로 이루어질 수 없으며, 최대 길이는 100자이다.
  - 작성자는 반드시 가입된 회원이어야 한다 (익명 불가).
  - 리조트는 반드시 활성 상태여야 한다.
