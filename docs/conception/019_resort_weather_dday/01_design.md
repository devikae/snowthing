# 리조트 기온·개장 D-DAY (2026-11-04)

## 배경과 목표

- 슬로프캠 리조트 헤더에 현재 기온을 보여 준다. 사용자는 영상과 함께 현장 기온을 한 화면에서 확인한다.
- 시즌 개장 전에는 리조트별 개장 D-DAY를 보여 준다. 메인에는 가장 빠른 개장의 D-DAY 배너를 잠깐 두고 시즌이 시작되면 내린다.
- 운영 서버가 작다. 보는 사람이 없을 때 외부 호출이 생기지 않아야 하고, 외부 사이트가 느리거나 죽어도 캠 페이지가 영향을 받으면 안 된다.

## 범위

- 포함: 개장일 프론트 상수와 D-DAY 계산, 메인 배너, 슬로프캠 헤더 D-DAY 태그와 기온 칩, 리조트별 기온 수집 API.
- 제외: 정상·베이스 고도별 기온(공식 사이트 13곳 모두 단일 값만 제공), 개장일 자동 수집, 관리자 화면, DB 저장, 예보.

## 데이터 소스 조사 (2026-11-04)

### 개장일

- 공공데이터포털 `행정안전부_생활_스키장 조회서비스`는 인허가일·영업상태·주소만 제공한다. 개장일 필드가 없다.
- 국내 리조트가 개장일을 API로 공개하는 사례는 찾지 못했다. 홈페이지 공지·보도자료로만 알린다.
- 26/27 시즌 개장일은 10월 중순 기준 대부분 발표 전이다. 예년에는 11월 셋째 주부터 12월 중순 사이에 순차 개장했다.
- 결론: 사용자가 날짜를 찾아 프론트 상수로 넣는다. 발표 전 날짜는 "예상"으로 표시한다.

### 기온

공식 사이트를 쿠키·로그인 없이 GET/POST로 직접 받아 확인했다. 정상/베이스를 나눠 주는 곳은 없다.

| 리조트 | 공식 기온 | 수집 방법 | 이번 설계의 1순위 |
|---|---|---|---|
| 하이원 | 대표값 + 인근 관측소 | 웨더아이 XML `bn.weatheri.co.kr/kangwonland/weather3.php` | 공식 |
| 모나용평 | 대표값 + 강릉·대관령 | 웨더아이 HTML `hosts.weatheri.co.kr/yongpyong/weatherInfo_if.html` | 공식 |
| 무주덕유산 | 대표값 + 무주·구천동 | 웨더아이 HTML `hosts.weatheri.co.kr/muju/weather20_1.php` | 공식 |
| 웰리힐리 | 대표값 | 서버 렌더 HTML 안 `window.__NUXT__` 의 `weather` 객체 | 공식 |
| 알펜시아 | 대표값 (10분 갱신 표기) | 서버 렌더 HTML `/guide/weather-info.do` | 기상청 (구현 중 변경, 아래 참고) |
| 곤지암 | 대표값 | 서버 렌더 HTML `/main.dev` 의 `dd.temperature` | 공식 |
| 오투 | 황지동 관측값 보정 | 서버 렌더 HTML `/main.xhtml` 의 `.o2-weather-temp` | 공식 |
| 에덴밸리 | 예보값만 | JSON `WeatherController.asp?method=getWeather` | 기상청 (아래 결정 참고) |
| 휘닉스 | 대표값 | 브라우저에서 기상청 직접 호출 (HTML에 값 없음) | 기상청 |
| 오크밸리 | 대표값 (원주 격자) | 브라우저에서 기상청 직접 호출 | 기상청 |
| 지산 | 없음 | - | 기상청 |
| 엘리시안 강촌 | 없음 | - | 기상청 |
| 비발디파크 | 없음 | - | 기상청 |

