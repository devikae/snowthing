# 이메일 인증 구현 계획

## 1단계. 브랜치와 계약

- `origin/main` 기준 `feature/email-verification-password-reset` 생성
- 요구사항·ERD·API·보안·SES 운영 문서 확정
- ErrorCode와 DTO 계약 테스트 작성

## 2단계. 데이터베이스

- `013_migration_email_verification.sql` 작성
- `member.email_verified_at` 추가
- `email_verification` 테이블·제약조건·인덱스 추가
- 운영 마이그레이션 검증 스크립트 범위를 `013`까지 확장
- Docker MySQL 8 최초 적용·재실행 검증

## 3단계. 인증 도메인

- 이메일 정규화 Value Object 또는 전용 컴포넌트
- 목적·상태 Enum
- 인증번호와 토큰 HMAC 컴포넌트
- Entity·Repository·시간 주입 가능한 서비스
- 재전송·시도 횟수·만료·소비 상태 전이 테스트

## 4단계. SES 어댑터

- 메일 발송 포트와 SES v2 구현체 분리
- 가입·비밀번호 재설정 HTML/Text 템플릿
- timeout과 오류 매핑
- Fake 구현과 SDK 요청 계약 테스트

## 5단계. 회원가입 연결

- 이메일 사용 가능 여부 API
- 가입 인증번호 발송·확인 API
- 회원가입 DTO에 일회용 토큰 추가
- 회원 생성과 토큰 소비를 같은 트랜잭션으로 처리
- 신규 회원 `email_verified_at` 저장

## 6단계. 비밀번호 재설정과 세션 폐기

- 계정 존재 여부 비노출 요청 API
- 재설정 번호 확인과 토큰 발급
- 비밀번호 변경·토큰 소비·기존 회원 인증 시각 기록
- Security 계층의 회원별 세션 등록·만료
- 라이브톡 연결 종료 연동

## 7단계. 프런트엔드

- 회원가입 이메일 중복 확인·번호 발송·확인 단계
- 이메일 변경 시 인증 상태 초기화
- 재전송 타이머와 만료 안내
- 로그인 화면 비밀번호 찾기 링크
- 비밀번호 재설정 요청·인증·변경 화면
- 가입 완료 자동 로그인 회귀 확인

## 8단계. 운영 설정과 검증

- SES 도메인 Identity와 Easy DKIM
- Cloudflare DNS 레코드
- SES Production access 요청
- EC2 역할 최소 권한
- Configuration Set·억제 목록·CloudWatch 경보
- 전체 테스트와 운영 체크리스트
- `docs/project/work.md` 진행 기록

## 재검토 조건

- 발송 요청량이 늘어 API 응답 지연이 문제가 됨
- SES 장애 중에도 발송 작업을 유실 없이 보관해야 함
- 여러 서버가 인증 요청과 IP 제한을 공유해야 함
- 배달·반송 이벤트를 회원 상태에 반영해야 함

위 조건이 생기면 Redis 기반 제한기, Transactional Outbox, SNS/SQS와 Inbox를 순서대로 검토합니다.
