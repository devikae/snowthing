# 리조트 슬로프 운영 현황판 (IKA-66, 2026-11-09)

## 목표

- 리조트별 오늘 운영 슬로프와 운영 시간을 한 화면(`/slopes`)에서 본다.
- 출처는 리조트 공식 사이트만 쓴다. 커뮤니티나 다른 서비스는 쓰지 않는다.
- 공식 사이트에 부담을 주지 않는다. 하루 몇 번만 읽고, 사용자 요청이 외부 호출로 이어지지 않게 한다.
- 수집이 실패해도 마지막 성공 값과 그 시각, 출처 링크를 보여 준다.

## 범위

- 포함: 출처 조사, 수집기(리조트별 켜고 끄기), 저장 테이블(046), 공개 조회 API, `/slopes` 화면, 슬로프캠 헤더 링크.
- 제외: 리프트 운행 현황(모나용평, 곤지암은 표가 있으나 이번에는 슬로프만), 혼잡도, 관리자 화면, 알림, 수집 이력 보관.

## 출처 조사 (2026-11-09 새벽, 시즌 전)

조사용으로 리조트당 2~5번 요청했다. User-Agent는 `Mozilla/5.0 (compatible; SnowThingBot/1.0; +https://snowthing.org)`. 시즌 전이라 모든 슬로프가 닫혀 있어 열린 상태의 표기는 페이지 범례와 스크립트로 추정했다.

| 리조트 | 현황 주소 | 형식 | robots.txt | 수집 | 비고 |
|---|---|---|---|---|---|
| 하이원 | `https://www.high1.com/ski/slopeView.do?key=748&mode=p` | 서버 렌더 HTML 표. 캡션 "슬로프 오픈현황". 코스(제우스 등) rowspan, 번호, 주간, 야간, 난이도 | `Allow: /` (게시판 목록과 검색만 막음) | 구현 | `span.slope_open`에 OPEN/CLOSE. 주석 처리된 줄과 칸이 섞여 rowspan 숫자가 실제 줄 수와 다를 수 있다 |
| 모나용평 | `https://www.yongpyong.co.kr/kor/skiNboard/slope/openStatusBoard.do` | 서버 렌더 HTML 표 `tbody#slopeStatus`. 난이도 rowspan, 슬로프, 주간, 야간, 비고. 표 머리에 운영 시간 | `/resource/`, `/WEB-INF/...`만 막음 | 구현 | 리프트 운행 표 `tbody#liftStatus`도 있음. 인증서 루트가 OISTE WISeKey Global Root GB CA라 로컬 Oracle JDK에서는 TLS 연결이 실패한다. 운영 이미지(eclipse-temurin:21-jre-jammy, Dockerfile 고정 digest)의 cacerts에는 이 루트가 있다(keytool로 확인) |
| 곤지암 | `https://www.konjiamresort.co.kr/ski/slopeOpenClose.dev` | 서버 렌더 HTML 표 `table.chart-a1`. 슬로프, 수준, 주간, 야간, 백야 | 없음(404) | 구현 | "Open 30분 전에 업데이트" 안내. 리프트 표도 있음 |
| 휘닉스 | `https://phoenixhnr.co.kr/api/snowpark/slope` (화면: `/static/pyeongchang/snowpark/slope-lift`) | JSON 배열. 공식 화면이 axios로 부른다 | 없음(오류 페이지로 이동) | 구현 | 시즌 밖에는 제원만 온다. 시즌 중 필드(`slopeOpenDivNm`, `slopeOpenTime`, `slopeOpenYn`)는 공식 화면 스크립트에서 확인한 이름이다. 실제 시즌 응답은 아직 못 봤다 |
| 무주덕유산 | `https://www.mdysresort.com/convert_main_slope_221207.asp` | 서버 렌더 HTML. 슬로프별 새벽, 오전, 오후, 야간, 자정 칸(`ul.color_check li`) | `Allow: /` | 보류 | 시즌 전이라 칸이 모두 비어 있어 열림 표기(클래스나 글자)를 알 수 없다. 개장 후 한 번 보고 파서를 붙인다 |
| 오투 | `https://www.o2resort.com/SKI/slope.jsp`, `/SKI/liftInfo.jsp` | 정적 제원 표 | `/SKI/*` 허용 | 불가 | 운영 현황 페이지를 찾지 못했다 |
| 비발디파크 | `https://www.sonohotelsresorts.com/skiboard/info` | Nuxt 화면 | `Allow: /` | 불가 | 운영 안내 문구만 있고 슬로프별 현황 페이지나 API를 찾지 못했다 |
| 웰리힐리 | `https://www.wellihillipark.com/` | Nuxt 화면(`openSlope` 화면 조각 있음) | 일부 봇만 막고 `*`는 허용 | 불가 | 화면이 브라우저에서 데이터를 받는다. 조사 요청 수 안에서 API 주소를 찾지 못했다 |
| 오크밸리 | `https://www.oakvalley.co.kr/` | Vite 단일 페이지 앱 | 없음(앱 화면으로 응답) | 불가 | 서버 HTML에 데이터가 없다. API 주소를 찾지 못했다 |