- 기상청 대체: 공공데이터포털 `기상청_단기예보 조회서비스`의 초단기실황(`getUltraSrtNcst`)을 리조트 격자(nx, ny)로 조회한다. 5km 격자 실황이라 베이스 근처 값으로 본다.
- 알펜시아(구현 중 변경): 사이트 인증서 루트(OISTE WISeKey Global Root GB CA)가 JDK 신뢰 저장소에 없어 Java HTTP 클라이언트로 HTTPS 연결이 실패한다(curl은 OS 저장소를 써서 성공). 인증서 검증을 끄거나 신뢰 저장소에 루트를 추가하는 대신 기상청을 쓴다. 바로 옆 모나용평과 같은 지역이다.
- 에덴밸리: 공식 JSON은 오늘 예보값이라 실황이 아니다. "공식 우선"의 목적은 현재 기온을 보여 주는 것이므로 기상청 실황을 1순위로 쓰고, 공식 JSON은 쓰지 않는다.
- 격자 좌표는 조사 중 추정 좌표로 계산한 값이다. 구현 때 리조트 주소의 위경도로 다시 계산하고 기상청 격자 엑셀과 대조한다.

## 화면

실제 화면에 예시를 넣어 확인하고 확정했다.

### 슬로프캠 헤더 (`/resort-cam/{resort}`)

- 메타 태그 줄 끝에 `개장 D-17 (예상)` 태그. 확정일이면 `(예상)`을 뺀다. 개장일이 지나면 태그를 숨긴다.
- 헤더 오른쪽에 기온 칩: 큰 글씨 기온, 아래 작은 줄에 `하늘 상태 · HH:mm 기준 · 공식|기상청`.
- 모바일에서는 기온 칩이 제목 아래로 내려간다.
- 기온은 페이지 렌더와 분리해 브라우저에서 따로 받는다. 받는 동안은 칩 자리만 비워 두고, 실패하거나 값이 없으면 칩을 숨긴다. 캠 영상과 페이지는 기다리지 않는다.

### 메인 배너 (`/`)

- 게시판 탭 위 한 줄: `첫 개장까지 D-17 · 휘닉스파크·모나용평 11.21 예상 · 리조트별 개장일 →`. 누르면 `/resort-cam`.
- 아직 개장하지 않은 리조트 중 가장 빠른 날짜 기준. 같은 날짜는 이름을 함께 보여 준다.
- 모든 리조트가 개장하면 자동으로 숨긴다. 시즌 중 코드에서 배너를 지우는 것은 별도 작업으로 한다.

## 개장 D-DAY (프론트)

- 상수 파일 `frontend/app/lib/seasonOpening.ts`: 리조트 코드, 개장일(`YYYY-MM-DD`), 확정 여부. 날짜는 사용자가 제공한다. 빈 값이면 해당 리조트의 D-DAY를 표시하지 않는다.
- 계산은 KST 달력 날짜 차이다. 브라우저 시간대와 무관하게 `Asia/Seoul` 기준 오늘 날짜를 구해 개장일과 일수 차이를 낸다. 0이면 `D-DAY`, 음수면 숨김.
- 오늘 날짜는 `useKstToday`(`useSyncExternalStore`)로 브라우저에서만 구한다. 서버 렌더와 하이드레이션 중에는 표시하지 않아 자정 근처 서버·브라우저 날짜 차이로 하이드레이션 불일치가 나지 않는다.
- 서버 호출이 없다. 메인 배너가 백엔드 부하를 만들지 않는다.

## 기온 API

### 계약

`GET /api/v1/resort-cams/{resortCode}/weather` (공개, 인증 없음)

- 200
  ```json
  {
    "resortCode": "HIGH1",
    "temperature": -3.2,
    "sky": "맑음",
    "observedAt": "2026-11-04T17:00:00+09:00",
    "source": "OFFICIAL",
    "stale": false
  }
  ```
  - `source`: `OFFICIAL`(리조트 공식 사이트) 또는 `KMA`(기상청).
  - `observedAt`: 원본이 준 관측 시각. 원본에 시각이 없으면 수집 시각.
  - `sky`: 원본에 없으면 `null`.
  - `stale`: 갱신이 실패해 직전 값을 내보내는 중이면 `true`.
