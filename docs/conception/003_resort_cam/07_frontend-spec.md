# 리조트캠 프런트엔드 명세

## 1. 라우트

```text
/resort-cam                                  기본 리조트로 이동
/resort-cam/[resortCode]                    리조트 전체 보기
/resort-cam/[resortCode]/[cameraCode]       개별 카메라 보기
/resort                                      /resort-cam 리다이렉트
```

경로 코드는 소문자를 사용하고 API 코드와 비교할 때 대문자로 정규화합니다.

## 2. 레이아웃

SnowThing 공통 최대 폭과 색상 토큰을 사용합니다. 리조트 선택은 콘텐츠 상단의 가로 스크롤 탐색으로 제공하며, 고정 사이드바나 별도 대시보드 스타일을 사용하지 않습니다.

전체 보기는 반응형 1~3열 그리드, 개별 보기는 단일 대형 플레이어를 사용합니다. 재생 가능한 소스는 영상 중심 카드로 표시하고, `EXTERNAL_LINK`는 검은 빈 플레이어 대신 공식 페이지 이동 목적이 명확한 간결한 카드로 표시합니다. 모바일에서도 동일한 탐색 구조를 유지하고 가로 스크롤로 리조트를 선택합니다.

## 3. 데이터 흐름

1. 리조트캠 레이아웃에서 공개 API를 한 번 조회합니다.
2. 메타데이터를 하위 라우트가 공유합니다.
3. 경로 코드로 리조트와 카메라를 선택합니다.
4. 라우트 전환은 Next.js `Link`를 사용합니다.
5. 메타데이터는 유지하고 실제 플레이어만 교체합니다.

## 4. 플레이어 어댑터

```text
ResortCameraPlayer
├─ HlsCameraPlayer
├─ YoutubeCameraPlayer
├─ IframeCameraPlayer
└─ ExternalCameraCard
```

HLS는 Safari의 native HLS를 우선하고 그 외 지원 브라우저에서 `hls.js`를 사용합니다. 각 어댑터는 `mount`, `play`, `pause`, `destroy`, `retry` 생명주기를 동일한 상위 컴포넌트 계약으로 제공합니다.

## 5. 재생 정책

- PC: 30% 이상 보이는 카드가 자동재생 후보
- 자동재생은 `muted`, `playsInline`
- 후보 중 화면 위쪽 순서로 최대 6개 재생
- 화면 이탈 시 pause
- 모바일: 카드 선택 전 플레이어를 만들지 않음
- 개별 보기: 해당 플레이어 하나만 생성

## 6. 상태와 오류

`idle`, `loading`, `playing`, `paused`, `error`, `external-only` 상태를 사용합니다. 오류 카드에는 카메라명, 오류 안내, 재시도, 원본 링크를 유지하여 레이아웃 높이가 갑자기 줄지 않게 합니다.

## 7. 접근성

- 리조트·카메라 메뉴는 키보드 이동 가능
- 활성 항목에 `aria-current`
- 드로어에 포커스 이동과 Escape 닫기
- 플레이어 제목을 제공하고 아이콘 단독 버튼에 `aria-label`
- 자동재생은 음소거 상태로만 허용
