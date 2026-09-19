# Sprint 06 배포 결과

## 제출 정보

- 배포 브랜치: `deploy/aws-ec2`
- 운영 URL: `https://snowthing.org`
- ECR 전환 커밋: `eaf7acc669f1c9ae0f82dee2ca07a65bfdfb4fbb`
- 로그인 UI 및 수동 롤백 추가 커밋: `bf94203015a6edf6d0b0a977af5e4afb2c9c5710`
- 배포 PR: [#19 aws-ec2](https://github.com/devikae/snowthing/pull/19)

## 실제 배포 이미지

| 서비스 | 커밋 | ECR 태그 | 배포한 digest |
|---|---|---|---|
| 프런트 초기 버전 | `eaf7acc...` | `release-eaf7acc669f1c9ae0f82dee2ca07a65bfdfb4fbb` | `sha256:acba5de24d57eee6fa56da4845db6d59fc1b0a43e7b8d9867dd4fd1586b6798d` |
| 백엔드 | `eaf7acc...` | `release-eaf7acc669f1c9ae0f82dee2ca07a65bfdfb4fbb` | `sha256:f47b877a6aac0e3eb7b973bba78999a765e3415c7f7fda6c15a82834fca26d9c` |
| 프런트 새 로그인 UI | `bf94203...` | `release-bf94203015a6edf6d0b0a977af5e4afb2c9c5710` | `sha256:91f7b9d8027704d895ff697e37fea8329514cf684d619242f77e229a59946ffb` |

태그는 사람이 커밋을 찾을 때 사용하고, EC2에는 변경할 수 없는 digest 주소를 전달했습니다. 같은 태그가 가리키는 대상을 나중에 바꾸는 방식으로는 배포하지 않았습니다.

## Actions 실행 결과

- [초기 프런트 배포](https://github.com/devikae/snowthing/actions/runs/35076355024): CI 29초, 빌드·push·배포 1분 59초
- [초기 백엔드 배포](https://github.com/devikae/snowthing/actions/runs/35076355176): CI 2분 55초, 빌드·push·배포 2분 6초
- [새 로그인 UI 배포](https://github.com/devikae/snowthing/actions/runs/35079283676): CI 29초, 빌드·push·배포 1분 52초
- [이전 프런트 digest 롤백](https://github.com/devikae/snowthing/actions/runs/35079786140): 전체 27초, digest 조회와 SSM 배포 19초
- [새 프런트 digest 재배포](https://github.com/devikae/snowthing/actions/runs/35081931883): 전체 31초, digest 조회와 SSM 배포 21초

## 확인한 결과

- 새 로그인 UI 배포 후 `/login`에서 새 문구를 확인했습니다.
- 이전 digest로 롤백한 뒤 `/login`이 이전 UI로 바뀌었습니다.
- 새 digest를 다시 배포한 뒤 새 UI가 복구됐습니다.
- 롤백과 재배포 뒤 `GET /api/v1/posts`, `GET /api/v1/master/resorts`가 모두 `200 OK`를 반환했습니다.
- 프런트와 백엔드 컨테이너는 EC2에서 빌드하지 않았습니다. EC2는 ECR 이미지를 pull해 실행했습니다.

## 잔여 이슈

1. `deploy/aws-ec2` 변경을 `main`에 병합하기 전이라 새 수동 관리 워크플로를 기본 브랜치에서 직접 실행할 수 없습니다. PR 검토와 병합이 필요합니다.
2. RDS 수동 스냅샷을 별도 DB `snowthing-db-restore-test`로 복원하고 SQL로 레코드를 대조했습니다. 다만 복원 DB 전용 읽기 계정과 검증 앱을 연결한 증거는 이번 자료에 없으므로, 미션 문구 그대로의 "읽기 전용 검증 앱" 확인은 별도 증거가 필요합니다.
3. 최초 프런트와 백엔드 배포 사이에 API가 잠시 `502 Bad Gateway`를 반환했습니다. 당시 로그가 충분하지 않아 원인을 단정하지 않았습니다. 이후 배포 실패 시 Docker 상태·제한된 inspect 결과·컨테이너 로그·Nginx error log를 같은 UTC 시각으로 저장하도록 보강했습니다.
4. 복원 검증용 RDS의 삭제 여부는 제출 전 AWS 콘솔에서 확인해야 합니다. 남아 있다면 불필요한 과금을 막기 위해 삭제하되, 제출에 필요한 스냅샷의 보존 여부는 먼저 결정합니다.

## 추가 완료 항목

- 공식 액션을 Node.js 24 기반 최신 주요 버전으로 갱신한 뒤 프런트·백엔드 CI와 배포를 다시 통과했습니다.
- 백엔드 `stable-3ecdc50...` → `sha256:dec23cf6574e4fecddfb32946ce88a924afbc3561f31bb6069e55d0b714c9016`
- 프런트 `stable-3ecdc50...` → `sha256:3bdfa20b2d63f9fba13296f3bd3daba0662cec87f3e2e112df0396a38ae7b2aa`
- stable 지정 후 로그인·게시글·리조트 API `200 OK`를 확인했습니다.
- [백엔드 stable 지정](https://github.com/devikae/snowthing/actions/runs/35087127962), [프런트 stable 지정](https://github.com/devikae/snowthing/actions/runs/35087130593)

## RDS 스냅샷 복원 결과

- 2026-09-15 19:33 KST의 원본 DB에는 `TEST`와 `[DB 복구 테스트] 백업 시점 이전 데이터`가 있었습니다.
- 이 상태에서 수동 스냅샷을 만든 뒤 19:47 KST에 `[DB 복구 테스트] 백업 시점 이후 데이터`를 추가했습니다.
- 스냅샷을 새 인스턴스 `snowthing-db-restore-test`로 복원했습니다.
- 복원 DB에는 스냅샷 이전의 두 행만 있었고, 스냅샷 이후 추가한 행은 없었습니다. 운영 DB를 덮어쓰지 않고 스냅샷 시점의 데이터를 별도 DB에서 복구한 결과입니다.
- 증거 이미지는 [`evidence/rds-restore`](./evidence/rds-restore/)에 정리했습니다.

## 이미지 CDN 변경 결과

- 이미지 전달 구조 변경 커밋: `1466217f15d3d04ece095c044f3030cccbfd0192`
- 썸네일 생성·목록 URL 보정 커밋: `86888ff`
- 이미지 도메인: `https://images.snowthing.org`
- CloudFront 원본: Block Public Access가 적용된 비공개 S3 버킷
- S3 접근: CloudFront OAC와 EC2 IAM Role만 허용

기존 백엔드의 `GET /api/v1/images/**` 바이트 중계 API를 제거했습니다. 업로드 API는 CloudFront URL과 객체 키를 반환하고, Client는 CloudFront에서 이미지를 직접 조회합니다. 리뷰에서 제안한 S3 공개 URL은 사용하지 않았습니다. S3를 공개하면 향후 회원 전용 중고장터 이미지와 접근 정책이 충돌하므로, 비공개 S3를 원본으로 두고 CloudFront만 공개 조회 지점으로 사용했습니다.

새 게시글 이미지는 다음 두 객체로 저장됩니다.

- 원본: `public/posts/originals/{UUID}.{확장자}`
- 목록용 썸네일: `public/posts/thumbnails/{UUID}.jpg`

썸네일은 최대 320×320, 비율 유지, JPEG 품질 0.8로 생성합니다. 게시글 DB에는 원본 객체 키만 저장하고 상세 응답은 원본 CloudFront URL, 목록 응답은 썸네일 CloudFront URL을 반환합니다. 기존 `public/posts/{UUID}.{확장자}` 데이터는 원본 URL로 응답해 호환성을 유지합니다.

[이미지 CDN 최초 배포](https://github.com/devikae/snowthing/actions/runs/35422759822)와 [썸네일 배포](https://github.com/devikae/snowthing/actions/runs/35424121640)가 성공했습니다. 운영 업로드 결과 원본 PNG는 675×128, 11,976바이트였고 썸네일 JPEG는 320×61, 6,117바이트였습니다. 두 CloudFront URL은 200, 두 S3 직접 URL은 403이었습니다.

### 이미지 관련 잔여 이슈

1. 업로드 후 게시글 등록을 취소하거나 원본·썸네일 중 한쪽 저장만 성공하면 고아 객체가 남을 수 있습니다. 임시 업로드 prefix와 수명 주기 정리 또는 게시글 연결 후 승격 절차가 필요합니다.
2. `public/posts/`는 공개 조회 경로입니다. 회원 전용 중고장터 이미지는 별도 prefix로 분리하고 CloudFront Signed URL/Cookie 등 별도 권한 정책을 적용해야 합니다.
3. UUID 객체에는 장기 immutable 캐시를 적용했습니다. 같은 키를 덮어쓰지 말고 변경 시 새 UUID를 사용해야 합니다.
