# 중고장터 도메인 모델

- 문서 번호: `DOMAIN-USED-MARKET-001`
- 상태: Draft
- 작성일: 2026-09-28
- 기반 문서: [`01_requirements.md`](./01_requirements.md), [`ADR-201 post-extension.md`](./ADR-201%20post-extension.md)
- 대상 패키지: `com.ikae.snowthing.domain.market`

## 1. 도메인 경계

중고장터는 게시판과 화면 일부를 공유하지만 가격, 상품 상태, 거래 상태처럼 게시판에 없는 규칙을 가진다. 따라서 `Market`을 별도 Bounded Context로 두고, 기존 `Community`의 `Post`, `Comment`, `PostImage`를 콘텐츠 기반으로 참조한다.

```text
Member
  └─ 작성자
      └─ Post ── Comment
          │  └── PostImage
          └─ MarketListing ── MarketCategory
```

역할은 다음처럼 나눈다.

- `Post`: 제목, 본문, 작성자, 조회수, 댓글 수, 이미지, 게시글 운영 상태
- `MarketListing`: 가격, 상품 상태, 거래 방식, 거래 상태, 연락처, 수정 버전
- `MarketCategory`: 상품 분류 마스터 데이터
- `Member`: 판매자 닉네임과 프로필 이미지의 원본

`MarketListing`은 `Post` 없이 존재할 수 없으며 하나의 `Post`에는 하나의 `MarketListing`만 연결된다.

## 2. Aggregate 경계

판매글 생성·수정·삭제의 트랜잭션 경계는 `Post + MarketListing`이다. 두 엔티티를 물리적으로 한 테이블에 합치지는 않지만, 판매글 명령에서는 하나의 Aggregate처럼 취급한다.

### 왜 이렇게 사용하는가

- 댓글과 이미지를 다시 구현하지 않는다.
- 일반 게시글에 가격·연락처 같은 nullable 컬럼을 추가하지 않는다.
- 중고거래 규칙을 `market` 패키지 안에 모은다.
- 생성 도중 한쪽만 저장되는 불완전한 판매글을 막는다.

### 적용 시점

- 판매글 등록
- 제목·본문·이미지·중고거래 정보 수정
- 거래 상태 변경
- 판매글 삭제 및 관리자 운영 상태 변경

목록과 상세 조회는 두 엔티티와 판매자·분류를 조인해 전용 DTO로 반환한다.

## 3. 엔티티

### 3.1. `MarketListing`

| 속성 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `id` | `Long` | 필수 | DB 내부 PK |
| `post` | `Post` | 필수 | 판매글 콘텐츠, 1:1 UNIQUE |
| `category` | `MarketCategory` | 필수 | 상품 분류 |
| `productCondition` | `ProductCondition` | 필수 | 상품 상태 |
| `transactionMethod` | `TransactionMethod` | 필수 | 직거래·택배·모두 가능 |
| `tradeStatus` | `TradeStatus` | 필수 | 판매 중·예약 중·판매 완료 |
| `price` | `Long` | 필수 | 원 단위, 무료 나눔은 0 |
| `negotiable` | `boolean` | 필수 | 가격 협의 가능 여부 |
| `free` | `boolean` | 필수 | 무료 나눔 여부 |
| `contact` | `String` | 필수 | 1~30자 자유 입력 연락처 |
| `version` | `long` | 필수 | 낙관적 락 버전 |
| `createdAt` | `LocalDateTime` | 필수 | 등록 시각 |
| `updatedAt` | `LocalDateTime` | 필수 | 최종 수정 시각 |

판매자는 `Post.member`를 단일 원본으로 사용한다. `MarketListing`에 `seller_id`를 다시 저장하지 않는다. 작성자는 반드시 로그인 회원이므로 `Post.member`는 중고장터 글에서 NULL일 수 없다.

### 3.2. `MarketCategory`

| 속성 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `id` | `Long` | 필수 | DB 내부 PK |
| `code` | `String` | 필수 | 변경하지 않는 UNIQUE 식별 코드 |
| `name` | `String` | 필수 | 화면 표시명 |
| `sortOrder` | `int` | 필수 | 노출 순서 |
| `active` | `boolean` | 필수 | 신규 등록에서 선택 가능 여부 |

