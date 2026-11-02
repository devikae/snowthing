# 이미지 비용 제어 운영 확인

- CloudFront: EJ0LUYNZLPFPQ, images.snowthing.org. public/profiles/* 경로의 캐시 비활성 정책은 전파 완료(Deployed)를 확인했다.
- 프로필 경로의 CloudFront S3 읽기 정책, EC2 역할의 프로필 삭제·배포 무효화 권한을 추가했다.
- 일반 사진 경로 public/posts/*에는 EC2 역할의 삭제·태그 권한을 추가했다. 실제 EC2 역할로 테스트 객체를 업로드·태그·삭제했고 테스트 객체 삭제까지 확인했다.
- S3 수명주기 SnowThingConfirmedUnusedImages는 public/posts/ 아래 snowthing-cleanup=ready 태그에만 1일 만료를 적용한다. 정상 사진에는 이 태그를 붙이지 않는다.
- 기존 월 35 USD 예산 Snowthing Monthly Cost Guard는 유지했고 실제 비용 50%·80%·100% 알림에 82snowthing@gmail.com 수신자를 추가했다. 시험 메일은 보내지 않았다.
- 운영 prod.env에 CloudFront 배포 ID를 저장했으며 실행 중인 백엔드는 재생성하지 않았다. 코드 배포와 023·024 마이그레이션은 아직 수행하지 않았다.
- 기존 이미지 조사는 6객체·158,555바이트 중 미참조 후보 3객체·30,069바이트다. 삭제 게시글·본문 참조를 포함했다. 후보는 조사 스냅샷이며 실제 삭제는 수행하지 않았다.

로컬 전체 백엔드 테스트는 445건, 실패·오류 0, 기존 조건부 skip 1건으로 통과했다. 이후의 마지막 보완은 관련 테스트로 재검증한다. 프런트 린트·프로덕션 빌드와 임시 DB의 001~018+021~024 체인·재실행도 통과했다. 실제 사용자 사진을 업로드하거나 삭제하지 않았다.

AWS 설정 백업은 저장소 밖의 로컬 임시 디렉터리 snowthing-storage-backup-ptn2xodt, snowthing-image-cleanup-backup-ra67sxhy 및 snowthing-image-cleanup-backup-id4bgkdb에 보관했다. 복구 시 이후 타 작업의 정책 변경을 먼저 대조해야 한다.
