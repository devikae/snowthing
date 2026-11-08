# 1단계 기준선 평가: 평가 자료 형식과 실행기

- 상태: 실행기·검색 조건 구현 완료. **결과 없음 — 사람 라벨링 대기**
- 기준일: 2026-11-04 (KST)
- Linear: IKA-9 (부모 IKA-5)
- 상위 설계: `01_design.md` D8, §6 (IKA-6 브랜치). 검색 API: `03_stage1_keyword_search.md`

## 1. 무엇을 재나

질문 게시판(QNA) 질문 하나가 올라온 시점에, 그 전에 있던 다른 글·댓글에서 답이 되는 근거를 키워드 검색 상위 k개 안에 찾을 수 있는지 본다.

- 0단계 결과 실제 QNA 질문은 17건뿐이다(2026-11-04 운영 집계). 그래서 결과는 비율이 아니라 **n/N**으로만 쓴다. 17건에서 1건 차이는 약 6%p라서 백분율로 쓰면 차이가 과장된다.
- 이 평가는 1단계 키워드 기준선이다. 의미 검색(IKA-11)을 붙일 때 같은 자료·같은 실행기로 다시 재서 비교한다.

## 2. 평가 자료 형식 (JSONL, 저장소 밖에 둔다)

저장소(devikae/snowthing)는 공개 저장소다. 질문 원문과 근거 ID가 들어간 평가 자료는 커밋하지 않는다(D8). 예: `D:/private/snowthing-eval/qna-eval.jsonl`. 실행기는 저장소 안 경로의 평가 자료를 거부한다.

한 줄에 질문 하나:

```json
{"id": "q01", "split": "eval", "question": "덕스탠스 바인딩 각도 추천해 주세요", "askedAt": "2026-10-15T09:30:00", "questionPostPublicId": "0f8fad5b-d9cb-469f-a165-70867728950e", "expectedEvidenceIds": ["P:12:1", "C:40:0"], "reviewedBy": "ikae"}
```

| 필드 | 규칙 |
|---|---|
| id | 자료 안에서 유일한 문자열 |
| split | `tuning` 또는 `eval` |
| question | 질문. 보통 제목 + 본문 핵심 문장. 검색어로 쓴다 |
| query | 선택. 있으면 question 대신 검색어로 쓴다 |
| askedAt | 질문 글 작성 시각, KST `YYYY-MM-DDTHH:mm:ss` |
| questionPostPublicId | 질문 글의 publicId (UUID) |
| expectedEvidenceIds | 답이 되는 근거 ID 목록(`P:{post_id}:{chunk_no}`, `C:{comment_id}:0`). **빈 목록 = 답이 되는 근거가 없음**으로 라벨링한 것 |
| reviewedBy | 라벨링한 사람. 비어 있으면 라벨링 전으로 보고 집계에서 뺀다(건수만 센다) |

- 검색 API의 검색어 제한이 100자(코드 포인트)라서 실행기는 더 긴 검색어를 앞 100자로 자르고, 잘린 건수를 결과에 남긴다.

## 3. 사람이 정답 근거를 고르는 방법

1. 대상: 공개 QNA 질문 전체(현재 17건). 질문 글 하나 = 평가 항목 하나.
2. 시점 제한: 질문의 `askedAt`보다 **먼저 작성된** 글·댓글만 근거 후보다. 질문 뒤에 달린 글은 그 질문 당시 존재하지 않았다.
3. 자기 글타래 제외: 질문 글 자체와 그 글의 댓글(답변 댓글 포함)은 근거로 고르지 않는다. 그 답변이 바로 "반복 질문이 없었다면 쓰지 않아도 됐을 답"이기 때문이다. 다른 글의 댓글은 근거가 될 수 있다.
4. 근거 판정: "이 구간만 읽고 질문에 답할 수 있다"면 정답 근거다. 주제만 같고 답이 없으면 넣지 않는다. 한 질문에 정답 근거가 여러 개일 수 있다.
5. 근거 ID 찾기: 관리자로 로그인한 브라우저에서 `GET /api/v1/admin/search/evidence?q=...&createdBefore=<askedAt>&excludePostPublicId=<질문 publicId>&size=20`을 여러 검색어로 바꿔 가며 찾고, 응답의 `evidenceId`를 적는다. 게시글 본문이 여러 구간이면 답이 들어 있는 구간 번호를 고른다. 검색으로 안 나오는 근거는 게시글을 직접 보고 `GET /api/v1/admin/search/evidence/{evidenceId}`로 구간 번호를 확인한다. **검색 결과만 보고 정답을 고르면 기준선이 유리해진다.** 검색에 안 나온 근거도 직접 찾아 넣는다.
6. 답이 없으면 `expectedEvidenceIds: []`로 둔다. 실행기는 이 항목을 따로 센다.
7. 나누기: 항목을 `tuning`과 `eval`로 나눈다. 검색어 규칙·정규화·파라미터를 고칠 때는 `tuning`만 보고, `eval`은 마지막 비교에만 쓴다. 17건이면 예: tuning 5건, eval 12건. 나누는 기준(예: 질문 작성 순서로 앞 5건)을 자료 밖에 적어 둔다.
8. `reviewedBy`에 이름을 적는다. 둘이 라벨링하면 서로 다른 항목만 다시 의논해 하나로 맞춘다.