분류는 물리 삭제하지 않는다. 비활성 분류는 신규 등록·수정에서 선택할 수 없지만 기존 판매글은 계속 조회할 수 있다.

## 4. Enum과 값 객체

### 4.1. `ProductCondition`

```text
NEW             새 상품
LIKE_NEW        거의 새 상품
GOOD            사용감 적음
USED            사용감 있음
DAMAGED         수리·하자 있음
```

상품 상태는 서비스 규칙에 사용되는 고정 코드이므로 Enum으로 관리한다. 관리자가 표시명만 바꾸는 요구가 생기면 별도 표시 매핑을 둘 수 있지만, 상태 의미 자체를 관리자 데이터로 열지는 않는다.

### 4.2. `TransactionMethod`

```text
DIRECT          직거래
DELIVERY        택배
BOTH            모두 가능
```

### 4.3. `TradeStatus`

```text
ON_SALE         판매 중
RESERVED        예약 중
SOLD            판매 완료
```

거래 취소와 잘못된 상태 변경을 되돌릴 수 있도록 세 상태 사이의 전환을 모두 허용한다. 같은 상태로의 변경은 멱등 성공으로 처리한다.

### 4.4. `Price`

가격은 별도 Embeddable로 만들지 않고 `MarketListing` 내부 규칙으로 관리한다. 현재 가격 연산이나 통화가 하나뿐이라 값 객체를 별도 테이블 구조로 분리할 실익이 없기 때문이다.

불변식은 다음과 같다.

```text
free = true  -> price = 0, negotiable = false
free = false -> 1 <= price <= 20,000,000
```

상한은 잘못된 숫자 입력과 화면 오버플로를 막고 현재 서비스의 중고 장비 거래 범위를 벗어난 입력을 차단하기 위해 20,000,000원으로 확정한다.

## 5. 불변식

1. `MarketListing.post`는 반드시 존재하며 1:1 UNIQUE다.
2. 연결된 `Post.category.code`는 `MARKET`이어야 한다.
3. 연결된 `Post.member`는 NULL일 수 없다.
4. 판매글은 익명일 수 없으며 익명 비밀번호를 갖지 않는다.
5. 상품 분류는 존재해야 하며 등록·수정 시 활성 상태여야 한다.
6. 무료 나눔은 가격 0원이고 가격 협의를 허용하지 않는다.
7. 유료 판매글은 가격이 1원 이상 20,000,000원 이하다.
8. `DAMAGED` 상태도 별도 하자 설명 필드를 요구하지 않는다. 본문 작성 책임은 판매자에게 둔다.
9. 연락처는 앞뒤 공백을 제거한 1~30자 일반 문자열이다.
10. 이미지는 선택이며 0~5장이다.
11. 거래 상태의 최초 값은 `ON_SALE`다.
12. 추천·비추천을 생성할 수 없다.
13. 판매글과 댓글은 로그인 회원만 조회·작성할 수 있다. 단, 홈 공개 미리보기는 썸네일과 publicId만 제공한다.
14. 수정 요청은 현재 버전과 일치해야 한다.

## 6. 명령 모델

### 6.1. 판매글 등록

```text
인증 회원 확인
→ MARKET 게시판 분류 확인
→ 활성 상품 분류 확인
→ 가격·상태·연락처·이미지 검증
→ Post 생성
→ PostImage 연결
→ MarketListing 생성(ON_SALE, version=0)
→ 한 트랜잭션으로 커밋
```

한 단계라도 실패하면 `Post`, `PostImage`, `MarketListing` 변경을 모두 롤백한다.

### 6.2. 판매글 수정

```text
판매글과 작성자 조회
→ 본인 권한 확인
→ 요청 version과 현재 version 비교
→ 입력값 검증
→ Post 제목·본문·이미지 교체
→ MarketListing 거래 정보 변경
→ version 증가
→ 한 트랜잭션으로 커밋
```

