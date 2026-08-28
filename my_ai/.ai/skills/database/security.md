# Database Security Skill

## 권한

- 애플리케이션 DB 계정에 불필요한 관리자 권한을 주지 않는다.
- 최소 권한 원칙을 적용한다.
- 운영 DB 계정과 개발 DB 계정을 분리한다.

## Injection

- 문자열 연결로 SQL을 생성하지 않는다.
- Prepared Statement / Parameter Binding을 사용한다.
- 동적 ORDER BY, 컬럼명 등 parameter binding이 어려운 부분은 whitelist로 제한한다.

## 민감정보

- 비밀번호를 평문 저장하지 않는다.
- 민감 개인정보의 암호화/마스킹/접근통제를 검토한다.
- 로그와 slow query에 민감정보가 노출될 수 있는지 확인한다.