## 4. 실행기

`scripts/evaluate-evidence-search.py` (Python 3 표준 라이브러리만 사용)

```powershell
$env:SNOWTHING_EVAL_COOKIE = "JSESSIONID=<관리자 세션 값>"
python -I scripts/evaluate-evidence-search.py `
  --eval-set D:/private/snowthing-eval/qna-eval.jsonl `
  --base-url https://<대상 서버> --split eval `
  --output D:/private/snowthing-eval/result-eval.json
Remove-Item Env:SNOWTHING_EVAL_COOKIE
```

- 세션 쿠키는 환경 변수로만 받는다(`--cookie-env`로 이름 변경 가능). 명령줄 인자·로그·결과 파일에 쓰지 않는다.
- 항목마다 `GET /api/v1/admin/search/evidence`를 `size=10`, `createdBefore=askedAt`, `excludePostPublicId=questionPostPublicId`로 한 번 부른다(읽기 전용 GET).
- 결과 파일에는 집계만 쓴다. 질문 원문·근거 ID·게시글 ID는 쓰지 않는다. 평가 자료 파일의 SHA-256을 남겨 어떤 자료로 쟀는지 구분한다.

| 지표 | 정의 |
|---|---|
| hit@k (k=1,3,5,10) | 정답 근거가 하나라도 상위 k에 든 답변 가능 질문 수 / 답변 가능 질문 수 |
| recall@k | 상위 k에 든 정답 근거 수 / 전체 정답 근거 수 |
| noAnswer.withTop1Result | "근거 없음" 항목 중 검색 결과가 1건 이상 나온 수. 키워드 검색은 거의 항상 무언가를 돌려주므로 정밀도로 해석하지 않는다 |
| latencyMs p50/p95 | 실행기에서 잰 HTTP 왕복 시간(nearest-rank). 네트워크가 포함된다 |
| errorsByStatus | 실패한 요청 수(HTTP 상태별). 실패 항목은 지표 분모에서 빠지지 않고 "맞히지 못함"으로 센다 |

### 검색 API에 추가한 평가용 조건 (선택)

| 파라미터 | 규칙 |
|---|---|
| createdBefore | KST `YYYY-MM-DDTHH:mm:ss`만 허용(엄격 검증, 날짜만·밀리초·시간대 표기는 400 `SEARCH_001`). `search_document.source_created_at < createdBefore`인 문서만 |
| excludePostPublicId | UUID 형식(대소문자 무관, 소문자로 맞춤). 이 게시글의 구간과 그 글의 댓글을 뺀다 |

- 둘 다 없으면 1단계와 같은 결과다.
- 한계: `source_created_at`은 원문 작성 시각이다. 질문 뒤에 **수정**된 글은 수정된 내용으로 색인되어 있어 질문 당시 내용과 다를 수 있다. 라벨링할 때 근거 글의 수정 시각(`sourceUpdatedAt`)이 질문 뒤면 메모해 둔다.

## 5. 사용자가 할 일

1. 저장소 밖 경로에 평가 자료 JSONL을 만든다(§2).
2. QNA 질문 17건에 정답 근거를 라벨링하고 tuning/eval로 나눈다(§3). 운영 데이터로 하려면 IKA-10까지 배포되어 색인이 최신이어야 하고, 운영 관리자 세션이 필요하다.
3. 실행기를 `--split eval`로 한 번 실행하고(§4) 집계 JSON을 공유한다. 운영 서버를 대상으로 할지는 사용자가 정한다(읽기 전용 GET 17회).
4. 결과를 이 문서 §6에 n/N으로 적는다.

## 6. 결과

**아직 없음.** 사람 라벨링 전이다.

실행기 동작 확인(측정 아님): 2026-11-04 로컬 임시 스키마(001+002+전체 마이그레이션)에 직접 만든 게시글 4건·댓글 3건과 가짜 평가 항목 4건으로 실행기를 돌려 쿠키 전달, 평가용 조건, 라벨링 전 항목 제외, 근거 없음 항목 분리 집계, 저장소 안 평가 자료 거부, 쿠키 없음 거부, 잘못된 askedAt 거부, 결과 파일에 질문 원문이 없음을 확인했다. 이 수치는 기록하지 않는다(harness check, not a measurement).
