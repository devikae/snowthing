# 오늘의 설질(Resort Report) 도메인 모델 설계서

## 1. 모델

```mermaid
classDiagram
    class Member {
        +Long id
        +Role role
    }
    class Resort {
        +Long id
        +Boolean active
    }
    class ResortReport {
        +Long id
        +Member author
        +Resort resort
        +String content
        +ResortReportStatus status
        +LocalDateTime deletedAt
        +LocalDateTime createdAt
        +softDelete(deletedAt)
        +changeModerationStatus(status)
    }
    class ResortReportStatus {
        NORMAL
        HIDDEN
        BLOCKED
        DELETED
    }
    ResortReport --> Member : author N:1
    ResortReport --> Resort : target N:1
    ResortReport --> ResortReportStatus
```

## 2. 책임과 불변 조건

- `ResortReport`는 활성 리조트, 가입 회원, 공백 제거 후 1~100자 내용으로만 생성된다.
- 최초 상태는 `NORMAL`이다.
- 작성자 삭제는 `DELETED`와 삭제 시각을 함께 기록한다.
- 관리 상태 변경은 `NORMAL`, `HIDDEN`, `BLOCKED`만 허용하고 서비스 계층에서 관리자 권한과 허용 상태를 검사한다.
- 조회 시각과 작성 제한은 KST `Clock`을 주입받은 서비스에서 계산해 테스트 가능성을 유지한다.

## 3. 일일 한도 동시성

등록 트랜잭션은 먼저 회원 PK 행을 `PESSIMISTIC_WRITE`로 잠근다. 같은 회원의 요청은 이 잠금에서 직렬화된 뒤 KST 당일 범위의 기존 제보를 집계하므로, 두 요청이 동시에 4개를 보고 모두 저장하는 경쟁 조건을 막는다. 서로 다른 회원은 서로 다른 행을 잠그므로 영향을 받지 않는다.

기존 테이블을 직접 집계하는 방식은 구조가 단순하고 별도 정합성 관리가 필요 없지만 작성 이력에 대한 COUNT 비용을 지불한다. 월별 정리와 `(member_id, created_at)` 인덱스로 범위를 작게 유지하며, 트래픽이 크게 증가하면 원자적 카운터 저장소를 별도 설계한다.