- 지산, 엘리시안 강촌, 에덴밸리, 알펜시아는 이번 조사 대상에서 뺐다(티켓 목록 밖).
- 모든 구현 대상은 쿠키, 로그인 없이 GET 한 번으로 받는다. 리다이렉트 없이 200을 준다.

## 수집

- 스케줄: KST 06:30부터 20:30까지 2시간마다(하루 8번). `snowthing.slope-status.cron`으로 바꿀 수 있다.
- 켜고 끄기: `SNOWTHING_SLOPE_STATUS_ENABLED_RESORTS=HIGH1,YONGPYONG,KONJIAM,PHOENIX`처럼 리조트 코드를 적는다. 기본은 비어 있어 아무 곳도 부르지 않는다.
- 리조트를 하나씩 차례로 부른다. 한 리조트 실패가 다른 리조트를 막지 않는다.
- HTTP: 연결 3초, 읽기 10초, 리다이렉트를 따르지 않는다. 스케줄러 스레드에서만 부른다.
- 하루 외부 호출량: 켠 리조트당 8번. 네 곳을 모두 켜도 하루 32번이다.
- 실패 판단: 연결 실패, 4xx/5xx, 빈 본문, 표나 JSON을 찾지 못함, 슬로프 0개.
- 실패하면 마지막 성공 값은 두고 시도 시각과 실패 상태만 바꾼다. 실패 사유는 DB에만 남기고 API로 내보내지 않는다.

### 파서 규칙

- 칸 글자가 정확히 `OPEN`(대소문자 무시)이면 열림, 비었거나 `-`면 정보 없음(null), 나머지(CLOSE, 정설 등)는 닫힘. 열림을 좁게 잡아 모르는 표기가 생겨도 열린 슬로프를 부풀리지 않는다.
- 한 시간대라도 열려 있으면 그 슬로프를 운영 중으로 센다.
- 휘닉스: 슬로프마다 주간, 야간 기록이 따로 온다. `slopeOpenYn`이 있으면 그 값(Y), 없으면 `slopeOpenTime`이 있는지로 판단한다(공식 화면도 시간만 보여 준다). 시간대 기록이 없으면 닫힘. 운영 시간은 슬로프 비고에 남긴다.
- 파서는 순수 함수다. 조사 때 받은 실제 응답(HTML은 표 부분만 잘라 둠)을 테스트 픽스처로 두고, 열린 경우는 같은 응답에서 칸 글자만 바꿔 확인한다.

## 데이터 모델 (046)

`resort_slope_status`: 리조트당 한 줄.

| 열 | 형식 | 설명 |
|---|---|---|
| resort_slope_status_id | BIGINT PK | |
| resort_id | BIGINT, UNIQUE, FK resort | |
| source_url | VARCHAR(500) | 사용자에게 보여 줄 공식 페이지 |
| slopes_json | MEDIUMTEXT NULL | 마지막 성공 슬로프 목록 `[{name, difficulty, day, night, lateNight, note}]` |
| open_slope_count, total_slope_count | INT NULL | 마지막 성공 기준 |
| operating_note | VARCHAR(300) NULL | 출처가 적은 운영 시간 문구 |
| collected_at | DATETIME(6) NULL | 마지막 성공 시각 KST |
| last_attempt_at | DATETIME(6) | 마지막 시도 시각 KST |
| last_result | VARCHAR(20) | SUCCESS, FAILED (CHECK) |
| failure_reason | VARCHAR(200) NULL | 운영 확인용 |
| created_at, updated_at | DATETIME(6) | |

- 첫 줄 동시 생성은 UNIQUE(resort_id)가 막는다. 충돌하면 그 회차는 건너뛰고 다음 회차에 갱신한다.

## API

`GET /api/v1/slope-status` (공개, 인증 없음)

