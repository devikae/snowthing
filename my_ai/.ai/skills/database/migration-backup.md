# Database Migration & Backup Skill

## Migration

- 운영 DB 스키마를 수동 변경한 뒤 코드만 맞추는 방식을 피한다.
- Flyway/Liquibase 등 migration 도구 또는 프로젝트 표준을 따른다.
- migration은 가능한 한 재현 가능하고 순서가 명확해야 한다.
- 대용량 테이블 ALTER는 lock/시간/디스크 사용량을 검토한다.
- NOT NULL 컬럼 추가, default 변경, 컬럼 타입 변경은 기존 데이터 영향을 확인한다.
- `ddl-auto: validate`는 migration을 실행하지 않고 현재 schema가 entity와 맞는지만 검사한다는 점을 명시한다.
- 초기 schema SQL만 저장해 두고 배포와 연결하지 않은 상태를 migration 자동화로 간주하지 않는다.
- 일반 애플리케이션 계정에 DDL 권한을 추가해 migration을 해결하지 않는다. 별도 migration 계정 또는 job을 사용한다.
- migration job이 성공하고 version·checksum이 기록된 경우에만 애플리케이션 배포가 진행되도록 선행 관계를 둔다.
- 기존 운영 DB에 도구를 처음 도입할 때 baseline version과 기존 schema 대조 없이 자동 실행하지 않는다.

## 배포 호환성

Zero-downtime가 필요한 경우 Expand → Migrate → Contract 형태의 단계적 변경을 검토한다.

- Expand 단계에서는 구·신 이미지가 함께 읽고 쓸 수 있는 additive 변경만 적용한다.
- 데이터 이관과 새 코드 전환을 확인한 뒤 별도 배포에서 Contract 변경을 수행한다.
- 이전 이미지와 새 schema가 호환되지 않으면 자동 이미지 롤백을 중단한다.
- migration 실패 시 앱 배포를 차단하고 DB를 자동으로 역방향 변경하지 않는다. 복원 또는 forward fix 절차를 따른다.

## Backup/Restore

- Backup이 존재한다는 것만으로 충분하지 않다. 실제 Restore 가능성을 고려한다.
- RPO/RTO 요구사항에 따라 백업 주기와 복구 방식을 결정한다.
- 개인정보 포함 백업의 접근권한과 암호화를 검토한다.
