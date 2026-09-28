# 리조트캠 도메인 모델

## 1. Resort 확장

기존 회원 선호 리조트 마스터인 `Resort`를 재사용합니다. 카메라 테이블에 리조트명과 지역을 중복 저장하지 않습니다.

| 속성 | 타입 | 규칙 |
|---|---|---|
| id | Long | 내부 PK |
| code | String(30) | 변경 불가 업무 식별자, UNIQUE |
| name | String(100) | 표시명, 기존 UNIQUE 유지 |
| regionName | String(50) | 지역 |
| displayOrder | int | 0 이상 |
| active | boolean | 공개 마스터 노출 여부 |

URL과 API에는 숫자 PK가 아닌 소문자 변환한 `code`를 사용합니다. 회원 선호 리조트 FK는 기존 `resort_id`를 그대로 유지합니다.

## 2. ResortCamera

| 속성 | 타입 | 규칙 |
|---|---|---|
| id | Long | 내부 PK |
| resort | Resort | 필수 N:1 |
| code | String(50) | 리조트 내부 고정 코드 |
| name | String(100) | 화면 표시명 |
| sourceType | CameraSourceType | 필수 |
| sourceUrl | String(1000) | HTTPS 영상 또는 페이지 URL |
| externalPageUrl | String(1000) | 실패 시 이동할 원본 페이지, nullable |
| displayOrder | int | 0 이상 |
| active | boolean | 일반 조회 포함 여부 |
| createdAt | LocalDateTime | 생성 시각 |
| updatedAt | LocalDateTime | 수정 시각 |

`(resort_id, code)`는 UNIQUE입니다. `sourceUrl`은 비밀정보가 아니며 공개 API로 전달됩니다.

## 3. CameraSourceType

| 값 | 의미 |
|---|---|
| HLS | `.m3u8` 기반 스트림 |
| YOUTUBE | YouTube 임베드 |
| IFRAME | 삽입을 허용하는 외부 페이지 |
| EXTERNAL_LINK | 내부 재생 없이 원본 페이지 이동 |

재생 구현 자체가 유형에 의존하므로 관리자가 임의 문자열을 추가하는 DB 마스터가 아니라 코드 Enum으로 관리합니다.

## 4. 불변식

- 비활성 리조트의 카메라는 활성 상태여도 공개되지 않습니다.
- 카메라 코드는 같은 리조트 안에서 중복될 수 없습니다.
- 표시 순서는 음수가 될 수 없습니다.
- 내부 재생 유형의 URL은 HTTPS여야 합니다.
- 리조트나 카메라가 없을 때 문자열 비교 예외를 만들지 않고 정적 `ErrorCode`를 사용합니다.

