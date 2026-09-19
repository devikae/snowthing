# Sprint 06 배포 결과

## 제출 정보

- 배포 브랜치: `deploy/aws-ec2`
- 운영 URL: `https://snowthing.org`
- ECR 전환 커밋: `eaf7acc669f1c9ae0f82dee2ca07a65bfdfb4fbb`
- 로그인 UI 및 수동 롤백 추가 커밋: `bf94203015a6edf6d0b0a977af5e4afb2c9c5710`
- 배포 PR: 아직 만들지 않았습니다. 현재 결과를 정리한 뒤 `deploy/aws-ec2`에서 `main`으로 PR을 열어야 합니다.

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