```json
{
  "resorts": [
    {
      "resortCode": "YONGPYONG",
      "resortName": "모나용평",
      "regionName": "강원 평창",
      "supported": true,
      "sourceUrl": "https://www.yongpyong.co.kr/kor/skiNboard/slope/openStatusBoard.do",
      "collectedAt": "2026-12-01T08:30:00.123+09:00",
      "lastAttemptAt": "2026-12-01T10:30:00.456+09:00",
      "stale": true,
      "openSlopeCount": 5,
      "totalSlopeCount": 21,
      "operatingNote": "주간 08:30~16:30, 야간 18:00~22:00",
      "slopes": [
        { "name": "옐로우", "difficulty": "초급", "open": true, "day": "OPEN", "night": "CLOSED", "lateNight": null, "note": null }
      ]
    }
  ]
}
```

- 활성 리조트를 표시 순서(display_order, id)대로 모두 담는다. 출처가 없는 리조트는 `supported: false`.
- `collectedAt`이 null이면 한 번도 성공하지 못한 것이다(화면은 "정보 준비 중" 또는 "시즌 개장 전").
- `stale`: 마지막 시도가 실패해 이전 성공 값을 보여 주는 중.
- 저장된 값만 읽는다. 쿼리 두 번(활성 리조트, 현황 줄). 외부 호출 없음.
- `Cache-Control: public, max-age=300, stale-while-revalidate=600`.

## 화면

- `/slopes`: 수집하는 리조트마다 카드. 지역, 이름, 열린 슬로프 수/전체, 상태 문구, 운영 시간, "슬로프별 보기"(펼치기), 갱신 시각, 공식 페이지 링크.
  - 상태 문구: 값 없음이면 개장 전(`seasonOpening.ts` 기준) "시즌 개장 전", 아니면 "정보 준비 중". 값이 있고 열린 슬로프가 0이면 개장 전 "시즌 개장 전", 아니면 "운영 중인 슬로프 없음".
  - 갱신 실패면 "최근 갱신에 실패해 이전 값을 보여 줍니다".
  - 수집하지 않는 리조트는 아래에 이름만 한 줄로 알린다.
- 슬로프캠 헤더(`/resort-cam/{resort}`): 기온 칩 옆에 "슬로프 현황" 링크(`/slopes#{리조트}`).

## 결정과 대안

- 저장 단위: 리조트당 한 줄 + 슬로프 목록 JSON. 리조트마다 슬로프 이름과 칸이 달라 행으로 나누면 이름 매칭 규칙이 따로 필요하다. 읽기는 항상 리조트 단위로 통째로 한다. 대안: `resort_slope`(리조트, 슬로프 이름, 시간대별 상태) 행 구조. 슬로프별 이력이나 통계가 필요해지면 옮긴다.
- 이력: 최신 스냅샷만 둔다. 하루 32줄씩 쌓는 이력 테이블은 지금 쓰는 곳이 없다.
- 메모리 캐시(기온 방식) 대신 DB: 기온은 요청 때 수집하지만 이번 것은 스케줄 수집이고, 재시작해도 마지막 성공 값과 시각을 보여 줘야 한다(완료 조건). 그래서 DB에 둔다.
- 서버 여러 대: 지금은 한 대라 분산 잠금을 두지 않았다. 대수가 늘면 대수만큼 호출이 늘어난다. 그때 ShedLock이나 Redis 잠금을 붙인다.
- 기본 꺼짐: 운영에서 사용자가 리조트별로 켠다. 공식 사이트 이용 조건을 사람이 확인한 뒤 켜게 하기 위해서다.
- 리프트: 이번에는 슬로프만 저장한다. 출처마다 리프트 표 구조가 더 제각각이다.
- 열림 판단을 좁게(`OPEN`만): 시즌 중 표기가 달라지면 열린 슬로프가 0으로 보일 수 있다. 개장 후 실제 화면과 대조해 맞춘다.

## 테스트

- 파서: 실제 응답 픽스처 4종, 열린 경우(칸 글자 교체), 휘닉스 시즌 중 예시, 표 없음, 빈 JSON, JSON 아님.
- 수집과 API(실제 DB, SecurityConfig): 성공 저장과 응답, 성공 뒤 실패 시 이전 값 유지와 stale, 첫 실패, 파싱 실패, 꺼진 리조트와 비활성 리조트 미호출, 출처 없는 리조트 `supported: false`, 비로그인 접근, Cache-Control.

## 사용자 결정 필요

- 운영에서 켤 리조트(`SNOWTHING_SLOPE_STATUS_ENABLED_RESORTS`)와 수집 주기.
- 개장 후 휘닉스 시즌 응답, 무주 열림 표기 확인(무주 파서 추가 여부).
