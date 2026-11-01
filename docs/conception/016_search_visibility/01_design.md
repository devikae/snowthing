# 눈팅 사이트명·스키장 웹캠 검색 기반 (2026-11-01)

## 요청과 확인된 운영 상태

사용자는 한글 ‘눈팅’으로 구글/네이버에서 사이트를 찾고, 구글의 ‘스키장 웹캠’ 검색에서도 발견할 수 있기를 요청했다. 운영 https://snowthing.org/ 를 읽기 전용으로 확인했을 때 HTTP 200, 제목은 `Snowthing - Snowboard Community`였고 WebSite JSON-LD와 canonical은 없었다. `/sitemap.xml`은 404였다. `/robots.txt`는 Cloudflare의 관리 규칙이 있었으며 search=yes 및 일반 수집 허용이 확인됐다. 홈페이지에 Google/Naver 소유확인 메타태그는 없었지만 DNS 등의 다른 소유확인 여부는 이 결과만으로 판단하지 않는다.

## 코드 범위

- 공통 사이트명 ‘눈팅’, 영문명 SnowThing을 제목/설명/Open Graph/홈페이지의 WebSite 구조화 데이터에 연결한다. 실제 사용하지 않는 별칭은 만들지 않는다. 구조화 데이터는 홈페이지에 한 번만 렌더링하며 가짜 검색 기능이나 미검증 동영상 정보를 넣지 않는다.
- 기존 메인 화면/클라이언트 동작은 HomePageClient로 보존하고 서버 page에서 canonical과 JSON-LD를 제공한다. 다른 페이지가 홈페이지 canonical을 상속하지 않도록 홈페이지 전용 메타데이터에 둔다.
- 웹캠 모음은 ‘스키장 웹캠·슬로프캠’ 제목/설명과 본문 제목을 사용한다. `/resort-cam`의 클라이언트 이동 문구를 실제 기본 리조트 화면의 서버 렌더링으로 대체한다. 첫 리조트는 기존 사용자 지정 순서의 곤지암이다. URL은 `/resort-cam`으로 유지한다.
- 리조트/개별 카메라 페이지는 실제 마스터 이름·지역·카메라 이름을 사용한 고유 제목/설명/canonical을 제공한다. 없는 경로는 notFound로 제외한다. 메타데이터와 레이아웃은 요청 내 동일 카메라 조회를 React cache로 공유한다.
- sitemap은 공개 경로 8개와 리조트 13개(현재 총 21개)를 제공한다. 인증 페이지·작성/수정 폼·회원 전용 장터·이번 배포 제외 대상인 동호회는 넣지 않는다. 원본 영상 URL이나 회원정보는 XML에 포함하지 않는다. 내부 글을 재활용해 공개 검색 문서로 만들지 않는다.
- robots.txt는 공개 페이지와 렌더링에 필요한 공개 API/리소스를 수집 가능하게 두고 스트림 중계 경로만 제외한다. 로그인·가입·내 정보·비밀번호 재설정·장터·작성/수정 폼에는 noindex를 제공한다. 동호회 전용 경로는 이번 PR에서 제외하여 404이며, 메뉴와 sitemap에도 포함하지 않는다. robots는 인증/권한 검사를 대체하지 않는다.
- 도구 페이지의 한글 사이트명과 현재 입력/출력에 맞는 검색 설명도 정리한다. 이미 제거한 라이딩 성향/허리폭 입력을 검색 설명에 남기지 않는다.

## 소유확인과 배포

소유확인 코드를 임의로 만들거나 등록 완료로 보고하지 않는다. Google Search Console/네이버 서치어드바이저가 발급한 실제 코드가 있다면 `GOOGLE_SITE_VERIFICATION`, `NAVER_SITE_VERIFICATION` 저장소 변수로 넣는다. 로컬 Next는 frontend/.env.local에서 읽는다. 값이 없으면 메타태그를 만들지 않는다.

배포 워크플로와 Docker 빌드/런타임에 두 공개 검증값을 전달한다. 값만 변경해도 이전 이미지를 잘못 재사용하지 않도록 설정 지문에 포함한다. 실제 소유확인·사이트맵 제출·재수집 요청은 운영 반영 이후 해당 서비스에서 확인해야 한다. 이 로컬 작업에서는 운영 배포, 외부 계정 등록/제출, DNS/Cloudflare 설정 변경을 하지 않는다. 동호회는 이번 배포에서 제외해야 하므로 현재 작업 폴더 전체를 그대로 배포하면 안 된다.

## 검증과 의미

- scripts/seo-check.cjs: JavaScript를 실행하지 않은 서버 HTML의 사이트명/구조화 데이터/canonical, 웹캠 첫 화면, 리조트 13곳의 중복되지 않은 제목, 개별 카메라 메타데이터, 공개 sitemap 21개, 비공개 noindex 및 robots 링크를 확인했다.
- 실제 브라우저에서 JavaScript 비활성 상태에서도 카메라 이름/리조트 탐색이 있는지, 기본 곤지암 순서와 `/resort-cam` URL 유지, 390px 다크 가로 넘침 없음, 홈페이지 JSON-LD를 확인했다. 증거 `%TEMP%/snowthing-seo-browser-4dTZJS/`.
- 전체 ESLint와 Next production build 통과. 운영 검색결과 노출/순위를 검증한 것으로 보고하지 않는다. 검색엔진의 수집/색인/순위 결정은 별개이며 ‘눈팅’처럼 일반적으로 쓰이는 이름과 ‘스키장 웹캠’ 같은 검색어의 상위 노출을 보장하지 않는다.

## 공식 근거

- Google 사이트명: https://developers.google.com/search/docs/appearance/site-names
- 네이버 제목/설명 작성: https://searchadvisor.naver.com/guide/content-basic
- 네이버 사이트 등록/소유확인: https://searchadvisor.naver.com/guide/faq-start-register
- 네이버 robots/sitemap: https://searchadvisor.naver.com/guide/seo-basic-robots
