# Snowthing AI 작업 지침

이 파일은 눈팅 프로젝트의 입구다. 공통 규칙은 아래 파일에 있고, 이 파일에는 눈팅에만 해당하는 규칙을 둔다.

@my_ai/rules/RULES.md

- 위 `@` 줄을 자동으로 따라가지 않는 도구는 작업 전에 `my_ai/rules/RULES.md`를 직접 읽는다.
- `my_ai/`는 `devikae/ai-skills` 저장소의 submodule이다. 비어 있으면 `git submodule update --init`을 먼저 실행한다.
- 분야별 문서(`my_ai/rules/backend/`, `database/`, `frontend/` 등)는 자동으로 읽지 않는다. `RULES.md` §12에 따라 작업에 맞는 것만 연다.
- 공통 규칙과 이 파일이 충돌하면 이 파일을 따른다.

## 작업 파이프라인

| 단계 | 할 일 | 스킬 |
|---|---|---|
| 1 | 태스크 정의. 새 기능·스펙 변경이면 설계 | `brainstorming` |
| 2 | Linear 티켓 작성 | `linear-ticket` |
| 3 | 티켓으로 작업 지시 → 수정 계획 승인 (한 번) | `my_ai/rules/workflow/planning-and-approval.md` |
| 4 | 진행 댓글 🚀 🔨 ⚠️ | `linear-comment` |
| 5 | 포트폴리오용 버그만 GitHub 이슈도 생성 | `linear-ticket` |
| 6 | 셀프 리뷰 → PR 생성 | `github-pr` |
| 7~8 | CodeRabbit 리뷰 → 지적사항 반영 | `github-pr` |
| 9 | 머지 (사용자 확인 후) | `github-pr` |
| 10 | ✅ 완료 댓글 | `linear-comment` |
| 11 | Notion TIL | `notion-til` |

- 계획이 승인되면 중간에 묻지 않고 끝까지 진행한다. 멈추는 기준은 `RULES.md` §3 "멈추고 확인받는 작업"이다.
- AI 규칙·스킬 정리 같은 준비 작업은 티켓 없이 진행한다.

## Git

- 브랜치: `ika-12-short-desc` (티켓번호가 맨 앞, 소문자). 작업 종류는 Linear 라벨로 구분한다.
- PR 제목: `[IKA-12] 제목`, 본문 끝에 `Closes IKA-12`.
- main에 직접 push하지 않는다. 머지는 사용자 확인 후 merge commit으로 한다.

## 설계 문서

- 새 기능이나 모듈을 개발할 때는 `docs/conception/` 하위의 모든 설계 문서(도메인 모델, ERD, API 명세서 등)를 전수 검토한다. 누락된 필드, N:M 중계 관계, 마스터 API 스펙이 없는지 대조·검증한 뒤 계획을 세운다. 독단적인 스펙 생략이나 임의 구현은 금지한다.
- 설계 결과 위치: 기능 설계는 `docs/conception/NNN_기능/`, 화면만 바뀌는 스펙은 `docs/specs/`. 티켓 본문에는 배경·목표·범위와 문서 링크를 둔다.

## 눈팅 개발 규칙

- 시간은 KST 기준이다.
- 목록 정렬에는 ID를 보조 정렬 기준으로 붙인다.
- DB 변경은 `database/production/` 마이그레이션 파일로 한다.
- 검증 명령: 백엔드 `./gradlew spotlessCheck test`, 프론트 `npm run lint`, `npm run build`.

## 기록

- 작업 기록: `docs/project/work.md` (티켓 댓글과 함께 쓴다).
- 학습 문서: `docs/study/{주제폴더}/study{YYMMDD}-{메인}-{서브}.md`. 기존 파일은 옮기지 않는다.
- TIL: Notion Learn Board, 제목 `YY.MM.DD 눈팅 주제`.

## 스킬 사용 범위

- `ponytail`: 코딩할 때만 쓴다.
- `caveman`: 대화로 답할 때만 쓴다. 개념·기술 설명을 요청받으면 설명 규칙이 우선한다.
- 둘 다 티켓, Linear 댓글, PR과 리뷰 답글, GitHub 이슈, 커밋 메시지, TIL, 학습 문서, 설계 문서를 쓸 때는 쓰지 않는다.