- 204: 등록된 리조트지만 지금 보여 줄 값이 없다(첫 수집 실패, 30분 상한 초과). 프론트는 칩을 숨긴다.
- 404: 활성 리조트가 아닌 코드. 기존 `ResortCameraQueryService`의 활성 리조트 목록으로 검사한다.
- 응답 헤더: `Cache-Control: public, max-age=120, stale-while-revalidate=300`.

### 수집 정책

- 요청이 들어올 때만 수집한다. 스케줄러를 두지 않는다.
- 미리 갱신(stale-while-revalidate):
  - 값의 나이가 8분 미만이면 그대로 응답한다.
  - 8분 이상 30분 미만이면 기존 값을 즉시 응답하고, 백그라운드에서 갱신을 한 번 시작한다.
  - 30분 이상이거나 값이 없으면 요청 스레드에서 수집한다. 같은 리조트의 동시 요청은 하나의 수집 결과를 같이 기다린다.
- 소스 체인: 리조트별 1순위(공식) 실패 시 기상청. 둘 다 실패하면 이전 값이 30분 안이면 `stale: true`로 응답하고, 없으면 204.
- 실패 캐시: 첫 수집이 완전히 실패한 리조트는 5분간 다시 시도하지 않고 204를 준다. 죽은 사이트를 요청마다 두드리지 않는다.
- 외부 호출 타임아웃: 연결 1초, 읽기 2초. 1순위와 대체를 합쳐도 요청 스레드 최대 대기는 약 6초.
- 백그라운드 갱신은 스레드 2개짜리 전용 풀에서 돈다. 큐가 가득 차면 갱신을 건너뛰고 기존 값을 계속 쓴다.
- 최악의 외부 호출량은 리조트당 시간당 약 7회, 13곳 합쳐 시간당 약 90회. 보는 사람이 없으면 0회.

### 구조 (Redis 확장을 고려한 분리)

캐시 정책을 Caffeine 기능(`refreshAfterWrite`)에 맡기지 않고, 서비스가 저장소와 갱신 조정을 인터페이스로 주입받아 직접 판단한다. 저장소와 조정자만 바꾸면 Redis로 옮길 수 있다.

```
ResortWeatherController
  └ ResortWeatherService            나이 판단, 동기 수집 / 백그라운드 갱신 / stale 응답
      ├ WeatherSnapshotStore        (인터페이스) get/put, 30분 만료
      │   └ InMemoryWeatherSnapshotStore   Caffeine expireAfterWrite(30m), maximumSize(32)
      ├ WeatherRefreshCoordinator   (인터페이스) 리조트별 수집 1회 보장
      │   └ LocalWeatherRefreshCoordinator  ConcurrentHashMap<String, CompletableFuture>
      ├ ResortWeatherSourceRegistry 리조트 코드 → [공식 소스, 기상청]. 기상청 키가 없으면 기상청 소스를 넣지 않음
      └ ResortWeatherSource         (인터페이스) fetch(now) → WeatherReading
          ├ OfficialWeatherSource    고정 URL + 리조트별 파서(OfficialWeatherParsers)
          │                          하이원(웨더아이 XML), 모나용평·무주(웨더아이 HTML), 웰리힐리(Nuxt 상태), 곤지암, 오투
          └ KmaNowcastSource         기상청 초단기실황. 격자는 KmaGrid가 리조트 좌표에서 계산
```

- 설계 초안은 소스 종류마다 클래스를 나눴지만, 구현에서는 "URL 하나 받아 파싱"이 공통이라 `OfficialWeatherSource` 하나와 파서 함수로 합쳤다. 파서는 순수 함수라 실제 응답 픽스처로 바로 테스트한다.
- HTTP 호출은 `WeatherHttpClient`가 맡는다. User-Agent는 서비스를 밝히는 `SnowThingBot/1.0 (+https://snowthing.org)`이다. 실패 메시지에는 예외 종류만 남긴다. 기상청 URL에 서비스 키가 들어 있어 원래 예외 메시지(URL 포함)를 로그로 옮기지 않기 위해서다.