JPA의 `@Version`은 `MarketListing`이 실제로 갱신될 때 증가한다. 제목·본문·이미지만 바뀐 경우에도 충돌을 검출하려면 `OPTIMISTIC_FORCE_INCREMENT` 또는 동등한 명시적 버전 증가를 사용한다. 요청 버전을 애플리케이션에서 먼저 비교하더라도 커밋 시점의 경쟁 요청은 DB의 version 조건이 최종적으로 차단한다.

### 6.3. 거래 상태 변경

본문 전체를 다시 보내지 않고 `tradeStatus + version`만 받는다. 상태가 실제로 바뀌면 version을 증가시킨다. 같은 상태 요청은 현재 결과를 반환하고 version을 올리지 않는다.

### 6.4. 삭제

삭제는 `Post`의 기존 Soft Delete 정책을 사용한다. `MarketListing`과 이미지·댓글 행은 남기며 일반 조회에서 숨긴다. 물리 데이터 정리는 보관 정책이 확정된 뒤 별도 작업으로 다룬다.

## 7. 조회 모델

### 7.1. 목록

목록은 `MarketListing`을 기준으로 `Post`, `MarketCategory`, `Member`, 첫 번째 `PostImage`를 조회한다. Entity를 그대로 반환하지 않고 목록 전용 DTO로 projection한다.

- 최신순: `post.created_at DESC, market_listing_id DESC`
- 기본 상태: `ON_SALE`, `RESERVED`
- 검색 시: `SOLD` 포함
- 운영 상태: `Post.status = NORMAL`, `is_deleted = false`
- 제목 검색: `Post.title`
- 선택 필터: 상품 분류, 상품 상태, 거래 상태

사진이 없으면 API는 `thumbnailImageUrl = null`을 반환한다. 기본 이미지 자산은 프런트엔드가 선택한다.

### 7.2. 상세

상세는 연락처와 원본 이미지 URL, 낙관적 락 version을 포함한다. 판매자 이메일과 출발 지역은 포함하지 않는다. 작성자 또는 관리자 여부에 따라 `canEdit`, `canDelete`를 계산한다.

## 8. 계층 분리

### Controller

- HTTP 요청 검증과 인증 주체 전달
- 클라이언트 IP 해석
- HTTP 상태코드 결정
- Repository 직접 호출 금지

### Service

- 판매글 Aggregate 생성·수정·삭제
- 권한과 도메인 불변식 검증
- 트랜잭션 관리
- `HttpSession`, `SecurityContextHolder` 직접 사용 금지

### Repository

- Entity 저장
- 목록 필터와 OFFSET 조회
- 낙관적 락 대상 조회
- Entity를 웹 응답으로 직접 반환하지 않음

### DTO

- Request와 Response 분리
- 컬렉션은 생성 시 `List.copyOf()`로 방어적 복사
- Entity 참조를 외부로 노출하지 않음

## 9. 대안과 트레이드오프

### 기존 `Post`에 필드 추가

구현은 빠르지만 일반 게시글 대부분에 사용하지 않는 nullable 컬럼이 생기고 게시판 서비스가 가격·거래 상태 규칙까지 책임지게 된다.

### 완전 독립 Aggregate

중고장터 경계는 가장 선명하지만 댓글·이미지·조회수·운영 상태를 다시 구현해야 한다. 현재 규모에서는 중복 비용이 크다.

### 선택한 1:1 확장 모델

기존 기능을 재사용하면서 거래 규칙을 분리한다. 대신 두 엔티티를 항상 함께 다뤄야 하고, 일반 게시글 API가 `MARKET` 글을 노출하거나 수정하지 않도록 우회 경로를 차단해야 한다.

## 10. 재검토 조건

- 결제·주문·배송·구매자 개념이 추가되는 경우
- 판매글이 게시판 댓글 대신 1:1 문의만 사용하게 되는 경우
- 중고장터가 별도 서비스로 분리되는 경우
- 상품 옵션이나 여러 개의 재고를 가진 판매 형태가 도입되는 경우
- 하나의 판매글에 여러 판매 상품을 묶어야 하는 경우

이 조건이 생기면 `Post` 확장 모델을 유지할지 독립 `Marketplace` Aggregate로 옮길지 다시 결정한다.
