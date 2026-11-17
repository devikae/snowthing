# 개장 D-DAY 관리자 관리 (IKA-87, 2026-11-17)

## 왜

- 리조트 개장일은 `frontend/app/lib/seasonOpening.ts` 상수였다(019_resort_weather_dday). 리조트가 날짜를 발표하거나 바꿀 때마다 코드 수정과 배포가 필요했다.
- 개장 시즌에는 날짜가 자주 바뀐다(용평 11.20에서 11.22, 하이원 11.27에서 11.24). 관리자가 화면에서 바꾸고 사용자는 새로고침하면 보게 한다.

## 범위

- 포함: 개장일 테이블과 초기값, 공개 조회 API, 관리자 조회와 저장 API, 관리자 화면 `/admin/season-openings`, 메인 배너와 슬로프캠 D-DAY, 슬로프 현황, 시즌권 가격 표가 API 값을 쓰게 바꾸기.
- 제외: 리조트 추가와 삭제(마이그레이션으로 한다), 개장일 자동 수집, 시즌별 이력.
- 함께 반영: 닫힌 PR #147의 날짜 변경(용평 11.22, 하이원 11.24, AI 멤버 첫 개장일 기본값 11.22).

## 테이블 (052 마이그레이션)

`season_opening`, 리조트 한 곳에 한 줄.

| 컬럼 | 형식 | 설명 |
|---|---|---|
| season_opening_id | BIGINT PK | |
| resort_code | VARCHAR(30) UNIQUE | 리조트 코드. 슬로프캠, 슬로프 현황, 기온과 같은 코드 |
| resort_name | VARCHAR(50) | 배너에 보이는 짧은 이름 |
| opening_date | DATE NULL | 개장일. NULL이면 D-DAY를 그리지 않는다 |
| confirmed | BOOLEAN | 공식 발표 날짜인지. 아니면 "예상"을 붙인다 |
| show_on_banner | BOOLEAN | 메인 개장 배너에 넣을지 |
| banner_order | INT | 배너 순서, 작을수록 앞 |
| created_at, updated_at | DATETIME(6) | |

- 메인 배너 전체 스위치는 `system_config`의 `SEASON_OPENING_BANNER_ENABLED`(BOOLEAN) 한 줄에 둔다.
- 초기값: 예전 상수 9곳. `show_on_banner`는 예전 `confirmed` 값, `banner_order`는 개장일 순, 같은 날이면 이름 순.

## API

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/public/season-openings` | 누구나 | `{ bannerEnabled, resorts: [{ resortCode, name, openingDate, confirmed, showOnBanner, bannerOrder }] }`. 배너 순서, 같으면 ID 순. `Cache-Control: no-cache` |
| GET | `/api/v1/admin/season-openings` | 관리자 | 공개 조회와 같은 모양 |
| PUT | `/api/v1/admin/season-openings` | 관리자 | 목록 전체와 스위치를 한 번에 저장하고 저장 뒤 목록을 돌려준다 |

저장 검증 (모두 400, 아무것도 바꾸지 않는다)

- 형식: 스위치 필수, 목록 1개 이상 50개 이하, 리조트 코드 필수, 배너 순서 1 이상 999 이하, 날짜는 YYYY-MM-DD.
- ADMIN_003: 저장된 리조트 전부를 한 번씩 보내지 않음(모르는 코드, 빠진 코드, 같은 코드 두 번).
- ADMIN_004: 배너 순서가 겹침.
- ADMIN_005: 배너에 넣을 리조트에 개장일이 없음.
- ADMIN_006: 개장일이 2020년부터 2100년 밖.

값이 바뀐 저장만 관리자 조치 기록(`SEASON_OPENING_UPDATE`, 대상 `SEASON_OPENING`, 식별자 `ALL`)을 남긴다. 사유에는 스위치 상태와 바뀐 리조트 코드를 적는다.

## 화면

- 관리자 `/admin/season-openings`(메뉴 "개장일"): 배너 스위치, 리조트별 날짜 입력, "확정", "배너에 표시", 위아래 순서 버튼, 저장 버튼. 순서는 화면의 줄 순서대로 1부터 매겨 보낸다.
- 메인 개장 배너: 스위치가 켜져 있고 "배너에 표시"를 고른 리조트 중 아직 개장 전인 곳을 관리자가 정한 순서대로. 모두 개장했거나 스위치가 꺼지면 그리지 않는다.
- 슬로프캠 D-DAY 배지, 슬로프 현황의 "시즌 개장 전", 시즌권 가격 표의 개장일 글자도 같은 API 값을 쓴다.
- 네 곳 모두 `lib/useSeasonOpenings.ts`의 요청 하나를 나눠 쓴다. API가 실패하면 D-DAY 부분만 그리지 않고 나머지 화면은 그대로다.

## 결정

- 스위치 저장 위치: `system_config` 한 줄. 이미 관리자 운영 설정을 두는 표라 새 표가 필요 없다. 다른 안은 설정 전용 한 줄 테이블이나 `season_opening`에 컬럼을 두는 것인데, 리조트마다 같은 값을 반복하거나 표가 하나 더 생긴다.
- `confirmed` 컬럼 추가: 승인된 계획의 표에는 없었다. 예전 상수는 확정 여부로 "예상" 글자와 배너 표시를 함께 정했는데, 배너 표시를 관리자 선택(`show_on_banner`)으로 떼면 "예상" 글자를 정할 값이 사라진다. 배너 표시 값으로 대신하면 확정됐지만 배너에서 뺀 리조트가 "예상"으로 보인다. 그래서 컬럼을 하나 더 둔다.
- 저장은 목록 전체를 한 번에: 순서를 맞바꾸면 한 줄씩 저장하는 동안 순서가 겹친다. `banner_order`에 UNIQUE를 두지 않은 이유도 같다. 겹침은 서비스가 막는다.
- 캐시: 서버 캐시 없이 DB에서 읽는다(10줄 안팎). 응답은 `no-cache`라 새로고침마다 다시 받는다. 화면 안에서는 요청 하나를 나눠 쓰고, 관리자가 저장하면 그 탭의 캐시를 비운다.
- 동시 저장: 마지막 저장이 이긴다. 관리자 수가 적어 낙관적 잠금은 두지 않았다.

## 검증

- 서비스 단위 테스트, 컨트롤러 형식 검증 테스트, 실제 보안 설정 통합 테스트(공개 조회, 401, 403, 저장 뒤 공개 조회 반영, 조치 기록, 순서 겹침 400).
- 프론트 lint, `next build --webpack`.