- `WeatherSnapshot`: `reading`(기온·하늘·관측 시각·소스), `fetchedAt`. JSON으로 직렬화할 수 있는 불변 record로 만든다. Redis에 그대로 넣기 위해서다.
- 시간은 `Clock`을 주입받는다. 테스트에서 나이 판단을 고정 시각으로 검증한다.
- 캐시 키: `resort-weather:v1:{resortCode}`. 메모리 구현에서도 같은 키 규칙을 써서 Redis 전환 시 바꿀 것이 없게 한다. `v1`은 스냅샷 형식이 바뀔 때 올린다.

### Redis 전환 시 바뀌는 것

서버가 2대 이상이 되거나 재시작 후 첫 요청 지연이 문제가 될 때 전환한다.

| 부분 | 지금 (단일 서버) | Redis 전환 후 |
|---|---|---|
| `WeatherSnapshotStore` | Caffeine 메모리 | `RedisWeatherSnapshotStore`: `SET key json EX 1800`, `GET key` |
| `WeatherRefreshCoordinator` | JVM 안 `CompletableFuture` 맵 | `RedisWeatherRefreshCoordinator`: `SET lock:key token NX PX 10000`으로 서버 간 한 대만 수집, 끝나면 토큰 비교 후 삭제 |
| 락을 못 잡은 서버의 동기 요청 | (해당 없음) | 최대 3초 동안 저장소를 다시 읽고, 그래도 없으면 204 |
| 서비스·소스·컨트롤러·API 계약 | - | 변경 없음 |
| 선택 | - | `snowthing.weather.store=memory|redis` 설정으로 구현체 선택 |

- 선택 사항: 요청마다 Redis를 왕복하는 비용이 문제가 되면, Redis 앞에 1분짜리 Caffeine 1차 캐시를 둔다(2단 캐시). 이것도 `WeatherSnapshotStore` 데코레이터로 추가해 서비스는 바뀌지 않는다.
- 프로젝트에는 이미 `spring-boot-starter-data-redis`와 Redis가 있다. 전환에 새 인프라가 필요 없다.

## 보안·운영

- 외부 URL은 코드 상수다. 요청 값으로 URL을 만들지 않는다(SSRF 차단). `resortCode`는 활성 리조트 목록과 대조한 뒤에만 쓴다.
- 기상청 서비스 키는 환경변수 `SNOWTHING_KMA_SERVICE_KEY`로 받는다. 응답·로그에 남기지 않는다. 키가 없으면 기상청 소스를 비활성화하고 해당 리조트는 204.
- 리조트 사이트에는 리조트당 시간당 약 7회 이하로 요청하고, robots.txt가 막는 경로는 쓰지 않는다. 조사 시점 기준으로 사용하는 경로는 모두 허용되어 있다.
- 공식 사이트 구조가 바뀌면 그 리조트는 기상청으로 자동 대체된다. 파싱 실패는 리조트별로 WARN 로그를 남긴다. 같은 리조트의 실패 로그는 실패 캐시 5분 동안 한 번만 찍힌다.
- `SecurityConfig` 공개 경로와 `BotDelegationAuthenticationFilter` 허용 패턴에 새 경로를 추가해야 한다(기존 목록 경로는 정확히 일치하는 패턴이다).

## 테스트

- 소스별 파서: 조사 때 받은 실제 응답을 테스트 리소스로 저장하고 기온·하늘·시각 추출을 검증한다. 빈 값, `-` 자리표시, 깨진 마크업은 실패로 처리하는지 확인한다.
- 서비스: 고정 `Clock`과 동기 실행기로 나이 구간(8분 미만, 8~30분, 30분 이상), 1순위 실패 후 대체, 둘 다 실패 시 stale/204, 실패 캐시 5분, 동시 요청 1회 수집을 검증한다.
- 컨트롤러: 200/204/404와 `Cache-Control` 헤더, 인증 없이 접근 가능한지.
- 프론트: D-DAY 계산(KST 자정 경계, 당일, 지난 날, 빈 날짜), `npm run lint`, `npm run build`, 브라우저에서 헤더·배너·모바일·다크모드 확인.

## 검토한 대안

결정 과정과 버린 선택지는 [ADR-1901](ADR-1901-weather-collection-and-cache.md)에 정리했다.

## 사용자 결정 필요

- 리조트별 26/27 개장일과 확정 여부 (사용자가 찾아서 제공).
