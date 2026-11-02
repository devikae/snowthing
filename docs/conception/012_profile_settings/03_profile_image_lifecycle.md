# 프로필 이미지 수명주기와 리뷰 보완

## 저장과 DB 정합성

기존 POST /api/v1/images/profile 및 DELETE /api/v1/members/me/profile-image 계약을 유지한다. 전자는 인증된 본인 계정만 변경하며 반환 DTO에 공개 URL과 키를 제공한다. 컨트롤러는 ProfileImageLifecycleService를 호출하며 Repository를 직접 사용하지 않는다.

021의 profile_image_cleanup은 image_key PK, image_url, not_before, attempts와 예정 시각 인덱스를 갖는다. 업로드 전에 새 UUID 키와 24시간 뒤 정리할 의도를 별도 DB 트랜잭션으로 먼저 커밋한다. 파일 저장 후 회원 행을 잠그고 새 URL 연결·새 파일 정리 예약 해제·이전 파일 정리 예약을 하나의 DB 트랜잭션에서 처리한다. 프로세스가 파일 저장 중 죽어도 예약은 남는다. DB 갱신이 실패하면 새 파일 정리를 즉시 시도하고 실패한 작업을 보존한다. 회원과 파일 메타데이터 사이 외래 키를 두지 않아 계정 정리가 작업을 함께 지워 버리지 않는다.

삭제 작업은 분당 최대 50건을 조회하고 각 작업 행을 잠가 다른 인스턴스의 중복 처리를 제어한다. 현재 member.profile_image_url에 참조된 파일은 삭제하지 않는다. 실패는 attempts와 다음 시각을 기록하고 1분부터 최대 60분 간격으로 재시도한다. 배치 외부 저장소 호출 중에는 작업 행 잠금을 유지하지만 회원 행은 잠그지 않는다. 작업 수를 제한해 접속 풀 점유를 제한한다. 오류 유형과 재시도 수를 로그로 남기며 운영에서 지연 작업 수와 최소 not_before를 감시해야 한다.

## S3와 CDN

서버가 발급한 public/profiles/UUID.jpg 형식 및 설정된 CloudFront URL에 속하는 파일만 정리한다. 외부 소셜 프로필 URL과 일반 게시글 이미지는 자동 삭제하지 않는다. 이전 파일은 S3 삭제 후 해당 경로의 CloudFront invalidation을 요청하고, 요청이 수락된 뒤 작업을 완료한다. caller reference를 파일 키에 고정해 재시도를 멱등 처리한다. 무효화 완료는 CloudFront의 비동기 처리 시간에 따른다.

새 프로필 업로드의 Cache-Control은 no-store이며 게시글의 immutable 정책을 그대로 쓰지 않는다. 운영 CloudFront 프로필 경로의 minimum TTL을 0으로 설정해야 한다. 기존에 내려받은 사본이나 이미 브라우저에 장기 캐시된 데이터는 서버가 회수할 수 없다.

운영 환경변수 SNOWTHING_CLOUDFRONT_DISTRIBUTION_ID 및 해당 배포의 cloudfront:CreateInvalidation, 버킷의 해당 prefix에 대한 s3:DeleteObject 권한이 필요하다. 값을 저장소에 넣거나 이번 로컬 작업에서 AWS 정책을 변경하지 않는다. 설정/권한이 없으면 S3 삭제 또는 CDN 정리 실패를 작업으로 남겨 재시도하며 캐시까지 즉시 삭제됐다고 주장하지 않는다.

## 가입 문서와 공유 한도

가입 문서는 shared/registration-policies의 버전 파일을 불변 스냅샷으로 보존한다. current.json이 선택하는 동일한 파일을 Next.js와 백엔드 리소스에 포함한다. 가입 화면은 버전 URL을 표시하고 제출 버전을 전달하며 서버는 현재 문서와 일치하는지 확인한 뒤 서버 버전을 동의 기록에 저장한다. 기존 파일 내용을 덮어쓰지 않고 새 버전 파일·manifest·프런트 레지스트리를 같이 갱신한다. 현재 문서는 운영 사실 확인이 남은 초안이며 법률 준수 확정 문서가 아니다.

022 request_budget_window는 budget_key PK, used_count, expires_at_millis와 만료 인덱스다. MySQL 행 잠금과 DB 시계로 봇의 시간/일 예산을 동시에 확인한다. 비밀번호 변경도 같은 저장소에 계정별 15분 창을 유지한다. Redis는 현재 서비스에서 비활성화되어 있으므로 새 런타임 인프라를 추가하지 않는다. 만료 창은 시간당 최대 500건을 정리한다. 예산 확인 실패는 제한을 우회하지 않으며 요청자 오류로 처리한다.

## 빌드와 배포 순서

두 Dockerfile은 이제 저장소 루트를 빌드 컨텍스트로 사용한다: docker build -f frontend/Dockerfile . 및 docker build -f backend/Dockerfile . . shared를 함께 복사하며 .dockerignore가 비밀 파일·로컬 산출물을 제외한다. 관련 배포 워크플로는 shared 변경에도 빌드한다. 017·018·021·022를 운영 마이그레이션에 포함하며 동호회의 019·020은 계속 제외한다. Docker 이미지 빌드 검증은 로컬에서만 수행하고 배포하지 않는다.
