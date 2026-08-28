# Database Migration & Backup Skill

## Migration

- 운영 DB 스키마를 수동 변경한 뒤 코드만 맞추는 방식을 피한다.
- Flyway/Liquibase 등 migration 도구 또는 프로젝트 표준을 따른다.
- migration은 가능한 한 재현 가능하고 순서가 명확해야 한다.
- 대용량 테이블 ALTER는 lock/시간/디스크 사용량을 검토한다.
- NOT NULL 컬럼 추가, default 변경, 컬럼 타입 변경은 기존 데이터 영향을 확인한다.

## 배포 호환성

Zero-downtime가 필요한 경우 Expand → Migrate → Contract 형태의 단계적 변경을 검토한다.

## Backup/Restore

- Backup이 존재한다는 것만으로 충분하지 않다. 실제 Restore 가능성을 고려한다.
- RPO/RTO 요구사항에 따라 백업 주기와 복구 방식을 결정한다.
- 개인정보 포함 백업의 접근권한과 암호화를 검토한다.
